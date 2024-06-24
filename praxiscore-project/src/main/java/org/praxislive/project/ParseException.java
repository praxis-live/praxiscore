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

/**
 * Exception thrown when a script cannot be parsed into a graph or project
 * model.
 */
public class ParseException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance of <code>ParseException</code> without detail
     * message.
     */
    public ParseException() {
    }

    /**
     * Constructs an instance of <code>ParseException</code> with the specified
     * detail message.
     *
     * @param message the detail message
     */
    public ParseException(String message) {
        super(message);
    }

    /**
     * Constructs an instance of <code>ParseException</code> with the specified
     * detail message and cause.
     *
     * @param message the detail message
     * @param cause cause
     */
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an instance of <code>ParseException</code> with the specified
     * cause.
     *
     * @param cause cause
     */
    public ParseException(Throwable cause) {
        super(cause);
    }

}
