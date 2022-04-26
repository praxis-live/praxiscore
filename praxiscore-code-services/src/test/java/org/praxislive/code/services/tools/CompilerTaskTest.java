package org.praxislive.code.services.tools;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.praxislive.code.CodeDelegate;
import org.praxislive.code.DefaultCodeDelegate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 *
 */
public class CompilerTaskTest {

    private static final String CLASS_PATH = System.getProperty("java.class.path", "");
    private static final String MODULE_PATH = System.getProperty("jdk.module.path", "");

    private static final List<String> DEFAULT_COMPILER_OPTIONS = List.of(
            "-Xlint:all",
            "-proc:none",
            "--release", "11",
            "--add-modules", "ALL-MODULE-PATH",
            "--module-path", MODULE_PATH,
            "-classpath", CLASS_PATH
    );

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

        Map<String, byte[]> classes = compile(Map.of(SHARED_TEST_CLASS, SHARED_TEST_SOURCE));

        assertTrue(classes.containsKey(SHARED_TEST_CLASS));

        ByteMapCL classloader = new ByteMapCL(classes, this.getClass().getClassLoader());
        Class<?> cls = Class.forName(SHARED_TEST_CLASS, true, classloader);

        int val = (int) cls.getMethod("value").invoke(null);
        assertEquals(42, val);
    }

    @Test
    public void testCompileLayers() throws Exception {

        Map<String, byte[]> parentClasses = compile(Map.of(SHARED_TEST_CLASS, SHARED_TEST_SOURCE));
        byte[] existing = parentClasses.get(SHARED_TEST_CLASS);
        assertNotNull(existing, "Shared class not compiled");

        String code = "package foo;\n"
                + "import SHARED.Test;\n"
                + "public class Bar {\n"
                + "  public static int value() {\n"
                + "    return Test.value();\n"
                + "  }\n"
                + "}\n";

        Map<String, byte[]> classes = compile(Map.of("foo.Bar", code), parentClasses);

        assertTrue(classes.containsKey("foo.Bar"), "Class foo.Bar not found");
        assertEquals(1, classes.size(), "Classes contains more classes than expected");

        ByteMapCL parentClassloader = new ByteMapCL(parentClasses, this.getClass().getClassLoader());
        ByteMapCL classloader = new ByteMapCL(classes, parentClassloader);

        Class<?> cls = Class.forName("foo.Bar", true, classloader);
        int val = (int) cls.getMethod("value").invoke(null);
        assertEquals(42, val);

    }

    @Test
    public void testCompileLayeredClassBody() throws Exception {

        Map<String, byte[]> parentClasses = compile(Map.of(SHARED_TEST_CLASS, SHARED_TEST_SOURCE));
        byte[] existing = parentClasses.get(SHARED_TEST_CLASS);
        assertNotNull(existing, "Shared class not compiled");

        String code = " \timport SHARED.Test;// comment\n"
                + "public static int value() {\n"
                + "  return Test.value();\n"
                + "}\n";

        String wrapped = ClassBodyWrapper.create()
                .className("foo.Bar")
                .wrap(code);

        Map<String, byte[]> classes = compile(Map.of("foo.Bar", wrapped),
                parentClasses);

        assertTrue(classes.containsKey("foo.Bar"), "Class foo.Bar not found");
        assertEquals(1, classes.size(), "Classes contains more classes than expected");

        ByteMapCL parentClassloader = new ByteMapCL(parentClasses, this.getClass().getClassLoader());
        ByteMapCL classloader = new ByteMapCL(classes, parentClassloader);

        Class<?> cls = Class.forName("foo.Bar", true, classloader);
        int val = (int) cls.getMethod("value").invoke(null);
        assertEquals(42, val);

    }

    @Test
    public void testCompileDelegate() throws Exception {

        String code = "import java.net.URI;\n"
                + "public URI test(URI value) {\n"
                + "  return value;\n"
                + "}\n";

        String wrapped = ClassBodyWrapper.create()
                .className("foo.BarDelegate")
                .extendsType(DefaultCodeDelegate.class)
                .wrap(code);

        Map<String, byte[]> classes = compile(Map.of("foo.BarDelegate", wrapped));

        assertTrue(classes.containsKey("foo.BarDelegate"),
                "Class foo.BarDelegate not found");
        assertEquals(1, classes.size(),
                "Classes contains more classes than expected");

        ByteMapCL classloader = new ByteMapCL(classes, this.getClass().getClassLoader());

        Class<?> cls = Class.forName("foo.BarDelegate", true, classloader);
        assertTrue(DefaultCodeDelegate.class.isAssignableFrom(cls),
                "Class does not extend DefaultCodeDelegate");

        CodeDelegate instance = (CodeDelegate) cls.getConstructor().newInstance();
        int[] values = new int[]{1, 2, 3, 4, 5};
        Method maxIntArray = cls.getMethod("max", int[].class);
        int value = (int) maxIntArray.invoke(instance, values);
        assertEquals(5, value);
    }

    @Test
    public void testCompileLayeredDelegate() throws Exception {

        String baseCode = "package SHARED;\n"
                + "import org.praxislive.code.*;\n"
                + "public class BaseDelegate extends CodeDelegate implements DefaultDelegateAPI {\n"
                + "  public int test(int[] values) {\n"
                + "    return max(values) * 5;\n"
                + "  }\n"
                + "}\n";
        String baseType = "SHARED.BaseDelegate";

        Map<String, byte[]> baseClasses = compile(
                Map.of(baseType, baseCode));
        assertNotNull(baseClasses.get(baseType), "Base type not compiled");

        String code = " extends " + baseType + ";\n"
                + " import java.net.URI;\n"
                + "public URI test(URI value) {\n"
                + "  return value;\n"
                + "}\n";

        String wrapped = ClassBodyWrapper.create()
                .className("foo.BarDelegate")
                .extendsType(DefaultCodeDelegate.class)
                .wrap(code);

        Map<String, byte[]> classes = compile(
                Map.of("foo.BarDelegate", wrapped),
                baseClasses);

        assertTrue(classes.containsKey("foo.BarDelegate"),
                "Class foo.BarDelegate not found");
        assertEquals(1, classes.size(),
                "Classes contains more classes than expected");

        ByteMapCL baseClassLoader = new ByteMapCL(baseClasses, this.getClass().getClassLoader());
        ByteMapCL classloader = new ByteMapCL(classes, baseClassLoader);

        Class<?> cls = Class.forName("foo.BarDelegate", true, classloader);
        assertTrue(CodeDelegate.class.isAssignableFrom(cls),
                "Class does not extend CodeDelegate");
        assertEquals(baseType, cls.getSuperclass().getCanonicalName());

        CodeDelegate instance = (CodeDelegate) cls.getConstructor().newInstance();
        int[] values = new int[]{1, 2, 3, 4, 5};
        Method testMethod = cls.getMethod("test", int[].class);
        int value = (int) testMethod.invoke(instance, values);
        assertEquals(25, value);
    }

    private static Map<String, byte[]> compile(Map<String, String> sources)
            throws Exception {
        return compile(sources, null);
    }

    private static Map<String, byte[]> compile(Map<String, String> sources,
            Map<String, byte[]> existingClasses) throws Exception {
        assumeFalse(ToolProvider.getSystemJavaCompiler() == null, "No compiler available");
        CompilerTask task = CompilerTask.create(sources);
        task.options(DEFAULT_COMPILER_OPTIONS);
        if (existingClasses != null) {
            Map<String, Supplier<InputStream>> clss
                    = existingClasses.entrySet().stream()
                            .map(e -> Map.entry(e.getKey(),
                            (Supplier<InputStream>) () -> new ByteArrayInputStream(e.getValue())))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            task.existingClasses(clss);
        }
        return task.compile();
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
