package org.praxislive.core.syntax;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.praxislive.core.syntax.Token.Type.*;

/**
 *
 * 
 */
public class TokenizerTest {
    
    private static final String TEXT =
            "#comment\nthis \"is\"; [a] {{test\\}}} in\\; for you; now";
    
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

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
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