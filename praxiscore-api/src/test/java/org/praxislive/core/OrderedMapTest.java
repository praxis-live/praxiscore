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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class OrderedMapTest {

    private final String k1 = "KEY ONE";
    private final String k2 = "KEY TWO";
    private final String k3 = "KEY THREE";
    private final Integer v1 = 1001001;
    private final Integer v2 = 2002002;
    private final Integer v3 = 3003003;

    @Test
    public void testToString() {

        LinkedHashMap<String, Integer> lhm = new LinkedHashMap<>();
        lhm.put(k1, v1);
        lhm.put(k2, v2);
        lhm.put(k3, v3);

        OrderedMap<String, Integer> om = OrderedMap.of(k1, v1, k2, v2, k3, v3);
        assertEquals(lhm, om);
        assertEquals(om, lhm);
        assertEquals(lhm.toString(), om.toString());

    }

    @Test
    public void testEquality() {
        var map = Map.of(k1, v1, k2, v2, k3, v3);
        var om1 = OrderedMap.of(k1, v1, k2, v2, k3, v3);
        var om2 = OrderedMap.of(k3, v3, k2, v2, k1, v1);

        assertEquals(map, om1);
        assertEquals(map, om2);
        assertEquals(om1, om2);

        assertEquals(map.keySet(), om1.keySet());
        assertEquals(om1.keySet(), om2.keySet());

        assertEquals(map.entrySet(), om1.entrySet());
        assertEquals(om1.entrySet(), om2.entrySet());

        assertNotEquals(om1.keys(), om2.keys());

    }

    @Test
    public void testOrdering() {
        var map = OrderedMap.of(k1, v1, k2, v2, k3, v3);
        var values = map.entrySet().stream()
                .map(Map.Entry::getValue)
                .toList();
        assertEquals(List.of(v1, v2, v3), values);
        var reversed = map.reversed();
        assertEquals(map, reversed);
        assertEquals(List.of(k3, k2, k1), reversed.keys());
        values = reversed.entrySet().stream()
                .map(Map.Entry::getValue)
                .toList();
        assertEquals(List.of(v3, v2, v1), values);
    }

    @Test
    public void testOfEntries() {
        var map1 = OrderedMap.of(k1, v1, k2, v2, k3, v3);
        var map2 = OrderedMap.ofEntries(
                Map.entry(k1, v1),
                Map.entry(k2, v2),
                Map.entry(k3, v3)
        );
        assertEquals(map1, map2);
        assertEquals(map1.keys(), map2.keys());

        assertThrows(IllegalArgumentException.class, () -> {
            var badMap = OrderedMap.ofEntries(
                    Map.entry(k1, v1),
                    Map.entry(k2, v2),
                    Map.entry(k3, v3),
                    Map.entry(k3, v1)
            );
        });
    }

    @Test
    public void testImmutability() {
        var map = OrderedMap.of(k1, v1, k2, v2, k3, v3);
        assertThrows(UnsupportedOperationException.class, map::clear);
        assertThrows(UnsupportedOperationException.class, () -> map.put("KEY", 42));
        assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent("KEY", 42));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(Map.of("KEY", 42)));
        assertThrows(UnsupportedOperationException.class, () -> map.compute(k1, (k, v) -> 42));
        assertThrows(UnsupportedOperationException.class, () -> map.computeIfAbsent("KEY", k -> 42));
        assertThrows(UnsupportedOperationException.class, () -> map.computeIfPresent(k1, (k, v) -> 42));
        assertThrows(UnsupportedOperationException.class, () -> map.merge(k1, 21, (o, n) -> 42));
        assertThrows(UnsupportedOperationException.class, () -> map.replaceAll((k, v) -> 42));
        assertThrows(UnsupportedOperationException.class, () -> map.replace(k1, 42));
        assertThrows(UnsupportedOperationException.class, () -> map.replace(k1, v1, 42));
        assertThrows(UnsupportedOperationException.class, () -> map.keys().clear());
        assertThrows(UnsupportedOperationException.class, () -> {
            var itr = map.keys().iterator();
            itr.next();
            itr.remove();
        });
        assertThrows(UnsupportedOperationException.class, () -> map.keySet().clear());
        assertThrows(UnsupportedOperationException.class, () -> map.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> map.entrySet().clear());
        assertThrows(UnsupportedOperationException.class, () -> map.entrySet().add(Map.entry("KEY", 42)));
    }

}
