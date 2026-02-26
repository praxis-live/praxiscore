/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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
 *
 */
package org.praxislive.code;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Value;
import org.praxislive.core.ValueMapper;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.core.services.LogLevel;

abstract class ValueMapperBinding extends PropertyControl.Binding {

    final ValueMapper<Object> mapper;
    final Value defaultValue;
    final ArgumentInfo info;
    final Predicate<Object> validator;

    private ValueMapperBinding(ValueMapper<Object> mapper, Value defaultValue,
            ArgumentInfo info, Predicate<Object> validator) {
        this.mapper = mapper;
        this.defaultValue = defaultValue;
        this.info = info;
        this.validator = validator;
    }

    @Override
    public void set(double value) throws Exception {
        set(PNumber.of(value));
    }

    @Override
    public ArgumentInfo getArgumentInfo() {
        return info;
    }

    @Override
    public Value getDefaultValue() {
        return defaultValue;
    }

    static ValueMapperBinding create(CodeConnector<?> connector, Field field) {
        Class<?> cls = field.getType();

        if (Optional.class.isAssignableFrom(cls)) {
            return OptionalField.createImpl(connector, field);
        } else {
            return MappedField.createImpl(connector, field);
        }
    }

    private static class MappedField extends ValueMapperBinding {

        private final Field field;

        private CodeDelegate delegate;
        private Object lastFieldValue;
        private Value lastMappedValue;

        private MappedField(ValueMapper<Object> mapper, Value defaultValue,
                ArgumentInfo info, Predicate<Object> validator,
                Field field) {
            super(mapper, defaultValue, info, validator);
            this.field = field;
        }

        @Override
        protected void attach(CodeContext<?> context) {
            delegate = context.getDelegate();
            try {
                field.set(delegate, mapper.fromValue(defaultValue));
            } catch (Exception ex) {
                // ignore
            }
        }

        @Override
        public void set(Value value) throws Exception {
            Object obj = mapper.fromValue(value);
            if (obj == null) {
                field.set(delegate, mapper.fromValue(defaultValue));
            } else if (validator.test(obj)) {
                field.set(delegate, obj);
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Value get() {
            try {
                Object fieldValue = field.get(delegate);
                if (Objects.equals(fieldValue, lastFieldValue) && lastMappedValue != null) {
                    return lastMappedValue;
                } else {
                    Value mappedValue = mapper.toValue(fieldValue);
                    lastFieldValue = fieldValue;
                    lastMappedValue = mappedValue;
                    return mappedValue;
                }
            } catch (Exception ex) {
                return PString.EMPTY;
            }
        }

        @SuppressWarnings("unchecked")
        private static MappedField createImpl(CodeConnector<?> connector, Field field) {
            Class<?> cls = field.getType();
            ValueMapper<Object> mapper = (ValueMapper<Object>) ValueMapper.find(cls);
            if (mapper == null) {
                return null;
            }
            ArgumentInfo info;
            Value defValue;
            Predicate<Object> validator;
            AnnotationUtils.ArgumentData<?> data;

            try {
                data = AnnotationUtils.extractArgumentData(mapper, field);
            } catch (AnnotationUtils.TypeMismatchException ex) {
                data = null;
            }
            if (data != null) {
                defValue = data.defaultValue();
                info = data.info();
                validator = (Predicate<Object>) data.validator();
            } else {
                defValue = mapper.valueType().emptyValue().orElse(null);
                info = mapper.createInfo();
                validator = v -> true;
            }

            Object defObject = null;
            if (defValue != null) {
                try {
                    defObject = mapper.fromValue(defValue);
                    defValue = mapper.toValue(defObject);
                } catch (Exception ex) {
                    // fall through
                }
            }
            if (defValue == null || defObject == null) {
                connector.getLog().log(LogLevel.WARNING,
                        "Type " + cls.getSimpleName()
                        + " does not support default value, consider Optional<"
                        + cls.getSimpleName() + ">");
                defValue = PString.EMPTY;
            }
            return new MappedField(mapper, defValue, info, validator, field);
        }

    }

    private static class OptionalField extends ValueMapperBinding {

        private final Field field;

        private CodeDelegate delegate;
        private Object lastFieldValue;
        private Value lastMappedValue;

        private OptionalField(ValueMapper<Object> mapper, Value defaultValue,
                ArgumentInfo info, Predicate<Object> validator, Field field) {
            super(mapper, defaultValue, info, validator);
            this.field = field;
        }

        @Override
        protected void attach(CodeContext<?> context) {
            delegate = context.getDelegate();
            try {
                field.set(delegate, Optional.ofNullable(mapper.fromValue(defaultValue)));
            } catch (Exception ex) {
                try {
                    field.set(delegate, Optional.empty());
                } catch (Exception exx) {
                    // ignore
                }
            }
        }

        @Override
        public void set(Value value) throws Exception {
            Object obj = mapper.fromValue(value);
            if (validator.test(obj)) {
                field.set(delegate, Optional.ofNullable(obj));
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Value get() {
            try {
                Object fieldValue = ((Optional<Object>) field.get(delegate)).orElse(null);
                if (Objects.equals(fieldValue, lastFieldValue) && lastMappedValue != null) {
                    return lastMappedValue;
                } else {
                    Value mappedValue = mapper.toValue(fieldValue);
                    lastFieldValue = fieldValue;
                    lastMappedValue = mappedValue;
                    return mappedValue;
                }
            } catch (Exception ex) {
                return defaultValue;
            }
        }

        @SuppressWarnings("unchecked")
        private static OptionalField createImpl(CodeConnector<?> connector, Field field) {
            Class<?> optCls = TypeUtils.extractRawType(
                    TypeUtils.extractTypeParameter(field.getGenericType()));
            if (optCls == null) {
                return null;
            }
            ValueMapper<Object> mapper = (ValueMapper<Object>) ValueMapper.find(optCls);
            if (mapper == null) {
                return null;
            }
            ArgumentInfo info;
            Value defValue;
            Predicate<Object> validator;
            AnnotationUtils.ArgumentData<?> data;

            try {
                data = AnnotationUtils.extractArgumentData(mapper, field);
            } catch (AnnotationUtils.TypeMismatchException ex) {
                data = null;
            }
            if (data != null) {
                defValue = data.defaultValue();
                try {
                    defValue = mapper.toValue(mapper.fromValue(defValue));
                } catch (Exception ex) {
                }
                info = data.info();
                validator = (Predicate<Object>) data.validator();
            } else {
                defValue = mapper.valueType().emptyValue()
                        .map(Value.class::cast)
                        .orElse(PString.EMPTY);
                info = mapper.createInfo();
                validator = v -> true;
            }
            return new OptionalField(mapper, defValue, info, validator, field);
        }

    }

}
