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
package org.praxislive.project;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PortAddress;
import org.praxislive.core.Value;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class SyntaxUtilsTest {

    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");

    public SyntaxUtilsTest() {
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("START TEST : " + info.getDisplayName());
        }
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("END TEST : " + info.getDisplayName());
            System.out.println("=====================================");
        }
    }

    @Test
    public void testEscape() {
        assertEquals("\"\"", SyntaxUtils.escape(""));
        assertEquals("1234", SyntaxUtils.escape("1234"));
        assertEquals("\"te\\\"st\"", SyntaxUtils.escape("te\"st"));
        assertEquals("te#st", SyntaxUtils.escape("te#st"));
        assertEquals("\"#test\"", SyntaxUtils.escape("#test"));
        assertEquals("\".test\"", SyntaxUtils.escape(".test"));
        assertEquals("\"1234\"", SyntaxUtils.escapeQuoted("1234"));
    }

    @Test
    public void testIsSafeBraced() {
        assertTrue(SyntaxUtils.isSafeBraced(""));
        assertTrue(SyntaxUtils.isSafeBraced("{\n  {public void foo() {}}\n}"));
        assertTrue(SyntaxUtils.isSafeBraced("{\n  \\{uneven}"));
        assertFalse(SyntaxUtils.isSafeBraced("{"));
        assertFalse(SyntaxUtils.isSafeBraced("this is } not OK"));
    }

    @Test
    public void testValueFromToken() {
        String tokenText = "[array [map key true] 1 -2.34 /component /component.control /component!port \"42\" ]";
        List<Token> tokens = Tokenizer.parse(tokenText);
        if (VERBOSE) {
            System.out.println(tokens);
        }
        Value value = SyntaxUtils.valueFromToken(tokens.get(0));
        assertInstanceOf(PArray.class, value);
        PArray array = (PArray) value;
        assertEquals(7, array.size());
        assertInstanceOf(PMap.class, array.get(0));
        PMap map = (PMap) array.get(0);
        assertEquals(1, map.size());
        assertInstanceOf(PBoolean.class, map.get("key"));
        assertTrue(map.getBoolean("key", false));
        assertInstanceOf(PNumber.class, array.get(1));
        assertInstanceOf(PNumber.class, array.get(2));
        assertInstanceOf(ComponentAddress.class, array.get(3));
        assertInstanceOf(ControlAddress.class, array.get(4));
        assertInstanceOf(PortAddress.class, array.get(5));
        assertInstanceOf(PString.class, array.get(6));

        tokenText = "[array 1\nmap key value]";
        tokens = Tokenizer.parse(tokenText);
        Token multi = tokens.get(0);
        assertThrows(IllegalArgumentException.class, () -> SyntaxUtils.valueFromToken(multi));

        tokenText = "[file \"test.txt\"]";
        tokens = Tokenizer.parse(tokenText);
        Token file = tokens.get(0);
        assertThrows(IllegalArgumentException.class, () -> SyntaxUtils.valueFromToken(file));
    }

    @Test
    public void testValueFromTokenWithContext() {
        String tokenText = "[file \"test file.txt\"]";
        List<Token> tokens = Tokenizer.parse(tokenText);
        Token file = tokens.get(0);
        URI parent = URI.create("file:/parent/");
        URI expected = URI.create("file:/parent/test%20file.txt");
        Value value = SyntaxUtils.valueFromToken(parent, file);
        assertInstanceOf(PResource.class, value);
        assertEquals(expected, ((PResource) value).value());
    }

    @Test
    public void testValueToToken() {
        assertEquals("true", SyntaxUtils.valueToToken(PBoolean.TRUE));
        assertEquals("42", SyntaxUtils.valueToToken(PNumber.of(42)));
        assertEquals("\"Hello World\"", SyntaxUtils.valueToToken(PString.of("Hello World")));
        PMap map = PMap.of("key", PArray.of(PNumber.of(1), PNumber.of(2)),
                "another key", PResource.of(URI.create("file:/parent/test%20file.txt")));
        assertEquals("[map key [array 1 2] \"another key\" file:/parent/test%20file.txt]",
                SyntaxUtils.valueToToken(map));
        String method = """
                        @T(1) public void foo() {
                          if ("".equals(value[0])) {
                            out.send();
                          }
                        }
                        """;
        assertEquals("{" + method + "}", SyntaxUtils.valueToToken(PString.of(method)));
        String notBraceable = """
                              @T(1) public void foo() {
                                if ("".equals(value[0])) {
                                  out.send();
                                }""";
        String expected = """
                          "@T(1) public void foo() \\{
                            if (\\"\\".equals(value\\[0\\])) \\{
                              out.send();
                            \\}\"""";
        assertEquals(expected, SyntaxUtils.valueToToken(PString.of(notBraceable)));
    }

    @Test
    public void testValueToTokenWithContext() {
        URI parent = URI.create("file:/parent/");
        URI file1 = URI.create("file:/parent/test%20file.txt");
        URI file2 = URI.create("file:/parent2/test%20file.txt");
        PArray array = PArray.of(PResource.of(file1), PResource.of(file2));
        assertEquals("[array [file \"test file.txt\"] file:/parent2/test%20file.txt]",
                SyntaxUtils.valueToToken(parent, array));
        assertEquals("[file \"test file.txt\"]", SyntaxUtils.valueToToken(parent, PString.of(file1)));

    }

}
