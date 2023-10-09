package org.praxislive.core.types;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.praxislive.core.Value;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 *
 */
public class PArrayTest {

    public PArrayTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of toString method, of class PArray.
     */
    @Test
    public void testToString() {

    }

    /**
     * Test of get method, of class PArray.
     */
    @Test
    public void testGet() throws Exception {
        PArray arr1 = PArray.parse("1 2 3 4");
        assertEquals(arr1.get(3), arr1.get(-1));
        PArray arr2 = IntStream.range(-10, 10)
                .mapToObj(arr1::get)
                .collect(PArray.collector());
        System.out.println(arr2);
        assertEquals(arr1.get(0).toString(), arr2.get(2).toString());

        PArray arr3 = PArray.parse("");
        assertEquals(arr3.get(0).toString(), "");

    }

    /**
     * Test of from method, of class PArray.
     */
    @Test
    public void testFrom() throws Exception {
        System.out.println("coerce");
        PArray startArr = PArray.of(PString.of("this has spaces"), PString.of("is"),
                PString.of("an"),
                PArray.of(PString.of("embedded"), PString.of("\\\\array")));
        String arrStr = startArr.toString();
        System.out.println(arrStr);
        PArray a1 = PArray.parse(arrStr);
        System.out.println("Array 1");
//        for (Value a : a1) {
//            System.out.println(a);
//        }
        a1.stream().forEach(System.out::println);
        System.out.println("Array 2");
        PArray a2 = PArray.from(a1.get(3)).orElseThrow();
//        for (Value a : a2) {
//            System.out.println(a);
//        }
        System.out.println(a2.stream().map(Value::toString).collect(Collectors.joining(" | ")));
        assertEquals(2, a2.size());
    }

    @Test
    public void testParse() throws Exception {
        var a1 = PArray.parse("This is an array");
        var a2 = PArray.parse("This\n is \n an \n #comment \n array");
        assertEquals(4, a2.size());
        assertEquals("array", a2.get(3).toString());
        assertTrue(a1.equivalent(a2));
    }

    @Test
    public void testCollector() throws Exception {
        PArray arr1 = PArray.parse("a3 104 {some string} c#5");
        PArray arr2 = arr1.stream().collect(PArray.collector());

        System.out.println(arr1);
        System.out.println(arr2);

        assertEquals(arr1.size(), arr2.size());

        assertTrue(arr1.equivalent(arr2));

        PArray arr4 = PArray.parse("A3 104 C#5");
        PArray arr3 = arr1.stream()
                .map(Object::toString)
                .filter(s -> !s.contains(" "))
                .map(String::toUpperCase)
                .map(PString::of)
                .collect(PArray.collector());

        System.out.println(arr3);

        assertTrue(arr3.equivalent(arr4));

    }

    @Test
    public void testAsListOf() {
        var l1 = PArray.of(Value.ofObject("value1"), Value.ofObject(2), Value.ofObject(true));
        var l1AsValue = l1.asListOf(Value.class);
        assertSame(l1.asList(), l1AsValue);
        assertThrows(IllegalArgumentException.class, () -> {
            var errList = l1.asListOf(Boolean.class);
        });

        var l2 = PArray.of(Value.ofObject(1),
                Value.ofObject(2),
                Value.ofObject(3),
                Value.ofObject(4));
        var l2AsPNumber = l2.asListOf(PNumber.class);
        assertSame(l2.asList(), l2AsPNumber);
        var l2AsInteger = l2.asListOf(Integer.class);
        assertNotSame(l2.asList(), l2AsInteger);
        assertEquals(List.of(1, 2, 3, 4), l2AsInteger);
    }

}
