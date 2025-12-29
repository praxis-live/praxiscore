package org.praxislive.core.types;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.praxislive.core.AbstractTestBase;
import org.praxislive.core.Value;

import static org.junit.jupiter.api.Assertions.*;

public class PArrayTest extends AbstractTestBase {

    public PArrayTest() {
    }

    @Test
    public void testGet() throws Exception {
        PArray arr1 = PArray.parse("1 2 3 4");
        assertEquals(arr1.get(3), arr1.get(-1));
        PArray arr2 = IntStream.range(-10, 10)
                .mapToObj(arr1::get)
                .collect(PArray.collector());
        log(arr2);
        assertEquals(arr1.get(0).toString(), arr2.get(2).toString());

        PArray arr3 = PArray.parse("");
        assertEquals("", arr3.get(0).toString());

    }

    @Test
    public void testFrom() throws Exception {
        PArray startArr = PArray.of(PString.of("this has spaces"), PString.of("is"),
                PString.of("an"),
                PArray.of(PString.of("embedded"), PString.of("\\\\array")));
        String arrStr = startArr.toString();
        log(arrStr);

        PArray a1 = PArray.parse(arrStr);
        PArray a2 = PArray.from(a1.get(3)).orElseThrow();
        log("Array 1");
        log(a1.stream().map(Value::toString).collect(Collectors.joining("\n")));
        log("Array 2");
        log(a2.stream().map(Value::toString).collect(Collectors.joining("\n")));
        assertEquals(2, a2.size());
    }

    @Test
    public void testParse() throws Exception {
        PArray a1 = PArray.parse("This is an array");
        PArray a2 = PArray.parse("This\n is \n an \n #comment \n array");
        assertEquals(4, a2.size());
        assertEquals("array", a2.get(3).toString());
        assertTrue(a1.equivalent(a2));
    }

    @Test
    public void testPrint() throws Exception {
        PArray array = PArray.ofObjects(1, 2, 3, 4);
        String print = array.print();
        log(print);
        assertEquals("1 2 3 4", print);
        PArray parsed = PArray.parse(print);
        assertTrue(array.equivalent(parsed));

        array = Stream.of("A", "B", "C\nD\nE", "F")
                .map(Value::ofObject)
                .collect(PArray.collector());
        print = array.print();
        log(print);
        assertEquals("A\nB\n{C\nD\nE}\nF", print);
        parsed = PArray.parse(print);
        assertTrue(array.equivalent(parsed));

        array = PArray.ofObjects("This is a longer String", PMap.of("key", "value"));
        print = array.print();
        log(print);
        assertEquals("""
                     "This is a longer String"
                     {
                       key value
                     }""", print);
        parsed = PArray.parse(print);
        assertTrue(array.equivalent(parsed));

        array = PArray.of(
                PResource.parse("file:/path/to/a/file1"),
                PResource.parse("file:/path/to/a/file2"),
                PResource.parse("file:/path/to/a/file3")
        );
        print = array.print();
        log(print);
        assertEquals("""
                     file:/path/to/a/file1
                     file:/path/to/a/file2
                     file:/path/to/a/file3""", print);
        parsed = PArray.parse(print);
        assertTrue(array.equivalent(parsed));
    }

    @Test
    public void testCollector() throws Exception {
        PArray arr1 = PArray.parse("a3 104 {some string} c#5");
        PArray arr2 = arr1.stream().collect(PArray.collector());

        log(arr1);
        log(arr2);

        assertEquals(arr1.size(), arr2.size());

        assertTrue(arr1.equivalent(arr2));

        PArray arr4 = PArray.parse("A3 104 C#5");
        PArray arr3 = arr1.stream()
                .map(Object::toString)
                .filter(s -> !s.contains(" "))
                .map(String::toUpperCase)
                .map(PString::of)
                .collect(PArray.collector());

        log(arr3);

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
