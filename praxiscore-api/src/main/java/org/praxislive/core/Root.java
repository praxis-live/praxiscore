/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2023 Neil C Smith.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Root provides the companion part of the actor-model to Component within
 * PraxisCORE's forest-of-actors model. The Root implementation handles initial
 * message handling and scheduling. There may be many Roots within a running
 * PraxisCORE system - the Roots are sandboxed from each other and the RootHub
 * handles delivery of messages (Packet / Call) from one Root to another.
 * <p>
 * A Root may be a Component or Container, but this is not required. As
 * Component implementations are intended to be lock-free and single-threaded,
 * the Root implementation will ensure that all messages are handled serially.
 * Some Root implementations will have a one-to-one relationship to a thread of
 * execution.
 *
 *
 */
public interface Root extends Lookup.Provider {

    /**
     * Method used by the RootHub to initialize the Root and obtain a
     * Controller. Root implementations will ensure this method can only be
     * invoked once.
     *
     * @param ID the unique ID of this Root
     * @param hub the RootHub the Root resides within
     * @return Controller for use by the RootHub instance
     * @throws IllegalStateException if the Root has already been initialized
     */
    public Root.Controller initialize(String ID, RootHub hub);

    /**
     * An interface used by the RootHub to control the lifecycle of, and
     * communicate with, the Root.
     */
    public interface Controller {

        /**
         * Deliver a Packet to this Root. This method is intended to be called
         * from a thread other than the primary thread of the Root. It will add
         * the packet to a queue and return immediately - this method will never
         * block as it may be called from the thread of another Root.
         * <p>
         * This method will return true if the Packet can be handled (see eg.
         * BlockingQueue::offer)
         *
         * @param packet message (see Packet / Call) to handle
         * @return true if the packet can be handled
         */
        public boolean submitPacket(Packet packet);

        /**
         * Start the Root. Controller implementations will ensure that this
         * method can only be invoked once.
         * <p>
         * The lookup may contain services that the root may utilise - eg. a
         * shared {@link ScheduledExecutorService}. This allows services to be
         * controlled on a per-root basis, rather than relying on the hub
         * lookup.
         *
         * @param lookup optional services for the root to use
         * @throws IllegalStateException if the Root has already been started.
         */
        public void start(Lookup lookup);

        /**
         * Convenience method to call {@link #start(org.praxislive.core.Lookup)}
         * with an empty lookup.
         */
        public default void start() {
            start(Lookup.EMPTY);
        }

        /**
         * Signal the Root to be shutdown. This method is intended to be called
         * asynchronously and will return immediately - it will not wait for the
         * Root to actually complete execution.
         */
        public void shutdown();

        /**
         * Query whether the Root is alive - has been started and has not been
         * signalled to shutdown. This method may return false even if the Root
         * is still in the process of termination. If the caller needs to know
         * when the Root has finished termination, use
         * {@link #awaitTermination(long, java.util.concurrent.TimeUnit)}.
         *
         * @return true if started and not signalled to shutdown
         */
        public boolean isAlive();

        /**
         * Wait for the Root to fully terminate, freeing up all resources.
         *
         * @param timeout maximum time to wait
         * @param unit unit of timeout
         * @throws InterruptedException if the current thread is interrupted
         * @throws TimeoutException if the timeout is reached before termination
         * @throws ExecutionException if the termination is the result of an
         * exception
         */
        public void awaitTermination(long timeout, TimeUnit unit)
                throws InterruptedException, TimeoutException, ExecutionException;

    }

}
