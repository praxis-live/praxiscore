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
import java.util.Map;
import org.praxislive.code.userapi.Persist;
import org.praxislive.core.services.LogLevel;

class PersistDescriptor extends ReferenceDescriptor<PersistDescriptor> {

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            int.class, Integer.valueOf(0),
            long.class, Long.valueOf(0),
            float.class, Float.valueOf(0),
            double.class, Double.valueOf(0),
            byte.class, Byte.valueOf((byte) 0),
            short.class, Short.valueOf((short) 0),
            boolean.class, Boolean.FALSE,
            char.class, Character.valueOf((char) 0)
    );

    private final Field field;
    private final boolean autoReset;
    private final boolean autoClose;

    private CodeContext<?> context;

    PersistDescriptor(Field field, boolean autoReset, boolean autoDispose) {
        super(PersistDescriptor.class, field.getName());
        this.field = field;
        this.autoReset = autoReset;
        this.autoClose = autoDispose;
    }

    @Override
    public void attach(CodeContext<?> context, PersistDescriptor previous) {
        this.context = context;
        if (previous != null) {
            if (field.getGenericType().equals(previous.field.getGenericType())
                    && previous.context != null) {
                try {
                    field.set(context.getDelegate(),
                            previous.field.get(previous.context.getDelegate()));
                } catch (Exception ex) {
                    context.getLog().log(LogLevel.ERROR, ex);
                }
            } else {
                previous.dispose();
            }
        }
    }

    @Override
    public void onStop() {
        if (autoReset) {
            try {
                if (autoClose) {
                    handleAutoClose();
                }
                Object def = null;
                Class<?> type = field.getType();
                if (type.isPrimitive()) {
                    def = PRIMITIVE_DEFAULTS.get(type);
                }
                field.set(context.getDelegate(), def);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
        }
    }

    @Override
    public void dispose() {
        if (context == null) {
            return;
        }
        try {
            if (autoClose) {
                handleAutoClose();
            }
        } catch (Exception ex) {
            context.getLog().log(LogLevel.ERROR, ex);
        }
    }

    private void handleAutoClose() throws Exception {
        var value = field.get(context.getDelegate());
        if (value instanceof AutoCloseable) {
            ((AutoCloseable) value).close();
        }
    }

    static PersistDescriptor create(CodeConnector<?> connector, Persist ann, Field field) {
        try {
            field.setAccessible(true);
            return new PersistDescriptor(field, ann.autoReset(), ann.autoClose());
        } catch (Exception ex) {
            connector.getLog().log(LogLevel.ERROR, ex);
            return null;
        }
    }

}
