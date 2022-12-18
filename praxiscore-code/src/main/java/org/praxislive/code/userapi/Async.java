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
package org.praxislive.code.userapi;

import java.util.Objects;
import org.praxislive.core.types.PError;

/**
 * A lightweight holder for a future value, the result of an asynchronous
 * operation such as an actor call. An Async can also reflect the failure of
 * such an operation.<p>
 * An Async can be explicitly completed, with a value or error. Completion can
 * only happen once.
 * <p>
 * <b>Async is not thread safe and is not designed for concurrent operation.</b>
 * Use from a single thread, or protect appropriately.
 *
 * @param <T> result type
 */
public final class Async<T> {

    private T result;
    private PError error;

    /**
     * Construct an empty Async.
     */
    public Async() {

    }

    /**
     * Get the result of this Async if completed without error, otherwise null.
     *
     * @return result or null
     */
    public T result() {
        return result;
    }

    /**
     * Get the failure error or null.
     *
     * @return error or null
     */
    public PError error() {
        return error;
    }

    /**
     * Whether this Async has been completed, with or without error.
     *
     * @return true if done
     */
    public boolean done() {
        return !(result == null && error == null);
    }

    /**
     * Whether this Async completed with a failure.
     *
     * @return true if failed
     */
    public boolean failed() {
        return error != null;
    }

    /**
     * Complete the Async with the provided value. If this Async is already
     * completed, with or without failure, its state remains the same and this
     * method returns false.
     *
     * @param value value to complete the Async
     * @return true if completed
     */
    public boolean complete(T value) {
        if (done()) {
            return false;
        } else {
            result = Objects.requireNonNull(value);
            return true;
        }
    }

    /**
     * Complete the Async unsuccessfully with the provided error. If this Async
     * is already completed, with or without failure, its state remains the same
     * and this method returns false.
     *
     * @param error error to complete the Async
     * @return true if completed
     */
    public boolean fail(PError error) {
        if (done()) {
            return false;
        } else {
            this.error = Objects.requireNonNull(error);
            return true;
        }
    }

    /**
     * A task intended to be run asynchronously and outside of the main
     * component context. All data required to complete the task should be
     * passed in as input data, and implementations should be careful not to use
     * any other data from the component during execution.
     *
     * @param <T> type of the input data
     * @param <R> type of the result
     */
    @FunctionalInterface
    public static interface Task<T, R> {

        /**
         * Execute the task. This will run outside of the main component
         * context. All data required to complete the task should be passed in
         * as input. Implementations should not access (or capture) any other
         * data from the component during execution.
         *
         * @param input input data for task
         * @return result
         * @throws Exception on task error
         */
        public R execute(T input) throws Exception;

    }

}
