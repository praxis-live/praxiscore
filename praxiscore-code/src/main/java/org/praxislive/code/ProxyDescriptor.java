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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.praxislive.code.userapi.Proxy;
import org.praxislive.core.services.LogLevel;

final class ProxyDescriptor extends ReferenceDescriptor<ProxyDescriptor> implements ProxyContext.Handler {

    private final Field field;
    private final Object delegate;

    private CodeContext<?> context;

    protected ProxyDescriptor(Field field, Object delegate) {
        super(ProxyDescriptor.class, field.getName());
        this.field = field;
        this.delegate = delegate;
    }

    @Override
    public void attach(CodeContext<?> context, ProxyDescriptor previous) {
        this.context = context;
        if (previous != null) {
            if (!field.getType().equals(previous.field.getType())) {
                previous.dispose();
            }
        }
        try {
            var proxy = context.getComponent().getProxyContext()
                    .wrap(field.getType(), field.getName(), this, false);
            field.set(context.getDelegate(), proxy);
        } catch (Exception ex) {
            context.getLog().log(LogLevel.ERROR, ex);
        }
    }

    @Override
    public void dispose() {
        if (context != null) {
            context.getComponent().getProxyContext().clear(field.getType(), field.getName());
        }
        context = null;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(delegate, args);
    }

    static ProxyDescriptor create(CodeConnector<?> connector, Proxy ann, Field field) {
        if (!field.getType().isInterface()) {
            connector.getLog().log(LogLevel.ERROR,
                    "@Proxy annotated field " + field.getName() + " is not an interface type.");
            return null;
        }
        try {
            field.setAccessible(true);
            Object del = field.get(connector.getDelegate());
            field.set(connector.getDelegate(), null);
            return new ProxyDescriptor(field, del);
        } catch (Exception ex) {
            connector.getLog().log(LogLevel.ERROR, ex,
                    "Cannot access @Proxy annotated field " + field.getName());
            return null;
        }
    }

}
