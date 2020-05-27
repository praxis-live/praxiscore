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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Optional;
import org.praxislive.core.ComponentType;

/**
 * Information about an available proxy process to connect to.
 */
public interface ProxyInfo {

    /**
     * Access the socket address to use to connect to the proxy.
     *
     * @return socket address
     */
    public SocketAddress socketAddress();

    /**
     * Check whether the proxy is running on the same (local) machine. By
     * default this method returns true if the socket address is an instance of
     * InetSocketAddress and is using the loopback address. See
     * {@link InetAddress#isLoopbackAddress()}.
     *
     * @return true if proxy is local
     */
    public default boolean isLocal() {
        var address = socketAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) socketAddress()).getAddress().isLoopbackAddress();
        } else {
            return false;
        }
    }

    /**
     * Check whether this proxy should be used for creating and accessing the
     * given root ID and root type. The order of provided proxies is important
     * as the first matching proxy will take precedence.
     *
     * @param rootID root ID to match against
     * @param rootType root type to match against
     * @return true if this proxy should be used for this ID and type
     */
    public abstract boolean matches(String rootID, ComponentType rootType);

    /**
     * Access an optional {@link Exec} implementation giving information about
     * the process that should be executed when initializing this proxy.
     *
     * @return optional exec
     */
    public default Optional<Exec> exec() {
        return Optional.empty();
    }

    /**
     * Information about process that should be executed when initializing a
     * proxy.
     */
    public static interface Exec {

        /**
         * Optional command to run. If not provided, an implementation of
         * {@link ChildLauncher} will be used. Default is empty optional.
         *
         * @return optional command if not default child
         */
        public default Optional<String> command() {
            return Optional.empty();
        }

        /**
         * List of Java options to make available to the process. Default is
         * empty list.
         *
         * @return list of Java options
         */
        public default List<String> javaOptions() {
            return List.of();
        }

        /**
         * Command line arguments to pass along to the executing process.
         * Default is empty list.
         *
         * @return list of arguments
         */
        public default List<String> arguments() {
            return List.of();
        }

    }

}
