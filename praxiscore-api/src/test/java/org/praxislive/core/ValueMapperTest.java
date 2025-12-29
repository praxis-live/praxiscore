package org.praxislive.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class ValueMapperTest {

    public static enum TEST_ENUM {
        ONE, TWO, THREE, FOUR
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

}
