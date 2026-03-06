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

import org.junit.jupiter.api.Test;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

import static org.junit.jupiter.api.Assertions.*;
import org.praxislive.core.types.PMap;

/**
 *
 */
public class ValueMapperTest {

    public static enum TEST_ENUM {
        ONE, TWO, THREE, FOUR
    }

    public static record RECORD_ONE(int value, boolean flag) {

    }

    public static record RECORD_TWO(String name, RECORD_ONE value) {

    }

    public ValueMapperTest() {
    }

    @Test
    public void testValueSubtypeMappers() {
        for (Value.Type<?> type : Value.Type.listAll()) {
            ValueMapper<? extends Value> mapper = ValueMapper.find(type.asClass());
            assertNotNull(mapper);
            Value v = mapper.fromValue(PString.EMPTY);
            assertEquals(type.emptyValue().orElse(null), v);
            v = mapper.toValue(null);
            Value empty = type.emptyValue().map(Value.class::cast).orElse(PString.EMPTY);
            assertEquals(empty, v);
        }

    }

    @Test
    public void testStringMapper() {
        String test = "Testing String";
        ValueMapper<String> mapper = ValueMapper.find(String.class);
        assertNotNull(mapper);
        assertEquals(test, mapper.fromValue(PString.of(test)));
        assertEquals(PString.EMPTY, mapper.toValue(null));
    }

    @Test
    public void testBooleanMapper() {
        ValueMapper<Boolean> mapper = ValueMapper.find(Boolean.class);
        assertNotNull(mapper);
        assertEquals(mapper, ValueMapper.find(boolean.class));
        assertTrue(mapper.fromValue(PBoolean.TRUE));
        assertThrows(IllegalArgumentException.class, () -> mapper.fromValue(PString.of("FOO")));
        assertEquals(PBoolean.TRUE, mapper.toValue(true));
        assertEquals(PBoolean.FALSE, mapper.toValue(false));
        assertEquals(PBoolean.FALSE, mapper.toValue(null));
    }

    @Test
    public void testEnumMapper() {
        ValueMapper<TEST_ENUM> mapper = ValueMapper.find(TEST_ENUM.class);
        assertNotNull(mapper);
        assertEquals(TEST_ENUM.THREE, mapper.fromValue(PString.of("THREE")));
        assertEquals(PString.of("FOUR"), mapper.toValue(TEST_ENUM.FOUR));
        assertEquals(PString.of("ONE"), mapper.toValue(null));
        assertThrows(IllegalArgumentException.class,
                () -> mapper.fromValue(PString.of("FOO")));
    }

    @Test
    public void testIntMapper() {
        ValueMapper<Integer> mapper = ValueMapper.find(Integer.class);
        assertNotNull(mapper);
        assertEquals(mapper, ValueMapper.find(int.class));
        assertEquals(12, mapper.fromValue(PString.of("12")));
        assertEquals(PNumber.of(21), mapper.toValue(21));
        assertEquals(PNumber.ZERO, mapper.toValue(null));
        assertThrows(IllegalArgumentException.class,
                () -> mapper.fromValue(PString.of("FOO")));
    }

    @Test
    public void testFloatMapper() {
        float delta = 0.0001f;
        ValueMapper<Float> mapper = ValueMapper.find(Float.class);
        assertNotNull(mapper);
        assertEquals(mapper, ValueMapper.find(float.class));
        assertEquals(123.45f, mapper.fromValue(PString.of("123.45")), delta);
        assertEquals(PNumber.of(21.1).value(),
                PNumber.from(mapper.toValue(21.1f))
                        .map(PNumber::value)
                        .orElseThrow(),
                delta);
        assertEquals(PNumber.ZERO, mapper.toValue(null));
        assertThrows(IllegalArgumentException.class,
                () -> mapper.fromValue(PString.of("FOO")));
    }

    @Test
    public void testDoubleMapper() {
        double delta = 0.0001;
        ValueMapper<Double> mapper = ValueMapper.find(Double.class);
        assertNotNull(mapper);
        assertEquals(mapper, ValueMapper.find(double.class));
        assertEquals(123.45f, mapper.fromValue(PString.of("123.45")), delta);
        assertEquals(PNumber.of(21.1).value(),
                PNumber.from(mapper.toValue(21.1))
                        .map(PNumber::value)
                        .orElseThrow(),
                delta);
        assertEquals(PNumber.ZERO, mapper.toValue(null));
        assertThrows(IllegalArgumentException.class,
                () -> mapper.fromValue(PString.of("FOO")));
    }

    @Test
    public void testRecordMapper() {
        RECORD_ONE r1 = new RECORD_ONE(42, true);
        PMap r1map = PMap.of("value", 42, "flag", true);
        ValueMapper<RECORD_ONE> r1mapper = ValueMapper.find(RECORD_ONE.class);
        assertNotNull(r1mapper);
        assertEquals(r1map, r1mapper.toValue(r1));
        assertEquals(r1, r1mapper.fromValue(r1map));

        RECORD_TWO r2 = new RECORD_TWO("ValueMapperTest", r1);
        PMap r2map = PMap.of("name", "ValueMapperTest", "value", r1map);
        ValueMapper<RECORD_TWO> r2mapper = ValueMapper.find(RECORD_TWO.class);
        assertNotNull(r2mapper);
        assertEquals(r2map, r2mapper.toValue(r2));
        assertEquals(r2, r2mapper.fromValue(r2map));

        PMap schema = PMap.of("value",
                ArgumentInfo.of(PNumber.class, PMap.of(PNumber.KEY_IS_INTEGER, true)),
                "flag", ArgumentInfo.of(PBoolean.class));
        ArgumentInfo info1 = r1mapper.createInfo();
        assertEquals(schema, info1.properties().get(PMap.KEY_SCHEMA));
        ArgumentInfo info2 = r2mapper.createInfo();
        assertEquals(info1, PMap.from(
                info2.properties().get(PMap.KEY_SCHEMA)).orElseThrow()
                .get("value")
        );

    }

}
