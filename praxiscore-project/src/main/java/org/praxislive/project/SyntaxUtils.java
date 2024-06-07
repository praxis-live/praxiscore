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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PortAddress;
import org.praxislive.core.Value;
import org.praxislive.core.ValueFormatException;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;

import static org.praxislive.core.syntax.Token.Type.COMMENT;
import static org.praxislive.core.syntax.Token.Type.EOL;
import static org.praxislive.core.syntax.Token.Type.PLAIN;
import static org.praxislive.core.syntax.Token.Type.SUBCOMMAND;

/**
 * Various utility functions for parsing and writing values.
 */
public class SyntaxUtils {

    private static final String DIGITS = "0123456789";
    private static final String REQUIRE_QUOTING = "{}[];'\"\\";
    private static final String REQUIRE_QUOTING_START = ".#$" + REQUIRE_QUOTING;
    private static final int MAX_LENGTH_PLAIN = 128;

    private SyntaxUtils() {
    }

    /**
     * Escape the provided input for writing to a script, such that it can be
     * parsed as a single value. The value might be wrapped in quotation marks
     * if necessary.
     *
     * @param input text input
     * @return escaped text
     */
    public static String escape(String input) {
        String res = doPlain(input);
        if (res == null) {
            res = doQuoted(input);
        }
        return res;
    }

    /**
     * Escape the provided input for writing to a script, such that it can be
     * parsed as a single value. The value will be wrapped in quotation marks.
     *
     * @param input text input
     * @return escaped text
     */
    public static String escapeQuoted(String input) {
        return doQuoted(input);
    }

    /**
     * Validate whether the provided input can be safely written as-is between
     * braces without needing to be quote escaped. The check iterates through
     * the input checking that any braces in the input text are correctly
     * matched.
     *
     * @param input text input
     * @return true if safe to write input in braces without escaping
     */
    public static boolean isSafeBraced(String input) {
        int len = input.length();
        if (len == 0) {
            return true;
        }
        int level = 0;
        int idx = 0;
        for (; idx < len && level > -1; idx++) {
            char ch = input.charAt(idx);
            switch (ch) {
                case '}' -> {
                    if (idx > 0 && input.charAt(idx - 1) == '\\') {
                        // escaped
                    } else {
                        level--;
                    }
                }
                case '{' -> {
                    if (idx > 0 && input.charAt(idx - 1) == '\\') {
                        // escaped
                    } else {
                        level++;
                    }
                }
            }
        }
        return idx == len && level == 0;
    }

    /**
     * Extract a value from the provided token. Will attempt to parse plain
     * tokens to the correct value type. Array and map subcommands will be
     * parsed to the correct value types, including when nested.
     *
     * @param token script token
     * @return value
     */
    public static Value valueFromToken(Token token) {
        return valueFromTokenImpl(null, token);
    }

    /**
     * Extract a value from the provided token. Will attempt to parse plain
     * tokens to the correct value type. Array and map subcommands will be
     * parsed to the correct value types, including when nested. File
     * subcommands will be resolved based on the provided context.
     *
     * @param context resource context
     * @param token script token
     * @return value
     */
    public static Value valueFromToken(URI context, Token token) {
        return valueFromTokenImpl(Objects.requireNonNull(context), token);
    }

    /**
     * Return the provided value as suitable token text to be included in a
     * script. Equivalent to passing a StringBuilder to
     * {@link #writeValue(org.praxislive.core.Value, java.lang.Appendable)} and
     * returning the result.
     *
     * @param value value to write
     * @return value as script
     */
    public static String valueToToken(Value value) {
        StringBuilder sb = new StringBuilder();
        try {
            writeValue(value, sb);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return sb.toString();
    }

    /**
     * Return the provided value as suitable token text to be included in a
     * script. Equivalent to passing a StringBuilder to
     * {@link #writeValue(java.net.URI, org.praxislive.core.Value, java.lang.Appendable)}
     * and returning the result. Resources will be relativized, if possible, to
     * the provided context.
     *
     * @param context resource context
     * @param value value to write
     * @return value as script
     */
    public static String valueToToken(URI context, Value value) {
        StringBuilder sb = new StringBuilder();
        try {
            writeValue(context, value, sb);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return sb.toString();
    }

    /**
     * Write the provided value as suitable token text to the provided output.
     * Values will be quoted as necessary, and map and array values will be
     * converted to the equivalent subcommands.
     *
     * @param value value to write
     * @param out target to write the value to
     * @throws java.io.IOException if write fails
     */
    public static void writeValue(Value value, Appendable out) throws IOException {
        writeValueImpl(null,
                Objects.requireNonNull(value),
                Objects.requireNonNull(out));
    }

    /**
     * Write the provided value as suitable token text to the provided output.
     * Values will be quoted as necessary, and map and array values will be
     * converted to the equivalent subcommands. Resources will be relativized,
     * if possible, to the provided context using the file subcommand.
     *
     * @param context resource context
     * @param value value to write
     * @param out target to write the value to
     * @throws java.io.IOException if write fails
     */
    public static void writeValue(URI context, Value value, Appendable out) throws IOException {
        writeValueImpl(Objects.requireNonNull(context),
                Objects.requireNonNull(value),
                Objects.requireNonNull(out));
    }

    static String unescapeCommentText(String text) {
        if (!text.contains("\\")) {
            return text;
        }
        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                c = text.charAt(i);
                switch (c) {
                    case 'n':
                        sb.append('\n');
                        continue;
                    case 't':
                        sb.append('\t');
                        continue;
                    case 'r':
                        continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String escapeCommentText(String text) {
        int len = text.length();
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '{':
                case '}':
                case '[':
                case ']':
                case '\"':
                case '\\':
                    sb.append('\\').append(c);
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\r':
                    break;
                default:
                    sb.append(c);
            }
        }

        // just in case, make sure newline isn't escaped
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\\') {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String doPlain(String input) {
        int len = input.length();
        if (len == 0 || len > MAX_LENGTH_PLAIN) {
            return null;
        }
        char c = input.charAt(0);
        if (Character.isWhitespace(c) || REQUIRE_QUOTING_START.indexOf(c) > -1) {
            return null;
        }
        for (int i = 1; i < len; i++) {
            c = input.charAt(i);
            if (Character.isWhitespace(c) || REQUIRE_QUOTING.indexOf(c) > -1) {
                return null;
            }
        }
        return input;
    }

    private static String doQuoted(String input) {
        int len = input.length();
        if (len == 0) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(len * 2);
        sb.append("\"");
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            switch (c) {
                case '{', '}', '[', ']' ->
                    sb.append('\\').append(c);
                case '\"' ->
                    sb.append("\\\"");
                case '\\' ->
                    sb.append("\\\\");
                default ->
                    sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static Value valueFromTokenImpl(URI context, Token token) {
        Objects.requireNonNull(token);
        return switch (token.getType()) {
            case PLAIN ->
                valueFromPlainToken(token.getText());
            case QUOTED, BRACED ->
                PString.of(token.getText());
            case SUBCOMMAND ->
                valueFromSubcommand(context, token.getText());
            default ->
                throw new IllegalArgumentException("Invalid token type : " + token);
        };
    }

    private static Value valueFromPlainToken(String text) {
        if (!isSafePlainToken(text)) {
            throw new IllegalArgumentException("Unsupported plain token");
        }
        if ("true".equals(text)) {
            return PBoolean.TRUE;
        }
        if ("false".equals(text)) {
            return PBoolean.FALSE;
        }
        int length = text.length();
        if (length > 0) {
            char c = text.charAt(0);
            if (DIGITS.indexOf(c) > -1
                    || (c == '-' && length > 1 && DIGITS.indexOf(text.charAt(1)) > -1)) {
                return numberOrString(text);
            }
            if (c == '/' && length > 1) {
                return addressOrString(text);
            }
        }
        return PString.of(text);
    }

    private static Value numberOrString(String text) {
        try {
            return PNumber.parse(text);
        } catch (Exception ex) {
            return PString.of(text);
        }
    }

    private static Value addressOrString(String text) {
        try {
            if (text.lastIndexOf('.') > -1) {
                return ControlAddress.parse(text);
            } else if (text.lastIndexOf('!') > -1) {
                return PortAddress.parse(text);
            } else {
                return ComponentAddress.parse(text);
            }
        } catch (Exception ex) {
            return PString.of(text);
        }
    }

    private static Value valueFromSubcommand(URI context, String command) {
        List<Token> tokens = subcommandTokens(command);
        Token token = tokens.get(0);
        if (tokens.get(0).getType() != PLAIN) {
            throw new IllegalArgumentException("First token is not a plain command : " + command);
        }
        return switch (token.getText()) {
            case "array" ->
                arrayFromCommand(context, tokens);
            case "map" ->
                mapFromCommand(context, tokens);
            case "file" ->
                fileFromCommand(context, tokens);
            default ->
                throw new IllegalArgumentException("Unsupported subcommand : " + token.getText());
        };
    }

    private static List<Token> subcommandTokens(String command) {
        Iterator<Token> tokens = new Tokenizer(command).iterator();
        List<Token> result = new ArrayList<>();
        while (tokens.hasNext()) {
            Token t = tokens.next();
            if (t.getType() == COMMENT) {
                continue;
            }
            if (t.getType() == EOL) {
                break;
            }
            result.add(t);
        }
        if (tokens.hasNext()) {
            throw new IllegalArgumentException("More than one command found in subcommand token : " + command);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Empty subcommand");
        }
        return result;
    }

    private static PArray arrayFromCommand(URI context, List<Token> tokens) {
        if (tokens.size() == 1) {
            return PArray.EMPTY;
        }
        List<Value> values = tokens.stream().skip(1)
                .map(t -> valueFromTokenImpl(context, t))
                .toList();
        return PArray.of(values);
    }

    private static PMap mapFromCommand(URI context, List<Token> tokens) {
        if (tokens.size() == 1) {
            return PMap.EMPTY;
        }
        int size = tokens.size();
        if (size % 2 != 1) {
            // first value is `map`
            throw new IllegalArgumentException("Map requires an even number of arguments");
        }
        PMap.Builder builder = PMap.builder();
        for (int i = 1; i < size; i += 2) {
            Token keyToken = tokens.get(i);
            if (keyToken.getType() == SUBCOMMAND || keyToken.getType() == EOL
                    || keyToken.getType() == COMMENT) {
                throw new IllegalArgumentException("Invalid key token type : " + keyToken);
            }
            String key = keyToken.getText();
            if (keyToken.getType() == PLAIN && !isSafePlainToken(key)) {
                throw new IllegalArgumentException("Invalid plain key : " + key);
            }
            builder.put(key, valueFromTokenImpl(context, tokens.get(i + 1)));
        }
        return builder.build();
    }

    private static PResource fileFromCommand(URI context, List<Token> tokens) {
        if (context == null) {
            throw new IllegalArgumentException("Relative files cannot be parsed without working directory");
        }
        if (tokens.size() != 2) {
            throw new IllegalArgumentException("Invalid number of arguments for file subcommand");
        }
        Token pathToken = tokens.get(1);
        if (pathToken.getType() == SUBCOMMAND || pathToken.getType() == EOL
                || pathToken.getType() == COMMENT) {
            throw new IllegalArgumentException("Invalid path token type : " + pathToken);
        }
        String path = pathToken.getText();
        if (pathToken.getType() == PLAIN && !isSafePlainToken(path)) {
            throw new IllegalArgumentException("Invalid plain path : " + path);
        }
        try {
            if (path.contains(":")) {
                try {
                    URI uri = new URI(path);
                    if (uri.isAbsolute()) {
                        return PResource.of(uri);
                    }
                } catch (URISyntaxException ex) {
                    // fall through?
                }
            }
            URI uri = context.resolve(new URI(null, null, path, null));
            return PResource.of(uri);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static boolean isSafePlainToken(String text) {
        return !(text.startsWith("$") || text.startsWith("."));
    }

    private static void writeValueImpl(URI context, Value value, Appendable out) throws IOException {
        switch (value) {
            case PMap map ->
                writePMap(context, map, out);
            case PArray array ->
                writePArray(context, array, out);
            case PResource resource ->
                writePResource(context, resource, out);
            case PString string ->
                writePString(context, string, out);
            default ->
                out.append(escape(value.toString()));
        }
    }

    private static void writePMap(URI context, PMap map, Appendable out) throws IOException {
        if (map.isEmpty()) {
            out.append("[map]");
            return;
        }
        out.append("[map");
        for (var entry : map.asMap().entrySet()) {
            out.append(" ")
                    .append(escape(entry.getKey()))
                    .append(" ");
            writeValueImpl(context, entry.getValue(), out);
        }
        out.append("]");
    }

    private static void writePArray(URI context, PArray array, Appendable out) throws IOException {
        if (array.isEmpty()) {
            out.append("[array]");
            return;
        }
        out.append("[array");
        for (Value value : array) {
            out.append(" ");
            writeValueImpl(context, value, out);
        }
        out.append("]");
    }

    private static void writePResource(URI context, PResource resource, Appendable out) throws IOException {
        if (context != null) {
            URI res = context.relativize(resource.value());
            if (!res.isAbsolute()) {
                out.append("[file ")
                        .append(escapeQuoted(res.getPath()))
                        .append("]");
                return;
            }
        }
        out.append(escape(resource.toString()));
    }

    private static void writePString(URI context, PString string, Appendable out) throws IOException {
        String text = string.toString();
        int lines = (int) text.lines().limit(3).count();
        if (lines > 2 && isSafeBraced(text)) {
            out.append("{").append(text).append("}");
            return;
        } else if (lines == 1 && text.contains(":/")) {
            try {
                PResource res = PResource.parse(text);
                writePResource(context, res, out);
                return;
            } catch (ValueFormatException ex) {
                // fall through
            }
        }
        out.append(escape(text));
    }

}
