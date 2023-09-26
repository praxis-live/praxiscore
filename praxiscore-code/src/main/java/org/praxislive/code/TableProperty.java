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
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.praxislive.code.userapi.OnChange;
import org.praxislive.code.userapi.OnError;
import org.praxislive.code.userapi.P;
import org.praxislive.code.userapi.Table;
import org.praxislive.core.Value;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.services.TaskService;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;
import org.praxislive.core.services.LogLevel;

/**
 *
 */
class TableProperty extends AbstractAsyncProperty<TableParser.Response> {

    static final ControlInfo INFO = Info.control().property()
            .input(Info.argument().string().mime("text/x-praxis-table").build())
            .defaultValue(PString.EMPTY)
            .build();

    private final boolean isList;

    private Field field;
    private Method onChange;
    private Method onError;
    private CodeContext<?> context;

    private TableProperty(boolean isList) {
        super(PString.EMPTY, TableParser.Response.class, new TableParser.Response(List.of()));
        this.isList = isList;
    }

    private void attach(CodeContext<?> context,
            Field field, Method onChange, Method onError) {
        super.attach(context);
        this.context = context;
        this.field = field;
        try {
            updateFieldValue();
        } catch (Exception ex) {
            context.getLog().log(LogLevel.WARNING, ex);
        }
        this.onChange = onChange;
        this.onError = onError;
    }

    @Override
    protected TaskService.Task createTask(Value data) throws Exception {
        return new Task(data);
    }

    @Override
    protected void valueChanged(long time) {
        try {
            updateFieldValue();
        } catch (Exception ex) {
            context.getLog().log(LogLevel.ERROR, ex);
        }
        if (onChange != null) {
            context.invoke(time, onChange);
        }
    }

    @Override
    protected void taskError(long time, PError error) {
        if (onError != null) {
            context.invoke(time, onError);
        }
    }

    private void updateFieldValue() throws Exception {
        var list = getValue().tables();
        if (isList) {
            field.set(context.getDelegate(), list);
        } else {
            if (list.isEmpty()) {
                field.set(context.getDelegate(), Table.EMPTY);
            } else {
                field.set(context.getDelegate(), list.get(0));
            }
        }
    }

    private final static class Task implements TaskService.Task {

        private final Value data;

        private Task(Value data) {
            this.data = data;
        }

        @Override
        public Value execute() throws Exception {
            return PReference.of(TableParser.parse(data.toString()));
        }

    }

    static class Descriptor extends ControlDescriptor<Descriptor> {

        private final Field field;
        private final boolean isList;
        private final Method onChange, onError;
        
        private TableProperty control;

        private Descriptor(
                String id,
                int index,
                Field field,
                boolean isList,
                Method onChange,
                Method onError
        ) {
            super(Descriptor.class, id, ControlDescriptor.Category.Property, index);
            this.field = field;
            this.isList = isList;
            this.onChange = onChange;
            this.onError = onError;
        }

        @Override
        public ControlInfo controlInfo() {
            return INFO;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void attach(CodeContext<?> context, Descriptor previous) {
            if (previous != null && previous.isList == isList) {
                control = previous.control;
            } else {
                if (previous != null) {
                    previous.dispose();
                }
                control = new TableProperty(isList);
            }
            control.attach(context, field, onChange, onError);
        }

        @Override
        public Control control() {
            return control;
        }

        public static Descriptor create(CodeConnector<?> connector, P ann,
                Field field) {
            if (!isSupportedField(field)) {
                return null;
            }
            boolean isList = field.getType() == List.class;
            field.setAccessible(true);
            String id = connector.findID(field);
            int index = ann.value();
            Method onChange = null;
            Method onError = null;
            OnChange onChangeAnn = field.getAnnotation(OnChange.class);
            if (onChangeAnn != null) {
                onChange = extractMethod(connector, onChangeAnn.value());
            }
            OnError onErrorAnn = field.getAnnotation(OnError.class);
            if (onErrorAnn != null) {
                onError = extractMethod(connector, onErrorAnn.value());
            }
            return new Descriptor(id, index, field, isList, onChange, onError);
        }

        private static boolean isSupportedField(Field field) {
            if (field.getType() == List.class) {
                var genType = field.getGenericType();
                if (genType instanceof ParameterizedType) {
                    var parType = (ParameterizedType) genType;
                    var actualType = parType.getActualTypeArguments();
                    if (actualType.length == 1 && actualType[0] == Table.class) {
                        return true;
                    }
                }
            } else if (field.getType() == Table.class) {
                return true;
            }
            return false;
        }

        private static Method extractMethod(CodeConnector<?> connector, String methodName) {
            try {
                Method m = connector.getDelegate().getClass().getDeclaredMethod(methodName);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException | SecurityException ex) {
                connector.getLog().log(LogLevel.WARNING, ex);
                return null;
            }
        }

    }


}
