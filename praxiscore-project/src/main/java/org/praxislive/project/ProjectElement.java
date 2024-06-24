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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;

/**
 * Elements of a project.
 */
public sealed abstract class ProjectElement {

    /**
     * Create a file element wrapping the given file URI.
     *
     * @param file address of file
     * @return file element
     */
    public static File file(URI file) {
        if (!file.isAbsolute()) {
            throw new IllegalArgumentException("File URI must be absolute");
        }
        return new File(file);
    }

    /**
     * Create a file element wrapping the URI of the given file path.
     *
     * @param file path of file
     * @return file element
     */
    public static File file(Path file) {
        return file(file.toUri());
    }

    /**
     * Create a line element wrapping the given script line. The line must be a
     * single line of script with a plain first token.
     *
     * @param script line of script
     * @return line element
     * @throws IllegalArgumentException if the line fails to parse according to
     * the rules
     */
    public static Line line(String script) {
        Iterator<Token> itr = new Tokenizer(script).iterator();
        List<Token> tokens = new ArrayList<>();
        while (itr.hasNext()) {
            Token token = itr.next();
            if (tokens.isEmpty()) {
                if (token.getType() != Token.Type.PLAIN) {
                    throw new IllegalArgumentException("First token of a command must be plain");
                }
            }
            if (token.getType() == Token.Type.COMMENT) {
                throw new IllegalArgumentException("Invalid command - contains a comment");
            } else if (token.getType() == Token.Type.EOL) {
                break;
            }
            tokens.add(token);
        }
        if (itr.hasNext()) {
            throw new IllegalArgumentException("Invalid command - tokens found after EOL");
        }
        return new Line(script, List.copyOf(tokens));
    }

    /**
     * A file element wrapping a file to be included.
     */
    public static final class File extends ProjectElement {

        private final URI file;

        private File(URI file) {
            this.file = file;
        }

        /**
         * Address of the file as an absolute URI.
         *
         * @return file address
         */
        public URI file() {
            return file;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof File other
                    && Objects.equals(file, other.file);
        }

        @Override
        public int hashCode() {
            return file.hashCode();
        }

        @Override
        public String toString() {
            return "ProjectElement.File{" + "uri=" + file + "}";
        }

    }

    /**
     * A line element wrapping a single line of script.
     */
    public static final class Line extends ProjectElement {

        private final String script;
        private final List<Token> tokens;

        private Line(String script, List<Token> tokens) {
            this.script = script;
            this.tokens = tokens;
        }

        /**
         * The script line.
         *
         * @return script line
         */
        public String line() {
            return script;
        }

        /**
         * The tokens making up the line. The EOL token is not included.
         *
         * @return line tokens
         */
        public List<Token> tokens() {
            return tokens;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof Line other
                    && Objects.equals(script, other.script);
        }

        @Override
        public int hashCode() {
            return script.hashCode();
        }

        @Override
        public String toString() {
            return "ProjectElement.Line{" + "script=" + script + "}";
        }

    }

}
