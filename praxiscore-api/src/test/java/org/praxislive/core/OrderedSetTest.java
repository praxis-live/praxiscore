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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class OrderedSetTest {

    private final String e1 = "ELEMENT ONE";
    private final String e2 = "ELEMENT TWO";
    private final String e3 = "ELEMENT THREE";
    private final String e4 = "ELEMENT FOUR";
    private final String e5 = "ELEMENT FIVE";
    private final String e6 = "ELEMENT SIX";

    @Test
    public void testEquality() {
        var set = Set.of(e1, e2, e3, e4, e5, e6);
        var os1 = OrderedSet.of(e1, e2, e3, e4, e5, e6);
        var os2 = OrderedSet.of(e6, e5, e4, e3, e2, e1);

        assertEquals(set, os1);
        assertEquals(set, os2);
        assertEquals(os1, os2);

        assertNotEquals(os1.values(), os2.values());
        assertEquals(os1.values(), os2.reversed().values());
    }

    @Test
    public void testOrdering() {
        var set = OrderedSet.of(e1, e2, e3, e4);
        var values = set.stream().toList();
        assertEquals(List.of(e1, e2, e3, e4), values);
        var reversed = set.reversed();
        assertEquals(set, reversed);
        assertEquals(List.of(e4, e3, e2, e1), reversed.values());
        values = reversed.stream().toList();
        assertEquals(List.of(e4, e3, e2, e1), values);
    }

    @Test
    public void testOrderingOfCopy() {
        var lhs = new LinkedHashSet<String>();
        lhs.addAll(List.of(e1, e2, e3, e4, e5, e6));
        var set = OrderedSet.copyOf(lhs);
        assertEquals(lhs, set);
        assertEquals(set.values(), lhs.stream().toList());
        var reversed = set.reversed();
        var checkCopy = OrderedSet.copyOf(reversed);
        assertSame(reversed, checkCopy);
        assertNotEquals(reversed.values(), lhs.stream().toList());
        lhs.clear();
        lhs.addAll(reversed);
        assertEquals(reversed, lhs);
        assertEquals(reversed.values(), lhs.stream().toList());
    }

    @Test
    public void testImmutability() {
        var set = OrderedSet.of(e3, e4, e5);
        assertThrows(UnsupportedOperationException.class, set::clear);
        assertThrows(UnsupportedOperationException.class, () -> set.add(e2));
        assertThrows(UnsupportedOperationException.class, () -> set.addFirst(e1));
        assertThrows(UnsupportedOperationException.class, () -> set.addLast(e6));
        assertThrows(UnsupportedOperationException.class, () -> set.addAll(List.of(e1, e2, e6)));
        assertThrows(UnsupportedOperationException.class, () -> set.remove(e4));
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(List.of(e2, e3)));
        assertThrows(UnsupportedOperationException.class, () -> set.values().add(e6));
        assertThrows(UnsupportedOperationException.class, () -> set.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> {
            var itr = set.iterator();
            itr.next();
            itr.remove();
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            var itr = set.values().iterator();
            itr.next();
            itr.remove();
        });
    }

}
