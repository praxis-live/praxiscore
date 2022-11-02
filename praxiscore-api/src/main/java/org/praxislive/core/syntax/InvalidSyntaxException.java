/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2022 Neil C Smith.
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

/**
 * Exception thrown when text syntax is invalid and cannot be parsed.
 */
public class InvalidSyntaxException extends RuntimeException {

    /**
     * Creates a new instance of <code>InvalidSyntaxException</code> without
     * detail message.
     */
    public InvalidSyntaxException() {
    }

    /**
     * Constructs an instance of <code>InvalidSyntaxException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public InvalidSyntaxException(String msg) {
        super(msg);
    }

    /**
     * Constructs an instance of <code>InvalidSyntaxException</code> with the
     * specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause cause
     */
    public InvalidSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an instance of <code>InvalidSyntaxException</code> with the
     * specified detail cause.
     *
     * @param cause cause
     */
    public InvalidSyntaxException(Throwable cause) {
        super(cause);
    }

}
