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
package org.praxislive.core.types;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import org.praxislive.core.OrderedMap;
import org.praxislive.core.Value;
import org.praxislive.core.ValueFormatException;
import org.praxislive.core.ValueMapper;

/**
 * An ordered map of Strings to Values.
 */
public final class PMap extends Value {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "Map";

    /**
     * An empty PMap.
     */
    public final static PMap EMPTY = new PMap(OrderedMap.of());

    /**
     * An operator for use with
     * {@link #merge(org.praxislive.core.types.PMap, org.praxislive.core.types.PMap, java.util.function.BinaryOperator)}
     * that only adds mappings where the key is not present in the base map.
     */
    public final static BinaryOperator<Value> IF_ABSENT = (Value oldValue, Value newValue)
            -> oldValue == null ? newValue : oldValue;

    /**
     * An operator for use with
     * {@link #merge(org.praxislive.core.types.PMap, org.praxislive.core.types.PMap, java.util.function.BinaryOperator)}
     * that will replace mapped values in the base map, unless the new value is
     * empty in which case the mapping is removed.
     */
    public final static BinaryOperator<Value> REPLACE = (Value oldValue, Value newValue)
            -> newValue == null || newValue.isEmpty() ? null : newValue;

    private final OrderedMap<String, Value> map;
    private volatile String str;

    private PMap(OrderedMap<String, Value> map) {
        this(map, null);
    }

    private PMap(OrderedMap<String, Value> map, String str) {
        this.map = map;
        this.str = str;
    }

    /**
     * Get the value for the given key, or null if the key does not exist.
     *
     * @param key map key
     * @return mapped value or null
     */
    public Value get(String key) {
        return map.get(key);
    }

    /**
     * Get a boolean value from the map, returning a default value if the key is
     * not mapped or mapped to a value that cannot be converted to a boolean.
     *
     * @param key map key
     * @param def default if unmapped or invalid
     * @return value or default
     */
    public boolean getBoolean(String key, boolean def) {
        return Optional.ofNullable(get(key))
                .flatMap(PBoolean::from)
                .map(PBoolean::value)
                .orElse(def);
    }

    /**
     * Get an integer value from the map, returning a default value if the key
     * is not mapped or mapped to a value that cannot be converted to an
     * integer.
     *
     * @param key map key
     * @param def default if unmapped or invalid
     * @return value or default
     */
    public int getInt(String key, int def) {
        return Optional.ofNullable(get(key))
                .flatMap(PNumber::from)
                .map(PNumber::toIntValue)
                .orElse(def);
    }

    /**
     * Get a double value from the map, returning a default value if the key is
     * not mapped or mapped to a value that cannot be converted to a double.
     *
     * @param key map key
     * @param def default if unmapped or invalid
     * @return value or default
     */
    public double getDouble(String key, double def) {
        return Optional.ofNullable(get(key))
                .flatMap(PNumber::from)
                .map(PNumber::value)
                .orElse(def);
    }

    /**
     * Get a String value from the map, returning a default value if the key is
     * not mapped.
     *
     * @param key map key
     * @param def default if unmapped
     * @return value or default
     */
    public String getString(String key, String def) {
        Value val = get(key);
        if (val != null) {
            return val.toString();
        }
        return def;
    }

    /**
     * Size of the map. This is the number of key-value pairs.
     *
     * @return size of the map
     */
    public int size() {
        return map.size();
    }

    /**
     * List of map keys. This is returned as a List as the keys are ordered.
     *
     * @return map keys
     */
    public List<String> keys() {
        return map.keys();
    }

    /**
     * An unmodifiable {@link OrderedMap} view of this PMap.
     *
     * @return map view
     */
    public OrderedMap<String, Value> asMap() {
        return map;
    }

    /**
     * Access an unmodifiable {@link OrderedMap} view of this PMap as a map of
     * the provided value type.
     * <p>
     * If the provided type is {@link Value} then this method acts the same as
     * calling {@link #asMap()}.
     * <p>
     * If the provided type is a Value subclass and all values in the map
     * returned by {@link #asMap()} are of this type, then the map is cast and
     * returned.
     * <p>
     * If the provided type is a Value subclass or any other type supported by
     * {@link ValueMapper} then a new map will be returned with the values
     * converted to the required type.
     * <p>
     * This method throws an {@link IllegalArgumentException} if no value mapper
     * exists for the provided type, of if not all map values can be converted
     * to the provided type.
     *
     * @param <T> value type
     * @param type class of the value type
     * @return ordered map view of the provided value type
     * @throws IllegalArgumentException if there is no mapper for the provided
     * type, or if all values cannot be converted to the provided type
     */
    @SuppressWarnings("unchecked")
    public <T> OrderedMap<String, T> asMapOf(Class<T> type) {
        if (Value.class == type) {
            return (OrderedMap<String, T>) map;
        }
        if (Value.class.isAssignableFrom(type)
                && map.values().stream().allMatch(v -> type.isInstance(v))) {
            return (OrderedMap<String, T>) map;
        }
        var mapper = ValueMapper.find(type);
        if (mapper == null) {
            throw new IllegalArgumentException("No mapper found for type : " + type);
        }
        var lhm = new LinkedHashMap<String, T>(map.size());
        for (var entry : map.entrySet()) {
            var value = mapper.fromValue(entry.getValue());
            if (value == null) {
                throw new IllegalArgumentException();
            }
            lhm.put(entry.getKey(), value);
        }
        return OrderedMap.copyOf(lhm);
    }

    @Override
    public String toString() {
        if (str == null) {
            if (!map.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String key : map.keys()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(Utils.escape(key));
                    sb.append(" ");
                    Value value = map.get(key);
                    if (value instanceof PArray || value instanceof PMap
                            || value instanceof MapBasedValue) {
                        sb.append('{')
                                .append(value.toString())
                                .append('}');
                    } else {
                        sb.append(Utils.escape(String.valueOf(value)));
                    }
                }
                str = sb.toString();
            } else {
                str = "";
            }
        }
        return str;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean equivalent(Value arg) {
        if (arg == this) {
            return true;
        }
        try {
            PMap other = PMap.coerce(arg);
            if (!map.keys().equals(other.map.keys())) {
                return false;
            }
            for (String key : map.keys()) {
                if (!Utils.equivalent(map.get(key), other.map.get(key))) {
                    return false;
                }
            }
            return true;
        } catch (ValueFormatException ex) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(map.keys(), map);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof PMap other) {
            return map.keys().equals(other.map.keys()) && map.equals(other.map);
        }
        return false;
    }

    /**
     * Create a PMap with one mapping. Map values that are not {@link Value}
     * types will be converted automatically.
     *
     * @param key map key
     * @param value mapped value
     * @return new PMap
     */
    public static PMap of(String key, Object value) {
        return new PMap(OrderedMap.of(key, Value.ofObject(value)));
    }

    /**
     * Create a PMap with two mappings. Map values that are not {@link Value}
     * types will be converted automatically.
     *
     * @param key1 first map key
     * @param value1 first mapped value
     * @param key2 second map key
     * @param value2 second mapped value
     * @return new PMap
     */
    public static PMap of(String key1, Object value1,
            String key2, Object value2) {
        return new PMap(OrderedMap.of(
                key1, Value.ofObject(value1),
                key2, Value.ofObject(value2)
        ));
    }

    /**
     * Create a PMap with three mappings. Map values that are not {@link Value}
     * types will be converted automatically.
     *
     * @param key1 first map key
     * @param value1 first mapped value
     * @param key2 second map key
     * @param value2 second mapped value
     * @param key3 third map key
     * @param value3 third mapped value
     * @return new PMap
     */
    public static PMap of(String key1, Object value1,
            String key2, Object value2,
            String key3, Object value3) {
        return new PMap(OrderedMap.of(
                key1, Value.ofObject(value1),
                key2, Value.ofObject(value2),
                key3, Value.ofObject(value3)
        ));
    }

    /**
     * Create a PMap with four mappings. Map values that are not {@link Value}
     * types will be converted automatically.
     *
     * @param key1 first map key
     * @param value1 first mapped value
     * @param key2 second map key
     * @param value2 second mapped value
     * @param key3 third map key
     * @param value3 third mapped value
     * @param key4 fourth map key
     * @param value4 fourth mapped value
     * @return new PMap
     */
    public static PMap of(String key1, Object value1,
            String key2, Object value2,
            String key3, Object value3,
            String key4, Object value4) {
        return new PMap(OrderedMap.of(
                key1, Value.ofObject(value1),
                key2, Value.ofObject(value2),
                key3, Value.ofObject(value3),
                key4, Value.ofObject(value4)
        ));
    }

    /**
     * Create a PMap with five mappings. Map values that are not {@link Value}
     * types will be converted automatically.
     *
     * @param key1 first map key
     * @param value1 first mapped value
     * @param key2 second map key
     * @param value2 second mapped value
     * @param key3 third map key
     * @param value3 third mapped value
     * @param key4 fourth map key
     * @param value4 fourth mapped value
     * @param key5 fifth map key
     * @param value5 fifth mapped value
     * @return new PMap
     */
    public static PMap of(String key1, Object value1,
            String key2, Object value2,
            String key3, Object value3,
            String key4, Object value4,
            String key5, Object value5) {
        return new PMap(OrderedMap.of(
                key1, Value.ofObject(value1),
                key2, Value.ofObject(value2),
                key3, Value.ofObject(value3),
                key4, Value.ofObject(value4),
                key5, Value.ofObject(value5)
        ));
    }

    /**
     * Create a PMap of the provided mappings. Use
     * {@link #entry(java.lang.String, java.lang.Object)} to create entries.
     *
     * @param entries map entries
     * @return new PMap
     */
    public static PMap ofEntries(Entry... entries) {
        return new PMap(OrderedMap.ofEntries(entries));
    }

    /**
     * Create a {@link PMap.Entry} for creating a PMap. Values that are not
     * {@link Value} types will be converted automatically.
     *
     * @param key map key
     * @param value map value
     * @return map entry
     */
    public static PMap.Entry entry(String key, Object value) {
        return new PMap.Entry(key, Value.ofObject(value));
    }

    /**
     * Create a PMap from the provided {@link Map}. The order of keys in the
     * PMap will be based on the iteration order of the map.
     * <p>
     * If the provided map is an {@link OrderedMap} then no copy of the map will
     * be made and the map returned by {@link #asMap()} will be the map passed
     * in to this method.
     *
     * @param map map to copy
     * @return new PMap
     */
    @SuppressWarnings("unchecked")
    public static PMap ofMap(Map<String, ? extends Value> map) {
        return new PMap((OrderedMap<String, Value>) OrderedMap.copyOf(map));
    }

    /**
     * Parse the given text into a PMap.
     *
     * @param text text to parse
     * @return parsed PArray
     * @throws ValueFormatException
     */
    public static PMap parse(String text) throws ValueFormatException {
        PArray arr = PArray.parse(text);
        if (arr.isEmpty()) {
            return PMap.EMPTY;
        }
        int size = arr.size();
        if (size % 2 != 0) {
            throw new ValueFormatException("Uneven number of tokens passed to PMap.valueOf()");
        }
        PMap.Builder builder = builder();
        for (int i = 0; i < size; i += 2) {
            builder.put(
                    arr.get(i).toString(),
                    arr.get(i + 1));
        }
        return builder.build(text);
    }

    private static PMap coerce(Value arg) throws ValueFormatException {
        if (arg instanceof PMap) {
            return (PMap) arg;
        } else {
            return parse(arg.toString());
        }
    }

    /**
     * Cast or convert the provided value into a PMap, wrapped in an Optional.
     * If the value is already a PMap, the Optional will wrap the existing
     * value. If the value is not a PMap and cannot be converted into one, an
     * empty Optional is returned.
     *
     * @param value value
     * @return optional PMap
     */
    public static Optional<PMap> from(Value value) {
        try {
            return Optional.of(coerce(value));
        } catch (ValueFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * Create a new PMap by merging the additional map into the base map,
     * according to the result of the provided operator. The operator will be
     * called for all values in the additional map, even if no mapping exists in
     * the base map. The operator must be able to handle null input. The
     * operator should return the resulting mapped value, or null to remove the
     * mapping. See {@link #REPLACE} and {@link #IF_ABSENT} for operators that
     * should cover most common usage.
     *
     * @param base base map
     * @param additional additional map
     * @param operator operator to compute result value
     * @return new PMap
     */
    public static PMap merge(PMap base, PMap additional, BinaryOperator<Value> operator) {
        Objects.requireNonNull(base);
        if (additional.isEmpty()) {
            return base;
        }
        Objects.requireNonNull(operator);
        LinkedHashMap<String, Value> result = new LinkedHashMap<>();
        base.keys().forEach(key -> result.put(key, base.map.get(key)));
        additional.keys().forEach(key -> {
            Value baseValue = result.get(key);
            Value addValue = additional.get(key);
            Value resultValue = operator.apply(baseValue, addValue);
            if (resultValue == null) {
                result.remove(key);
            } else {
                result.put(key, resultValue);
            }
        });
        return new PMap(OrderedMap.copyOf(result));
    }

    /**
     * Create a PMap.Builder.
     *
     * @return new PMap.Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A PMap builder.
     */
    public static class Builder {

        private final LinkedHashMap<String, Value> data;

        private Builder() {
            data = new LinkedHashMap<>();
        }

        /**
         * Add a mapping for the given key and value.
         *
         * @param key map key
         * @param value mapped value
         * @return this
         */
        public Builder put(String key, Value value) {
            data.put(key, value);
            return this;
        }

        /**
         * Add a mapping for the given key and value.
         *
         * @param key map key
         * @param value mapped value
         * @return this
         */
        public Builder put(String key, boolean value) {
            return put(key, PBoolean.of(value));
        }

        /**
         * Add a mapping for the given key and value.
         *
         * @param key map key
         * @param value mapped value
         * @return this
         */
        public Builder put(String key, int value) {
            return put(key, PNumber.of(value));
        }

        /**
         * Add a mapping for the given key and value.
         *
         * @param key map key
         * @param value mapped value
         * @return this
         */
        public Builder put(String key, double value) {
            return put(key, PNumber.of(value));
        }

        /**
         * Add a mapping for the given key and value.
         *
         * @param key map key
         * @param value mapped value
         * @return this
         */
        public Builder put(String key, String value) {
            return put(key, PString.of(value));
        }

        /**
         * Add a mapping for the given key and value.
         *
         * @param key map key
         * @param value mapped value
         * @return this
         */
        public Builder put(String key, Object value) {
            return put(key, Value.ofObject(value));
        }

        /**
         * Build the PMap.
         *
         * @return new PMap
         */
        public PMap build() {
            return new PMap(OrderedMap.copyOf(data));
        }

        private PMap build(String str) {
            return new PMap(OrderedMap.copyOf(data), str);
        }

    }

    public static final class Entry implements Map.Entry<String, Value> {

        private final String key;
        private final Value value;

        private Entry(String key, Value value) {
            this.key = Objects.requireNonNull(key);
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Value getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return key.hashCode() ^ value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Map.Entry<?, ?> entry
                    && key.equals(entry.getKey())
                    && value.equals(entry.getValue());
        }

        @Override
        public Value setValue(Value value) {
            throw new UnsupportedOperationException("PMap.Entry is immutable");
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }

    }

    /**
     * An abstract superclass for values that are backed solely by a PMap.
     * Subclassing this type can help with efficient serialization of the
     * underlying representation. The concrete value type must be able to
     * construct an equivalent value entirely from the PMap returned from
     * {@link #dataMap()}.
     * <p>
     * The toString, equals, equivalent and hashCode methods are all implemented
     * based solely on the map data.
     */
    public static abstract class MapBasedValue extends Value {

        private final PMap data;

        /**
         * Construct a MapBasedValue using the provided data map.
         *
         * @param data data map
         */
        protected MapBasedValue(PMap data) {
            this.data = Objects.requireNonNull(data);
        }

        /**
         * Access the backing PMap data.
         *
         * @return backing map
         */
        public final PMap dataMap() {
            return data;
        }

        @Override
        public final String toString() {
            return data.toString();
        }

        @Override
        public final int hashCode() {
            return data.hashCode();
        }

        @Override
        public final boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MapBasedValue other = (MapBasedValue) obj;
            return Objects.equals(this.data, other.data);
        }

        @Override
        public final boolean equivalent(Value value) {
            if (this == value) {
                return true;
            }
            if (value instanceof MapBasedValue mapBased) {
                return data.equivalent(mapBased.data);
            } else {
                return PMap.from(value)
                        .map(data::equivalent)
                        .orElse(false);
            }

        }

    }
}
