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
package org.praxislive.script;

import org.praxislive.core.Clock;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;

/**
 * Environment context interface passed in to
 * {@link StackFrame#process(org.praxislive.script.Env)} and
 * {@link InlineCommand#process(org.praxislive.script.Env, org.praxislive.script.Namespace, java.util.List)}.
 * An implementation of this interface provides access to various services of
 * the script executor required to implement commands.
 *
 */
public interface Env {

    /**
     * Name of the context variable that relative component, control and port
     * addresses are resolved against. Used and controlled by the {@code @}
     * command.
     */
    public final static String CONTEXT = "_CTXT";

    /**
     * Name of the present working directory variable used to resolve relative
     * file paths in various commands.
     */
    public final static String PWD = "_PWD";

    /**
     * Lookup object of the script executor.
     *
     * @return lookup
     */
    public Lookup getLookup();

    /**
     * Current clock time inside the script executor. Should be used when
     * creating calls inside a command, and for any other purpose that the
     * current clock time is required.
     *
     * @see Clock
     * @return current clock time
     */
    public long getTime();

    /**
     * A packet router for sending calls during command execution.
     *
     * @return packet router
     */
    public abstract PacketRouter getPacketRouter();

    /**
     * The control address of this script executor. Should be used as the from
     * address in calls created during command execution. Replies to this
     * address will be routed to
     * {@link StackFrame#postResponse(org.praxislive.core.Call)} on the active
     * command.
     *
     * @return script executor control address
     */
    public abstract ControlAddress getAddress();

}
