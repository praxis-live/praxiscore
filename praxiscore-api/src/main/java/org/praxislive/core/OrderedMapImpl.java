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

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
final class OrderedMapImpl<K, V> implements OrderedMap<K, V> {

    private final Map<K, V> map;
    private final List<K> keys;

    OrderedMapImpl(List<K> keys, Map<K, V> map) {
        this.keys = keys;
        this.map = map;
    }

    @Override
    public List<K> keys() {
        return keys;
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                var keyItr = keys.iterator();
                return new Iterator<Entry<K, V>>() {
                    @Override
                    public boolean hasNext() {
                        return keyItr.hasNext();
                    }

                    @Override
                    public Entry<K, V> next() {
                        var key = keyItr.next();
                        return Map.entry(key, map.get(key));
                    }
                };
            }

            @Override
            public int size() {
                return keys.size();
            }
        };
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<K>() {
            @Override
            public Iterator<K> iterator() {
                return keys.iterator();
            }

            @Override
            public int size() {
                return keys.size();
            }
        };
    }

    @Override
    public OrderedMap<K, V> reversed() {
        return new OrderedMapImpl<>(List.copyOf(keys.reversed()), map);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Collection<V> values() {
        return new AbstractCollection<V>() {
            @Override
            public Iterator<V> iterator() {
                var keyItr = keys.iterator();
                return new Iterator<V>() {
                    @Override
                    public boolean hasNext() {
                        return keyItr.hasNext();
                    }

                    @Override
                    public V next() {
                        return map.get(keyItr.next());
                    }
                };
            }

            @Override
            public int size() {
                return keys.size();
            }
        };
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || map.equals(obj);
    }

    @Override
    public String toString() {
        return entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

}
