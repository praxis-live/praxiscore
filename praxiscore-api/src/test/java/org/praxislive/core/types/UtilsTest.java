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
package org.praxislive.core.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
