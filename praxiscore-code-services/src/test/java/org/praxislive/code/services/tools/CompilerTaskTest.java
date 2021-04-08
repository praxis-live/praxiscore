package org.praxislive.code.services.tools;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 *
 */
public class CompilerTaskTest {

    private static final String SHARED_TEST_CLASS = "SHARED.Test";
    private static final String SHARED_TEST_SOURCE = "package SHARED;\n\n"
            + "public class Test {\n"
            + "  public static int value() {\n"
            + "    return 42;\n"
            + "  }\n"
            + "}\n";

    public CompilerTaskTest() {
    }

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {

    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of compile method, of class CompilerTask.
     */
    @Test
    public void testCompile() throws Exception {
        assumeFalse(ToolProvider.getSystemJavaCompiler() == null, "No compiler available");

        CompilerTask task = CompilerTask.create(Map.of(SHARED_TEST_CLASS, SHARED_TEST_SOURCE));
        task.options(List.of("--release", "11"));

        Map<String, byte[]> classes = task.compile();

        assertTrue(classes.containsKey(SHARED_TEST_CLASS));

        ByteMapCL classloader = new ByteMapCL(classes, this.getClass().getClassLoader());
        Class<?> cls = Class.forName(SHARED_TEST_CLASS, true, classloader);

        int val = (int) cls.getMethod("value").invoke(null);
        assertEquals(42, val);
    }

    @Test
    public void testCompileLayers() throws Exception {
        assumeFalse(ToolProvider.getSystemJavaCompiler() == null, "No compiler available");

        CompilerTask task = CompilerTask.create(Map.of(SHARED_TEST_CLASS, SHARED_TEST_SOURCE));
        task.options(List.of("--release", "11"));

        Map<String, byte[]> parentClasses = task.compile();
        byte[] existing = parentClasses.get(SHARED_TEST_CLASS);
        assertNotNull(existing, "Shared class not compiled");

        String code = "package foo;\n"
                + "import SHARED.Test;\n"
                + "public class Bar {\n"
                + "  public static int value() {\n"
                + "    return Test.value();\n"
                + "  }\n"
                + "}\n";
        
        CompilerTask task2 = CompilerTask.create(Map.of("foo.Bar", code));
        task2.options(List.of("--release", "11"));
        task2.existingClasses(Map.of(SHARED_TEST_CLASS, () -> new ByteArrayInputStream(existing)));
        
        Map<String, byte[]> classes = task2.compile();
        
        assertTrue(classes.containsKey("foo.Bar"), "Class foo.Bar not found");
        assertEquals(1, classes.size(), "Classes contains more classes than expected");
        
        ByteMapCL parentClassloader = new ByteMapCL(parentClasses, this.getClass().getClassLoader());
        ByteMapCL classloader = new ByteMapCL(classes, parentClassloader);
        
        Class<?> cls = Class.forName("foo.Bar", true, classloader);
        int val = (int) cls.getMethod("value").invoke(null);
        assertEquals(42, val);

    }

    private static class ByteMapCL extends ClassLoader {

        private final Map<String, byte[]> classes;

        ByteMapCL(Map<String, byte[]> classes, ClassLoader parent) {
            super(parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] data = classes.get(name);
            if (data == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, data, 0, data.length);
        }

    }

}
