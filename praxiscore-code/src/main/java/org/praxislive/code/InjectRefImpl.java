/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2023 Neil C Smith.
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
import java.util.Objects;
import org.praxislive.code.userapi.Inject;
import org.praxislive.code.userapi.Ref;
import org.praxislive.core.services.LogLevel;

class InjectRefImpl<T> extends Ref<T> {

    private CodeContext<?> context;

    private InjectRefImpl() {
    }

    private void attach(CodeContext<?> context) {
        this.context = context;
    }

    @Override
    protected void log(Exception ex) {
        context.getLog().log(LogLevel.ERROR, ex);
    }

    static class Descriptor extends ReferenceDescriptor<InjectRefImpl.Descriptor> {

        private final Field field;
        private final Ref.Initializer<?> initializer;

        private CodeContext<?> context;
        private InjectRefImpl<?> ref;

        private Descriptor(CodeConnector<?> connector, Field field, Ref.Initializer<?> initializer) {
            super(Descriptor.class, field.getName());
            this.field = field;
            this.initializer = initializer;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void attach(CodeContext<?> context, Descriptor previous) {
            this.context = context;
            if (previous != null) {
                if (isCompatible(previous)) {
                    ref = previous.ref;
                    previous.ref = null;
                } else {
                    previous.dispose();
                }
            }

            if (ref == null) {
                ref = new InjectRefImpl<>();
            }

            ref.attach(context);
            init();

        }

        private boolean isCompatible(Descriptor other) {
            return field.getGenericType().equals(other.field.getGenericType());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void init() {
            try {
                initializer.initialize((Ref) ref);
                field.set(context.getDelegate(), ref.get());
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
        }

        @Override
        public void reset() {
            ref.reset();
        }

        @Override
        public void stopping() {
            dispose();
        }

        @Override
        public void dispose() {
            if (ref != null) {
                ref.dispose();
            }
            if (context != null) {
                try {
                    field.set(context.getDelegate(), null);
                } catch (Exception ex) {
                    context.getLog().log(LogLevel.ERROR, ex);
                }
            }
        }

        static Descriptor create(CodeConnector<?> connector, Inject ann, Field field) {

            try {
                Class<? extends Provider> handlerCls = ann.provider();
                Ref.Provider provider;
                if (handlerCls == Ref.Provider.class) {
                    provider = Ref.Provider.getDefault();
                } else {
                    provider = handlerCls.getConstructor().newInstance();
                }

                if (provider.isSupportedType(field.getType())) {
                    Ref.Initializer<?> init = provider.initializerFor(field.getType());
                    field.setAccessible(true);
                    return new Descriptor(connector, field, Objects.requireNonNull(init));
                } else {
                    return null;
                }
            } catch (Exception ex) {
                connector.getLog().log(LogLevel.ERROR, ex);
                return null;
            }

        }

    }

}
