/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
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

import org.praxislive.core.Value;

/**
 *
 */
class Utils {
    
    private static final String REQUIRE_QUOTING = "{}[];'\"\\";
    private static final String REQUIRE_QUOTING_START = ".#" + REQUIRE_QUOTING;

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

    static String escapeQuoted(String input) {
        String res = doQuoted(input);
        return res;
    }

    static boolean equivalent(Value arg1, Value arg2) {
        return arg1.equivalent(arg2) || arg2.equivalent(arg1);
    }
    
    static String doPlain(String input) {
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
    
    static String doBraced(String input) {
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
                case '}':
                    shouldBrace = true;
                    if (idx > 0 && input.charAt(idx - 1) == '\\') {
                        // escaped
                    } else {
                        level--;
                    }
                    break;
                case '{':
                    shouldBrace = true;
                    if (idx > 0 && input.charAt(idx - 1) == '\\') {
                        // escaped
                    } else {
                        level++;
                    }
                    break;
                case '[':
                case ']':
                case '\n':
                case '\r':
                    shouldBrace = true;
                    break;
                default:
                    break;
            }
        }
        if (shouldBrace && idx == len && level == 0) {
            return "{" + input + "}";
        } else {
            return null;
        }
    }

    static String doQuoted(String input) {
        int len = input.length();
        if (len == 0) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(len * 2);
        sb.append("\"");
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            switch (c) {
                case '{':
                case '}':
                case '[':
                case ']':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }


}
