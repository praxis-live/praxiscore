/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2022 Neil C Smith.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PBytes;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;

/**
 * Abstract base class of all types used in messaging inside PraxisCORE.
 *
 * All Value sub-types are guaranteed to be immutable.
 *
 */
public abstract class Value {

    /**
     * Values must override the default method to return a string representation
     * that is immutable.
     *
     * @return String representation
     */
    @Override
    public abstract String toString();

    /**
     * Values must override the default hashcode method.
     *
     * @return int hashcode
     */
    @Override
    public abstract int hashCode();

    /**
     * Values must override the default equals method. This method should only
     * return <code>true</code> if the supplied Object is of the same type as
     * the implementing Value. Values of an unknown type should be coerced
     * before calling this method. This method does not have to guarantee that
     * <code>this.equals(that) == this.toString().equals(that.toString())</code>
     *
     * @param obj
     * @return boolean
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Check whether this Value is an empty value and has a zero length string
     * representation. Subclasses may wish to override this for efficiency if
     * the String representation is lazily created.
     *
     * @return boolean true if empty
     */
    public boolean isEmpty() {
        return (toString().length() == 0);
    }

    /**
     * Indicates whether some other Value is equivalent to this one. Unlike
     * {@link #equals(java.lang.Object)} this method is not symmetric - a value
     * of a different type might be equivalent to this without the other type
     * considering the reverse to be true.
     * <P>
     * The default implementation uses identity or String equality.
     *
     * @param value value to test for equivalence
     * @return true if value is equivalent to this
     */
    public boolean equivalent(Value value) {
        return this == value || this.toString().equals(value.toString());
    }

    /**
     * Access the {@link Type} of this value.
     *
     * @return type
     */
    @SuppressWarnings("unchecked")
    public Type<?> type() {
        Class<?> cls = getClass();
        Type<?> type;
        while ((type = Type.TYPES_BY_CLASS.get(cls)) == null) {
            cls = cls.getSuperclass();
            if (cls == null) {
                throw new IllegalStateException();
            }
        }
        return type;
    }

    /**
     * Use this method to return an ArgumentInfo argument that can be used to
     * refer to ANY Value subclass. Usually, you will want to get an
     * ArgumentInfo object directly from a specific Value subclass.
     *
     * @return ArgumentInfo info
     */
    public static ArgumentInfo info() {
        return ArgumentInfo.of(Value.class, null);
    }

    /**
     * Convert the provided Object into a Value. This is a lightweight
     * conversion, primarily to map Java literals to the right Value type,
     * rather than a general purpose mapping API.
     * <p>
     * Any instance of a Value subclass is returned as is. Booleans are
     * converted to PBoolean. Numbers are converted to PNumber.
     * <code>null</code> is converted to {@link PString#EMPTY}. All other types
     * are converted to a PString of their String representation.
     *
     * @param obj object to convert
     * @return value
     */
    public static Value ofObject(Object obj) {
        if (obj instanceof Value) {
            return (Value) obj;
        }
        if (obj instanceof Boolean) {
            return ((Boolean) obj) ? PBoolean.TRUE : PBoolean.FALSE;
        }
        if (obj instanceof Integer) {
            return PNumber.of(((Integer) obj));
        }
        if (obj instanceof Number) {
            return PNumber.of(((Number) obj).doubleValue());
        }
        if (obj == null) {
            return PString.EMPTY;
        }
        return PString.of(obj);
    }

    /**
     * The type of a Value. Only registered types can be used in PraxisCORE.
     * Type maps to the class or superclass of a Value, and provides additional
     * features such as simple name and conversion.
     *
     * @param <T> value subclass
     */
    public static class Type<T extends Value> {

        private final Class<T> type;
        private final String name;
        private final Function<Value, Optional<T>> converter;
        private final T emptyValue;

        Type(Class<T> type, String name, Function<Value, Optional<T>> converter) {
            this(type, name, converter, null);
        }

        Type(Class<T> type, String name, Function<Value, Optional<T>> converter, T emptyValue) {
            this.type = Objects.requireNonNull(type);
            this.name = Objects.requireNonNull(name);
            this.converter = Objects.requireNonNull(converter);
            this.emptyValue = emptyValue;
        }

        /**
         * The class that this Type maps to. May be a superclass of the actual
         * class of a Value.
         *
         * @return mapped class
         */
        public Class<T> asClass() {
            return type;
        }

        /**
         * Simple name for this Value type.
         *
         * @return type name
         */
        public String name() {
            return name;
        }

        /**
         * A convertor function that can convert a value to this type. May cast,
         * parse or otherwise convert the value. An empty Optional is returned
         * if the value does not support conversion to the required type.
         *
         * @return optional converted value
         */
        public Function<Value, Optional<T>> converter() {
            return converter;
        }

        /**
         * The empty value of this type, if this type supports empty values, as
         * defined by {@link Value#isEmpty()}.
         *
         * @return optional of empty value
         */
        public Optional<T> emptyValue() {
            return Optional.ofNullable(emptyValue);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int hashCode() {
            return type.hashCode();
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
            final Type<?> other = (Type<?>) obj;
            return Objects.equals(this.type, other.type);
        }

        /**
         * Return the Type mapping for the passed in Value class.
         *
         * @param <T> value type
         * @param cls value class
         * @return Type
         * @throws IllegalArgumentException if the class is unregistered
         */
        @SuppressWarnings("unchecked")
        public static <T extends Value> Type<T> of(Class<T> cls) {
            Type<T> type = (Type<T>) TYPES_BY_CLASS.get(cls);
            if (type == null) {
                throw new IllegalArgumentException("Unregistered Value type : " + cls.getName());
            }
            return type;
        }

        /**
         * Return the Type with the given name, or an empty optional if
         * unregistered.
         *
         * @param name type name
         * @return optional type or empty
         */
        public static Optional<Type<? extends Value>> fromName(String name) {
            return Optional.ofNullable(TYPES_BY_NAME.get(name));
        }

        /**
         * List of all registered Value Types. The returned list is read-only.
         *
         * @return list of types
         */
        public static List<Type<?>> listAll() {
            return TYPES;
        }

        private final static List<Type<?>> TYPES;
        private final static Map<Class<? extends Value>, Type<?>> TYPES_BY_CLASS;
        private final static Map<String, Type<?>> TYPES_BY_NAME;

        static {

            List<Type<?>> types = new ArrayList<>(18);

            types.add(new Type<>(Value.class, "Value", v -> Optional.of(v)));

            types.add(new Type<>(PArray.class, PArray.TYPE_NAME, PArray::from, PArray.EMPTY));
            types.add(new Type<>(PBoolean.class, PBoolean.TYPE_NAME, PBoolean::from));
            types.add(new Type<>(PBytes.class, PBytes.TYPE_NAME, PBytes::from, PBytes.EMPTY));
            types.add(new Type<>(PError.class, PError.TYPE_NAME, PError::from));
            types.add(new Type<>(PMap.class, PMap.TYPE_NAME, PMap::from, PMap.EMPTY));
            types.add(new Type<>(PNumber.class, PNumber.TYPE_NAME, PNumber::from));
            types.add(new Type<>(PReference.class, PReference.TYPE_NAME, PReference::from));
            types.add(new Type<>(PResource.class, PResource.TYPE_NAME, PResource::from));
            types.add(new Type<>(PString.class, PString.TYPE_NAME, PString::from, PString.EMPTY));

            types.add(new Type<>(ArgumentInfo.class, ArgumentInfo.TYPE_NAME, ArgumentInfo::from));
            types.add(new Type<>(ComponentInfo.class, ComponentInfo.TYPE_NAME, ComponentInfo::from));
            types.add(new Type<>(ControlInfo.class, ControlInfo.TYPE_NAME, ControlInfo::from));
            types.add(new Type<>(PortInfo.class, PortInfo.TYPE_NAME, PortInfo::from));

            types.add(new Type<>(ComponentAddress.class, ComponentAddress.TYPE_NAME, ComponentAddress::from));
            types.add(new Type<>(ControlAddress.class, ControlAddress.TYPE_NAME, ControlAddress::from));
            types.add(new Type<>(PortAddress.class, PortAddress.TYPE_NAME, PortAddress::from));

            types.add(new Type<>(ComponentType.class, ComponentType.TYPE_NAME, ComponentType::from));

            Map<Class<? extends Value>, Type<?>> typesByClass = new HashMap<>();
            Map<String, Type<?>> typesByName = new HashMap<>();

            for (Type<?> type : types) {
                typesByClass.put(type.asClass(), type);
                typesByName.put(type.name(), type);
            }

            TYPES = List.copyOf(types);
            TYPES_BY_CLASS = Map.copyOf(typesByClass);
            TYPES_BY_NAME = Map.copyOf(typesByName);

        }
    }

}
