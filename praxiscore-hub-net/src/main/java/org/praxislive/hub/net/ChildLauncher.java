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
package org.praxislive.hub.net;

import java.net.SocketAddress;
import java.util.List;
import java.util.Objects;

/**
 * Service for launching a child process clone of this process, to be provided
 * to {@link NetworkCoreFactory}.
 */
public interface ChildLauncher {

    /**
     * Launch a child process with the optional given Java options and
     * arguments. This method will block until the process is launched and the
     * process handle and network socket to connect to are available.
     *
     * @param javaOptions optional Java options to pass to the child process
     * @param arguments optional arguments to be passed to the child process
     * @return Info of the child process
     * @throws Exception if the process launch fails, times out, etc.
     */
    public Info launch(List<String> javaOptions, List<String> arguments)
            throws Exception;

    /**
     * Information about the launched child process.
     */
    public static class Info {

        private final Process handle;
        private final SocketAddress address;

        /**
         * Construct an Info object, for use by ChildLauncher implementation.
         * 
         * @param handle Process of child.
         * @param address SocketAddress to connect to child.
         */
        public Info(Process handle, SocketAddress address) {
            this.handle = Objects.requireNonNull(handle);
            this.address = Objects.requireNonNull(address);
        }

        /**
         * Access the {@link Process} handle of the child.
         * 
         * @return process handle
         */
        public final Process handle() {
            return handle;
        }

        /**
         * Access the {@link SocketAddress} to connect to the child.
         * 
         * @return address of the child
         */
        public final SocketAddress address() {
            return address;
        }

    }

}
