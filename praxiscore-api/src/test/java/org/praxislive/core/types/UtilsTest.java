package org.praxislive.core.types;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void testEscape() {
        String in = "This is a {test}";
        String out = "{" + in + "}";
        assertEquals(out, Utils.escape(in));

        in = "void init() {\nSystem.out.println(\"\\{\");\n}";
        out = "{" + in + "}";
        assertEquals(out, Utils.escape(in));

        in = "This is {} unbalanced {at this time";
        out = "\"This is \\{\\} unbalanced \\{at this time\"";
        assertEquals(out, Utils.escape(in));

        in = "This is {}} balanced {but goes under level 0";
        out = "\"This is \\{\\}\\} balanced \\{but goes under level 0\"";
        assertEquals(out, Utils.escape(in));

        in = "Quote this single line string";
        out = "\"Quote this single line string\"";
        assertEquals(out, Utils.escape(in));

        in = "Brace this\nmultiline\nstring";
        out = "{" + in + "}";
        assertEquals(out, Utils.escape(in));

    }
    
}
