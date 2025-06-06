/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 *
 *
 * Please visit https://www.praxislive.org if you need additional information or
 * have any questions.
 */
package org.praxislive.code;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.ThreadContext;

class ProxyContext {

    private final CodeComponent<?> component;
    private final ThreadContext threadCtxt;
    private final Map<ProxyKey, ProxyInfo> proxies;

    ProxyContext(CodeComponent<?> component, ThreadContext threadCtxt) {
        this.component = component;
        this.threadCtxt = threadCtxt;
        this.proxies = new HashMap<>();
    }

    Object wrap(Class<?> type, String name, Handler delegate, boolean direct) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Type is not interface");
        }
        if (direct && !threadCtxt.supportsDirectInvoke()) {
            throw new UnsupportedOperationException("ThreadContext does not support direct invoke");
        }
        var key = new ProxyKey(type, name);
        var info = proxies.get(key);
        if (info != null) {
            info.handler.configure(delegate, direct);
            return type.cast(info.proxy());
        } else {
            var handler = new BaseHandler(delegate, direct);
            var proxy = Proxy.newProxyInstance(new ProxyLoader(type.getClassLoader()),
                    new Class<?>[]{type}, handler);
            proxies.put(key, new ProxyInfo(type, proxy, handler));
            return type.cast(proxy);
        }
    }

    void clear(Class<?> type, String name) {
        var key = new ProxyKey(type, name);
        var info = proxies.get(key);
        if (info != null) {
            info.handler().configure(null, false);
        }
    }

    class BaseHandler implements InvocationHandler {

        private volatile Handler delegate;
        private volatile boolean direct;

        private BaseHandler(Handler delegate, boolean direct) {
            this.delegate = delegate;
            this.direct = direct;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (direct) {
                return invokeDirect(proxy, method, args);
            } else {
                return invokeOnRootThread(proxy, method, args);
            }
        }

        private Object invokeDirect(Object proxy, Method method, Object[] args) throws Throwable {
            if (threadCtxt.isInUpdate()) {
                return invokeDelegate(proxy, method, args);
            } else {
                return threadCtxt.invoke(() -> invokeDelegate(proxy, method, args));
            }
        }

        private Object invokeOnRootThread(Object proxy, Method method, Object[] args) throws Throwable {
            if (threadCtxt.isInUpdate()) {
                return invokeDelegate(proxy, method, args);
            } else if (threadCtxt.isRootThread()) {
                if (threadCtxt.supportsDirectInvoke()) {
                    return threadCtxt.invoke(() -> invokeDelegate(proxy, method, args));
                } else {
                    return invokeDelegate(proxy, method, args);
                }
            } else {
                var task = new FutureTask<Object>(() -> invokeDelegate(proxy, method, args));
                threadCtxt.invokeLater(task);
                return task.get(10, TimeUnit.SECONDS);
            }
        }

        private Object invokeDelegate(Object proxy, Method method, Object[] args) throws Exception {
            // checkActive, which could reassign delegate
            if (component.getCodeContext().checkActive()) {
                try {
                    return delegate.invoke(proxy, method, args);
                } catch (Throwable t) {
                    throw new Exception(t);
                } finally {
                    component.getCodeContext().flush();
                }
            }
            throw new UnsupportedOperationException();
        }

        private /*synchronized*/ void configure(Handler delegate, boolean direct) {
            this.delegate = delegate;
            this.direct = direct;
        }

    }

    interface Handler extends InvocationHandler {

    }

    private static class ProxyLoader extends ClassLoader {

        public ProxyLoader(ClassLoader parent) {
            super(parent);
        }

    }

    private static record ProxyKey(Class<?> type, String name) {

    }

    private static record ProxyInfo(Class<?> type, Object proxy, BaseHandler handler) {

    }

}
