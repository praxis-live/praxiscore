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
 */
package org.praxislive.core;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 * ValueMappers translate values of type Value to another Java type. See
 * {@link #find(java.lang.Class)} to access pre-registered ValueMappers.
 *
 * @param <T> Java type
 */
public abstract class ValueMapper<T> {

    private static final Registry REGISTRY = new Registry();

    private final Type type;
    private final Value.Type<?> valueType;

    /**
     * Base constructor.
     *
     * @param type the Java type
     * @param valueCls the Value class
     */
    protected ValueMapper(Type type, Class<? extends Value> valueCls) {
        this(type, Value.Type.of(valueCls));
    }

    /**
     * Base constructor.
     *
     * @param type the Java type
     * @param valueType the Value Type
     */
    protected ValueMapper(Type type, Value.Type<?> valueType) {
        this.type = Objects.requireNonNull(type);
        this.valueType = Objects.requireNonNull(valueType);
    }

    /**
     * Map the provided Value to a value of type T. If the input is empty, some
     * implementations may return null. Invalid values may cause an exception.
     *
     * @param value input Value
     * @return mapped value, or null
     * @throws IllegalArgumentException on invalid input
     */
    public abstract T fromValue(Value value);

    /**
     * Map the provided T value to a Value. Invalid input values may cause an
     * exception. Null input is allowed, and will return either the Value Type's
     * empty value if present, or {@link PString#EMPTY}.
     *
     * @param value input value
     * @return mapped Value (never null)
     * @throws IllegalArgumentException on invalid input
     */
    public abstract Value toValue(T value);

    /**
     * Type of T.
     *
     * @return type of T
     */
    public final Type type() {
        return type;
    }

    /**
     * The preferred Value.Type for mapping to type T. This is the type that
     * Values will be coerced to, and will be the type usually (but not always)
     * returned from {@link ValueMapper#toValue(java.lang.Object)}.
     *
     * @return preferred value type
     */
    public final Value.Type<?> valueType() {
        return valueType;
    }

    /**
     * Create a basic {@link ArgumentInfo} representing this value mapping.
     *
     * @return new ArgumentInfo
     */
    public ArgumentInfo createInfo() {
        return ArgumentInfo.of(valueType.asClass());
    }

    /**
     * Find a ValueMapper capable of mapping Values to and from the provided
     * Class. Mappers for String, Boolean, Integer, Float and Double are
     * provided, along with their primitive counterparts where applicable.
     * Mappers will be created on demand for any Enum type. Mappers for all
     * registered Value types are provided as a convenience.
     *
     * @param <T> mapped class
     * @param type mapped class
     * @return value mapper or null
     */
    @SuppressWarnings("unchecked")
    public static <T> ValueMapper<T> find(Class<T> type) {
        return (ValueMapper<T>) REGISTRY.find(type);
    }

    private static class Registry {

        private final Map<Type, ValueMapper<?>> map;
        private final WeakHashMap<Type, WeakReference<ValueMapper<?>>> dynamicMap;

        private Registry() {
            map = new HashMap<>();
            dynamicMap = new WeakHashMap<>();
            initDefaults();
        }

        private void initDefaults() {
            for (Value.Type<?> type : Value.Type.listAll()) {
                map.put(type.asClass(), new ValueMapperImpl<>(type));
            }
            map.put(String.class, new StringMapper());
            map.put(Boolean.class, new BooleanMapper());
            map.put(Integer.class, new IntMapper());
            map.put(Float.class, new FloatMapper());
            map.put(Double.class, new DoubleMapper());

            map.put(boolean.class, map.get(Boolean.class));
            map.put(int.class, map.get(Integer.class));
            map.put(float.class, map.get(Float.class));
            map.put(double.class, map.get(Double.class));

        }

        @SuppressWarnings("unchecked")
        synchronized ValueMapper<?> find(Type type) {
            ValueMapper<?> mapper = map.get(type);
            if (mapper != null) {
                return mapper;
            }
            WeakReference<ValueMapper<?>> mapperRef = dynamicMap.get(type);
            if (mapperRef != null) {
                mapper = mapperRef.get();
                if (mapper != null) {
                    return mapper;
                } else {
                    dynamicMap.remove(type);
                }
            }

            if (type instanceof Class) {
                Class<?> cls = (Class<?>) type;
                if (cls.isEnum()) {
                    mapper = new EnumMapper<>((Class<? extends Enum>) cls);
                } else if (cls.isRecord()) {
                    mapper = RecordMapper.create((Class<? extends Record>) cls);
                }
            }

            if (mapper != null) {
                dynamicMap.put(type, new WeakReference<>(mapper));
            }

            return mapper;

        }

    }

    private static class ValueMapperImpl<T extends Value> extends ValueMapper<T> {

        private final T emptyValue;
        private final Function<Value, Optional<T>> converter;

        ValueMapperImpl(Value.Type<T> type) {
            super(type.asClass(), type);
            emptyValue = type.emptyValue().orElse(null);
            converter = type.converter();
        }

        @Override
        public T fromValue(Value value) {
            if (value.isEmpty()) {
                return emptyValue;
            } else {
                return converter.apply(value).orElseThrow(IllegalArgumentException::new);
            }
        }

        @Override
        public Value toValue(T value) {
            if (value == null) {
                if (emptyValue == null) {
                    return PString.EMPTY;
                } else {
                    return emptyValue;
                }
            } else {
                return value;
            }
        }

        @Override
        public ArgumentInfo createInfo() {
            if (valueType().emptyValue().isPresent()) {
                return super.createInfo();
            } else {
                return Info.argument().type(valueType().asClass())
                        .property(ArgumentInfo.KEY_ALLOW_EMPTY, true)
                        .build();
            }
        }

    }

    private static class StringMapper extends ValueMapper<String> {

        StringMapper() {
            super(String.class, PString.class);
        }

        @Override
        public String fromValue(Value value) {
            return value.toString();
        }

        @Override
        public Value toValue(String value) {
            if (value == null) {
                return PString.EMPTY;
            } else {
                return PString.of(value);
            }
        }

    }

    private static class BooleanMapper extends ValueMapper<Boolean> {

        BooleanMapper() {
            super(Boolean.class, PBoolean.class);
        }

        @Override
        public Boolean fromValue(Value value) {
            return PBoolean.from(value).map(PBoolean::value)
                    .orElseThrow(IllegalArgumentException::new);
        }

        @Override
        public Value toValue(Boolean value) {
            if (value == null) {
                return PBoolean.FALSE;
            } else {
                return PBoolean.of(value);
            }
        }

    }

    private static class IntMapper extends ValueMapper<Integer> {

        public IntMapper() {
            super(Integer.class, PNumber.class);
        }

        @Override
        public Integer fromValue(Value value) {
            return PNumber.from(value)
                    .orElseThrow(IllegalArgumentException::new)
                    .toIntValue();

        }

        @Override
        public Value toValue(Integer value) {
            return value == null ? PNumber.ZERO : PNumber.of(value);
        }

        @Override
        public ArgumentInfo createInfo() {
            return Info.argument().type(PNumber.class)
                    .property(PNumber.KEY_IS_INTEGER, true)
                    .build();
        }

    }

    private static class FloatMapper extends ValueMapper<Float> {

        public FloatMapper() {
            super(Float.class, PNumber.class);
        }

        @Override
        public Float fromValue(Value value) {
            return (float) PNumber.from(value)
                    .orElseThrow(IllegalArgumentException::new)
                    .value();
        }

        @Override
        public Value toValue(Float value) {
            return value == null ? PNumber.ZERO : PNumber.of(value);
        }

    }

    private static class DoubleMapper extends ValueMapper<Double> {

        public DoubleMapper() {
            super(Double.class, PNumber.class);
        }

        @Override
        public Double fromValue(Value value) {
            return PNumber.from(value)
                    .orElseThrow(IllegalArgumentException::new)
                    .value();
        }

        @Override
        public Value toValue(Double value) {
            return value == null ? PNumber.ZERO : PNumber.of(value);
        }

    }

    private static class EnumMapper<E extends Enum<E>> extends ValueMapper<E> {

        private final Class<E> enumCls;
        private final E defaultValue;

        public EnumMapper(Class<E> enumCls) {
            super(enumCls, PString.class);
            this.enumCls = enumCls;
            E[] values = enumCls.getEnumConstants();
            if (values.length > 0) {
                defaultValue = values[0];
            } else {
                defaultValue = null;
            }
        }

        @Override
        public E fromValue(Value value) {
            return Enum.valueOf(enumCls, value.toString());
        }

        @Override
        public Value toValue(E value) {
            if (value == null) {
                return defaultValue == null ? PString.EMPTY : PString.of(defaultValue);
            } else {
                return PString.of(value);
            }
        }

        @Override
        public ArgumentInfo createInfo() {
            return Info.argument().string()
                    .allowed(Arrays.stream(enumCls.getEnumConstants())
                            .map(Object::toString).toArray(String[]::new))
                    .build();

        }

    }

    private static class RecordMapper<R extends Record> extends ValueMapper<R> {

        private final Constructor<R> constructor;
        private final List<String> names;
        private final List<ValueMapper<Object>> mappers;
        private final List<Method> accessors;

        private RecordMapper(Class<R> recordCls,
                Constructor<R> constructor,
                List<String> names,
                List<ValueMapper<Object>> mappers,
                List<Method> accessors) {
            super(recordCls, PMap.class);
            this.constructor = constructor;
            this.names = names;
            this.mappers = mappers;
            this.accessors = accessors;
        }

        @Override
        public R fromValue(Value value) {
            if (value.isEmpty()) {
                return null;
            }
            PMap input = PMap.from(value).orElseThrow(IllegalArgumentException::new);
            Object[] parameters = new Object[names.size()];
            for (int i = 0; i < parameters.length; i++) {
                Value v = input.get(names.get(i));
                Object o = mappers.get(i).fromValue(v);
                parameters[i] = o;
            }
            try {
                return constructor.newInstance(parameters);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (ReflectiveOperationException ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public Value toValue(R value) {
            if (value == null) {
                return PMap.EMPTY;
            }
            try {
                PMap.Entry[] entries = new PMap.Entry[names.size()];
                for (int i = 0; i < entries.length; i++) {
                    Object o = accessors.get(i).invoke(value);
                    Value v = mappers.get(i).toValue(o);
                    entries[i] = PMap.entry(names.get(i), v);
                }
                return PMap.ofEntries(entries);
            } catch (ReflectiveOperationException ex) {
                return PMap.EMPTY;
            }
        }

        @Override
        public ArgumentInfo createInfo() {
            PMap.Entry[] schemaEntries = new PMap.Entry[names.size()];
            for (int i = 0; i < schemaEntries.length; i++) {
                schemaEntries[i] = PMap.entry(names.get(i), mappers.get(i).createInfo());
            }
            return Info.argument().type(PMap.class)
                    .property(ArgumentInfo.KEY_ALLOW_EMPTY, true)
                    .property(PMap.KEY_SCHEMA, PMap.ofEntries(schemaEntries))
                    .build();
        }

        private static <R extends Record> RecordMapper<R> create(Class<R> recordCls) {
            RecordComponent[] rcs = recordCls.getRecordComponents();
            if (rcs == null) {
                return null;
            }
            Constructor<R> constructor;
            Class<?>[] parameters = Arrays.stream(rcs).map(RecordComponent::getType).toArray(Class<?>[]::new);
            try {
                constructor = recordCls.getDeclaredConstructor(parameters);
                if (!constructor.trySetAccessible()) {
                    return null;
                }
            } catch (NoSuchMethodException ex) {
                return null;
            }
            List<String> names = new ArrayList<>(rcs.length);
            List<ValueMapper<Object>> mappers = new ArrayList<>(rcs.length);
            List<Method> accessors = new ArrayList<>(rcs.length);
            for (RecordComponent rc : rcs) {
                if (recordCls.equals(rc.getType())) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                ValueMapper<Object> mapper = (ValueMapper<Object>) find(rc.getType());
                if (mapper == null) {
                    return null;
                }
                names.add(rc.getName());
                mappers.add(mapper);
                Method accessor = rc.getAccessor();
                if (!accessor.trySetAccessible()) {
                    return null;
                }
                accessors.add(accessor);
            }
            return new RecordMapper<>(recordCls, constructor, List.copyOf(names),
                    List.copyOf(mappers), List.copyOf(accessors));
        }

    }

}
