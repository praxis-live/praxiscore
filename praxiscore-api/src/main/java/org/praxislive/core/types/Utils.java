/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.praxislive.core.Value;

/**
 *
 */
class Utils {

    private static final String REQUIRE_QUOTING = "{}[];'\"\\";
    private static final String REQUIRE_QUOTING_START = ".#$" + REQUIRE_QUOTING;

    private Utils() {
    }

    static String escape(String input) {
        String res = doPlain(input);
        if (res == null) {
            res = doBraced(input);
        }
        if (res == null) {
            res = doQuoted(input);
        }
        return res;
    }

    static boolean equivalent(Value arg1, Value arg2) {
        return arg1.equivalent(arg2) || arg2.equivalent(arg1);
    }

    static String print(PArray array, Value.PrintOption... options) {
        if (array.isEmpty()) {
            return "";
        }
        int maxInlineTokenSize = 10;
        int maxInlineLineLength = 80;

        int maxLength = 1;
        List<String> tokens = new ArrayList<>(array.size());
        for (Value value : array.asList()) {
            String token = printValue(value, options);
            if (maxLength != Integer.MAX_VALUE) {
                if (token.startsWith("{") || token.contains("\n")) {
                    maxLength = Integer.MAX_VALUE;
                } else {
                    maxLength = Math.max(maxLength, token.length());
                }
            }
            tokens.add(token);
        }
        if (maxLength < maxInlineTokenSize) {
            int tokensPerLine = maxInlineLineLength / (maxLength + 1);
            if (tokens.size() > tokensPerLine) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0, count = tokens.size(); i < count; i++) {
                    if (i != 0 && i % tokensPerLine == 0) {
                        sb.append("\n");
                    }
                    String token = tokens.get(i);
                    sb.append(token);
                    if (i < (count - 1)) {
                        sb.append(" ".repeat(1 + (maxLength - token.length())));
                    }
                }
                return sb.toString();
            } else {
                return tokens.stream().collect(Collectors.joining(" "));
            }
        } else {
            return tokens.stream().collect(Collectors.joining("\n"));
        }
    }

    static String print(PMap map, Value.PrintOption... options) {
        if (map.isEmpty()) {
            return "";
        }
        return map.asMap().entrySet().stream()
                .map(e -> escape(e.getKey()) + " " + printValue(e.getValue(), options))
                .collect(Collectors.joining("\n"));
    }

    static String checkStripIndent(String text) {
        if (text.isEmpty()) {
            return text;
        }
        if (Character.isWhitespace(text.charAt(0)) && text.contains("\n")) {
            int indent = text.lines()
                    .filter(s -> !s.isEmpty())
                    .mapToInt(Utils::initialWhitespace)
                    .min().orElse(0);
            if (indent > 0) {
                String stripped = text.lines()
                        .map(l -> indent <= l.length() ? l.substring(indent) : "")
                        .collect(Collectors.joining("\n", "", "\n"));
                return stripped;
            }
        }
        return text;
    }

    private static String doPlain(String input) {
        int len = input.length();
        if (len == 0 || len > 128) {
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

    private static String doBraced(String input) {
        int len = input.length();
        if (len == 0) {
            return null;
        }
        boolean shouldBrace = false;
        int level = 0;
        int idx = 0;
        for (; idx < len && level > -1; idx++) {
            char ch = input.charAt(idx);
            switch (ch) {
                case '}' -> {
                    shouldBrace = true;
                    if (idx > 0 && input.charAt(idx - 1) == '\\') {
                        // escaped
                    } else {
                        level--;
                    }
                }
                case '{' -> {
                    shouldBrace = true;
                    if (idx > 0 && input.charAt(idx - 1) == '\\') {
                        // escaped
                    } else {
                        level++;
                    }
                }
                case '[', ']', '\n', '\r' ->
                    shouldBrace = true;
            }
        }
        if (shouldBrace && idx == len && level == 0) {
            return "{" + input + "}";
        } else {
            return null;
        }
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
                case '{', '}', '[', ']' -> {
                    sb.append('\\');
                    sb.append(c);
                }
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

    private static String printValue(Value value, Value.PrintOption... options) {
        if (value instanceof PArray || value instanceof PMap
                || value instanceof PArray.ArrayBasedValue
                || value instanceof PMap.MapBasedValue) {
            return "{\n" + value.print(options).indent(2) + "}";
        } else {
            return escape(value.print(options));
        }
    }

    private static int initialWhitespace(String text) {
        int length = text.length();
        int index = 0;
        while (index < length && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

}
