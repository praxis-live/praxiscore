/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2024 Neil C Smith.
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

import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Stream;

/**
 * A {@link Map} with consistent order of entries. All static factory methods
 * produce unmodifiable maps.
 *
 * @param <K> key type
 * @param <V> value type
 */
public sealed interface OrderedMap<K, V> extends SequencedMap<K, V> permits OrderedMapImpl {

    /**
     * A {@link List} containing the keys of this OrderedMap.
     * <p>
     * Because neither Map nor Set equality depends on order, code needing to
     * verify whether an OrderedMap contains the same mappings in the same order
     * should also check for equality of the keys list.
     * <p>
     * This method may also be more efficient than calling {@link #keySet()}.
     *
     * @return map keys as list
     */
    public List<K> keys();

    @Override
    public OrderedMap<K, V> reversed();

    /**
     * Compares the provided object with this map for equality in accordance
     * with the specification of {@link Map#equals(java.lang.Object)}.
     * <p>
     * Because Map equality does not depends on order, code needing to verify
     * whether an OrderedMap contains the same mappings in the same order should
     * also check for equality of the {@link #keys()} list.
     *
     * @param obj object to compare
     * @return true if the object is a map containing the same mappings, not
     * necessarily in the same order
     */
    @Override
    public boolean equals(Object obj);

    /**
     * Returns an empty OrderedMap.
     *
     * @param <K> key type
     * @param <V> value type
     * @return empty map
     */
    public static <K, V> OrderedMap<K, V> of() {
        return new OrderedMapImpl<>(List.of(), Map.of());
    }

    /**
     * Returns an OrderedMap with a single mapping.
     *
     * @param <K> key type
     * @param <V> value type
     * @param k1 key
     * @param v1 value
     * @return an OrderedMap of the provided mapping
     * @throws NullPointerException if key or value are null
     */
    public static <K, V> OrderedMap<K, V> of(K k1, V v1) {
        return new OrderedMapImpl<>(List.of(k1), Map.of(k1, v1));
    }

    /**
     * Returns an OrderedMap with two mappings.
     *
     * @param <K> key type
     * @param <V> value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @return an OrderedMap of the provided mappings
     * @throws NullPointerException if any keys or values are null
     * @throws IllegalArgumentException if any keys are duplicated
     */
    public static <K, V> OrderedMap<K, V> of(K k1, V v1, K k2, V v2) {
        return new OrderedMapImpl<>(List.of(k1, k2), Map.of(k1, v1, k2, v2));
    }

    /**
     * Returns an OrderedMap with three mappings.
     *
     * @param <K> key type
     * @param <V> value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @return an OrderedMap of the provided mappings
     * @throws NullPointerException if any keys or values are null
     * @throws IllegalArgumentException if any keys are duplicated
     */
    public static <K, V> OrderedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        return new OrderedMapImpl<>(List.of(k1, k2, k3), Map.of(k1, v1, k2, v2, k3, v3));
    }

    /**
     * Returns an OrderedMap with four mappings.
     *
     * @param <K> key type
     * @param <V> value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @param k4 fourth key
     * @param v4 fourth value
     * @return an OrderedMap of the provided mappings
     * @throws NullPointerException if any keys or values are null
     * @throws IllegalArgumentException if any keys are duplicated
     */
    public static <K, V> OrderedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return new OrderedMapImpl<>(List.of(k1, k2, k3, k4), Map.of(k1, v1, k2, v2, k3, v3, k4, v4));
    }

    /**
     * Returns an OrderedMap with five mappings.
     *
     * @param <K> key type
     * @param <V> value type
     * @param k1 first key
     * @param v1 first value
     * @param k2 second key
     * @param v2 second value
     * @param k3 third key
     * @param v3 third value
     * @param k4 fourth key
     * @param v4 fourth value
     * @param k5 fifth key
     * @param v5 fifth value
     * @return an OrderedMap of the provided mappings
     * @throws NullPointerException if any keys or values are null
     * @throws IllegalArgumentException if any keys are duplicated
     */
    public static <K, V> OrderedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3,
            K k4, V v4, K k5, V v5) {
        return new OrderedMapImpl<>(List.of(k1, k2, k3, k4, k5),
                Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5));
    }

    /**
     * Returns an OrderedMap from the given entries. The entries themselves are
     * not stored in the map.
     *
     * @param <K> key type
     * @param <V> value type
     * @param entries map entries
     * @return an OrderedMap of the provided mappings
     * @throws NullPointerException if any keys or values are null, or if
     * {@code entries} is null
     * @throws IllegalArgumentException if any keys are duplicated
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <K, V> OrderedMap<K, V> ofEntries(Map.Entry<? extends K, ? extends V>... entries) {
        var map = Map.ofEntries(entries);
        var list = (List<K>) Stream.of(entries)
                .map(Map.Entry::getKey)
                .toList();
        return new OrderedMapImpl<>(list, map);
    }

    /**
     * Return an OrderedMap that contains a copy of the provided Map. The order
     * will be the same as the iteration order of the map's entry set.
     * <p>
     * If the map is already an unmodifiable OrderedMap it may be returned as
     * is.
     *
     * @param <K> key type
     * @param <V> value type
     * @param map map to copy
     * @return an ordered map copy of the provided map
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <K, V> OrderedMap<K, V> copyOf(Map<? extends K, ? extends V> map) {
        if (map instanceof OrderedMapImpl) {
            return (OrderedMap<K, V>) map;
        } else {
            return ofEntries(map.entrySet().toArray(Map.Entry[]::new));
        }
    }

}
