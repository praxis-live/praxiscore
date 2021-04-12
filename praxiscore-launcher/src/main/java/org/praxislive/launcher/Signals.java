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
package org.praxislive.launcher;

/**
 * Instances of Signals provide the ability to register custom signal handlers,
 * for example to ignore SIGINT and similar in a child process. Implementations
 * should be registered and obtained via service loader.
 */
public interface Signals {

    /**
     * Register a handler to execute when the VM receives the specified signal.
     *
     * @param signal signal, such as "INT"
     * @param handler handler to run on signal
     */
    public void register(String signal, Runnable handler);

}
