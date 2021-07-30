/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith.
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
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.ThreadContext;

class ProxyContext {

    private final CodeComponent<?> component;
    private final ThreadContext threadCtxt;
    private final ProxyLoader loader;
    private final Map<ProxyKey, ProxyInfo> proxies;

    ProxyContext(CodeComponent<?> component, ThreadContext threadCtxt) {
        this.component = component;
        this.threadCtxt = threadCtxt;
        this.loader = new ProxyLoader(component.getClass().getClassLoader());
        this.proxies = new HashMap<>();
    }

    Object wrap(Class<?> type, String name, Object delegate) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Type is not interface");
        }
        var key = new ProxyKey(type, name);
        var info = proxies.get(key);
        if (info != null) {
            info.handler.swap(delegate, false);
            return type.cast(info.proxy);
        } else {
            var handler = new Handler(delegate, false);
            var proxy = Proxy.newProxyInstance(loader, new Class<?>[]{type}, handler);
            proxies.put(key, new ProxyInfo(type, proxy, handler));
            return type.cast(proxy);
        }
    }

    void clear(Class<?> type, String name) {
        var key = new ProxyKey(type, name);
        var info = proxies.get(key);
        if (info != null) {
            info.handler.swap(null, false);
        }
    }

    class Handler implements InvocationHandler {

        private Object delegate;
        private boolean direct;

        private Handler(Object delegate, boolean direct) {
            this.delegate = delegate;
            this.direct = direct;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (threadCtxt.isRootThread()) {
                return method.invoke(delegate, args);
            } else {
                return invokeOffRootThread(proxy, method, args);
            }
        }

        private Object invokeOffRootThread(Object proxy, Method method, Object[] args) throws Throwable {
            synchronized (this) {
                if (direct) {
                    throw new UnsupportedOperationException();
                }
            }
            var task = new FutureTask<Object>(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    if (component.getAddress() == null) {
                        throw new IllegalStateException("Invalid component");
                    }
                    try {
                        return method.invoke(delegate, args);
                    } finally {
                        component.getCodeContext().flush();
                    }
                }

            });
            threadCtxt.invokeLater(task);
            return task.get(10, TimeUnit.SECONDS);
        }

        private synchronized Object swap(Object delegate, boolean direct) {
            Object ret = this.delegate;
            this.delegate = delegate;
            this.direct = direct;
            return ret;
        }

    }

    private static class ProxyLoader extends ClassLoader {

        public ProxyLoader(ClassLoader parent) {
            super(parent);
        }

    }

    private static class ProxyKey {

        private final Class<?> type;
        private final String name;

        private ProxyKey(Class<?> type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + Objects.hashCode(this.type);
            hash = 37 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ProxyKey other = (ProxyKey) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "ProxyKey{" + "type=" + type + ", name=" + name + '}';
        }

    }

    private static class ProxyInfo {

        private final Class<?> type;
        private final Object proxy;
        private final Handler handler;

        public ProxyInfo(Class<?> type, Object proxy, Handler handler) {
            this.type = type;
            this.proxy = proxy;
            this.handler = handler;
        }

    }

}
