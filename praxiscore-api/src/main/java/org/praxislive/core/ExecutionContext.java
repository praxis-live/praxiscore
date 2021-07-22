/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith.
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
package org.praxislive.core;

import java.util.OptionalLong;

/**
 * An optional context available from the {@link Root} lookup providing the
 * ability to query and listen for changes in root state or clock time.
 * <p>
 * A Root will be initialized with {@link State#NEW}, move to / between
 * {@link State#ACTIVE} and {@link State#IDLE}, then move to
 * {@link State#TERMINATED}. A Root will not return to the NEW state, or any
 * other state once terminated.
 * <p>
 * Time is the local Root time relative to {@link RootHub#getClock()}. Clock
 * listeners are called whenever the Root's local time changes - the frequency
 * of changes, and whether the frequency is fixed or variable, is Root
 * implementation specific.
 */
public interface ExecutionContext {

    /**
     * Possible states of a Root.
     */
    public static enum State {

        /**
         * Newly created.
         */
        NEW,
        /**
         * Actively running.
         */
        ACTIVE,
        /**
         * Idle (optional state). Clock listeners will not be fired when idle,
         * and other specific root behaviour (eg. display) may be switched off.
         */
        IDLE,
        /**
         * Terminated.
         */
        TERMINATED
    };

    /**
     * Add a listener for state changes.
     *
     * @param listener state listener, may not be null
     */
    public void addStateListener(StateListener listener);

    /**
     * Remove an existing state listener.
     *
     * @param listener state listener to remove
     */
    public void removeStateListener(StateListener listener);

    /**
     * Add a listener for clock changes. Listeners will be called every time the
     * Root is active and its local time changes - the frequency of changes, and
     * whether the frequency is fixed or variable, is Root implementation
     * specific.
     *
     * @param listener clock listener, may not be null
     */
    public void addClockListener(ClockListener listener);

    /**
     * Remove an existing clock listener.
     *
     * @param listener clock listener to remove
     */
    public void removeClockListener(ClockListener listener);

    /**
     * Get the current Root local time in nanoseconds, relative to the RootHub
     * clock.
     *
     * @return time in nanoseconds
     */
    public long getTime();

    /**
     * Get the clock time in nanoseconds when the Root last became active. If
     * the current state is not active, the return value is not valid.
     *
     * @return clock time became active
     */
    public long getStartTime();

    /**
     * Get the current state of the Root.
     *
     * @return current state
     */
    public State getState();

    /**
     * Get the optional update period, if the Root implementation guarantees to
     * update clock time at a fixed frequency.
     *
     * @return optional update period
     */
    public default OptionalLong getPeriod() {
        return OptionalLong.empty();
    }

    /**
     * Listener called on state changes.
     */
    public static interface StateListener {

        /**
         * State changed.
         * 
         * @param source execution context
         */
        public void stateChanged(ExecutionContext source);

    }

    /**
     * Listener called on clock time updates.
     */
    public static interface ClockListener {

        /**
         * Called on clock time update.
         * 
         * @param source execution context
         */
        public void tick(ExecutionContext source);

    }

}
