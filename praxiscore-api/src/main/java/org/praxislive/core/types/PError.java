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
package org.praxislive.core.types;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.core.Value;
import org.praxislive.core.ValueFormatException;

/**
 * An error message, possibly wrapping a Java Exception.
 */
public final class PError extends Value {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "Error";

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_STACK_TRACE = "stack-trace";

    private final String type;
    private final String message;
    private final Exception ex;

    private volatile String stack;
    private volatile PMap data;

    private PError(String type, String message, Exception ex) {
        this(type, message, ex, null, null);
    }

    private PError(String type, String message, Exception ex, String stack, PMap data) {
        this.type = type;
        this.message = message;
        this.ex = ex;
        this.stack = stack;
        this.data = data;
    }

    @Deprecated
    public Class<? extends Exception> exceptionType() {
        return ex == null ? Exception.class : ex.getClass();
    }

    /**
     * The error type, usually the simple name of a Java Exception.
     *
     * @return error type
     */
    public String errorType() {
        return type;
    }

    /**
     * The error message.
     *
     * @return error message
     */
    public String message() {
        return message;
    }

    /**
     * A short form of the stack trace leading to this error, if available.
     * Otherwise an empty String.
     *
     * @return stack trace or empty String.
     */
    public String stackTrace() {
        if (stack == null) {
            if (ex != null) {
                stack = Stream.of(ex.getStackTrace())
                        .skip(1)
                        .limit(5)
                        .map(e -> "    " + e.toString())
                        .collect(Collectors.joining("\n"));
            } else {
                stack = "";
            }
        }
        return stack;
    }

    /**
     * The wrapped exception, if available. Direct access to the exception is
     * only available in the process in which the error was created.
     *
     * @return optional exception
     */
    public Optional<Exception> exception() {
        return Optional.ofNullable(ex);
    }

    @Override
    public String toString() {
        return dataMap().toString();
    }

    /**
     * The error as a {@link PMap}. This is similar to
     * {@link PMap.MapBasedValue} except that the map representation of a PError
     * is created lazily. A PError recreated from its PMap representation will
     * not have an Exception reference.
     *
     * @return PError data as a PMap
     */
    public PMap dataMap() {
        if (data == null) {
            data = PMap.of(
                    KEY_ERROR, errorType(),
                    KEY_MESSAGE, message(),
                    KEY_STACK_TRACE, stackTrace()
            );
        }
        return data;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.type);
        hash = 67 * hash + Objects.hashCode(this.message);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof PError) {
            final PError other = (PError) obj;
            return Objects.equals(this.type, other.type)
                    && Objects.equals(this.message, other.message);
        }
        return false;
    }

    /**
     * Parse a String as a PError. This first parses the text as a PMap.
     *
     * @param string text representation
     * @return PError instance
     * @throws ValueFormatException if the text cannot be parsed
     */
    public static PError parse(String string) throws ValueFormatException {
        return fromMap(PMap.parse(string));
    }

    /**
     * Cast or convert the provided value into a PError, wrapped in an Optional.
     * If the value is already a PError, the Optional will wrap the existing
     * value. If the value is not a PError and cannot be converted into one, an
     * empty Optional is returned.
     *
     * @param value value
     * @return optional PError
     */
    public static Optional<PError> from(Value value) {
        try {
            return Optional.of(coerce(value));
        } catch (ValueFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * Create a PError wrapping the given Exception. The message will be taken
     * from the exception, and the error type will be the simple name of the
     * exception's class.
     *
     * @param ex exception
     * @return PError instance
     */
    public static PError of(Exception ex) {
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        if (msg == null) {
            msg = "";
        }
        return new PError(type, msg, ex);
    }

    /**
     * Create a PError wrapping the given Exception, with a custom message. The
     * exception's message will be ignored. The error type will be the simple
     * name of the exception's class.
     *
     * @param ex exception
     * @param msg message
     * @return PError instance
     */
    public static PError of(Exception ex, String msg) {
        return new PError(ex.getClass().getSimpleName(),
                Objects.requireNonNull(msg),
                ex);
    }

    /**
     * Create a PError of the given message. The error type will be
     * {@code Exception}.
     *
     * @param msg message
     * @return PError instance
     */
    public static PError of(String msg) {
        return of(Exception.class, msg);
    }

    /**
     * Create a PError of the given type and message. The error type will be the
     * simple name of the passed in Exception type.
     *
     * @param type error type
     * @param msg message
     * @return PError instance
     */
    public static PError of(Class<? extends Exception> type, String msg) {
        return new PError(type.getSimpleName(),
                Objects.requireNonNull(msg),
                null);
    }

    private static PError coerce(Value arg) throws ValueFormatException {
        if (arg instanceof PError err) {
            return err;
        } else if (arg instanceof PReference ref) {
            return PError.of(ref.as(Exception.class)
                    .orElseThrow(ValueFormatException::new));
        }
        return fromMap(PMap.from(arg).orElseThrow(ValueFormatException::new));
    }

    private static PError fromMap(PMap data) throws ValueFormatException {
        Value type = data.get(KEY_ERROR);
        Value message = data.get(KEY_MESSAGE);
        if (type == null || message == null) {
            throw new ValueFormatException();
        }
        Value stack = data.get(KEY_STACK_TRACE);
        return new PError(type.toString(), message.toString(), null,
                stack == null ? "" : stack.toString(), data);
    }

}
