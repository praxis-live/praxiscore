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
package org.praxislive.core.syntax;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.praxislive.core.syntax.Token.Type.*;

/**
 *
 *
 */
public class TokenizerTest {

    private static final String TEXT
            = "#comment\nthis \"is\"; [a] {{test\\}}} in\\; for you; now";

    private static final String INVALID_TEXT = "#comment\n{unfinished";

    private static final List<Token.Type> TOKEN_TYPES = List.of(
            COMMENT, EOL,
            PLAIN, QUOTED, EOL,
            SUBCOMMAND, BRACED, PLAIN, PLAIN, PLAIN, EOL,
            PLAIN, EOL
    );

    private static final List<String> TOKEN_TEXT = List.of(
            "comment", "",
            "this", "is", "",
            "a", "{test\\}}", "in;", "for", "you", "",
            "now", ""
    );

    public TokenizerTest() {
    }

    /**
     * Test of iterator method, of class Tokenizer.
     */
    @Test
    public void testIterator() {
        List<Token> tokens = new ArrayList<>();
        for (Token token : new Tokenizer(TEXT)) {
            tokens.add(token);
        }
        validateTokens(tokens);
    }

    @Test
    public void testParse() {
        validateTokens(Tokenizer.parse(TEXT));
        assertThrows(InvalidSyntaxException.class,
                () -> Tokenizer.parse(INVALID_TEXT));
    }

    private void validateTokens(List<Token> tokens) {
        assertEquals(13, tokens.size());
        for (int i = 0; i < 13; i++) {
            assertEquals(TOKEN_TYPES.get(i), tokens.get(i).getType());
            assertEquals(TOKEN_TEXT.get(i), tokens.get(i).getText());
        }
    }

}
