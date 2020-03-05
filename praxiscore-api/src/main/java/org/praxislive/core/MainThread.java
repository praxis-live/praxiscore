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
package org.praxislive.core;

/**
 * Provide access to the main or first Thread where certain APIs need the
 * ability to run tasks on the thread that initially started the VM. Instances
 * of MainThread should be obtained from the Hub Lookup.
 */
public interface MainThread {

    /**
     * Run the provided task on the main thread. Tasks should not block and
     * should run for as little time as possible. The task will run
     * asynchronously.
     *
     * @param task Runnable task to execute.
     */
    public void runLater(Runnable task);

    /**
     * Query whether the currently executing thread is the main thread.
     *
     * @return true if the current thread is the main thread, false otherwise.
     */
    public boolean isMainThread();

}
