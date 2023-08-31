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
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.praxislive.core.Lookup;
import org.praxislive.core.Root;
import org.praxislive.core.services.Service;
import org.praxislive.hub.Hub;

/**
 * A CoreRootFactory supporting a tree of networked roots. Use
 * {@link #builder()} to create.
 */
public final class NetworkCoreFactory extends Hub.CoreRootFactory {

    private final boolean enableServer;
    private final InetSocketAddress serverAddress;
    private final CIDRUtils cidr;
    private final List<Class<? extends Service>> services;
    private final ChildLauncher childLauncher;
    private final HubConfiguration hubConfiguration;
    private final CompletableFuture<Info> futureInfo;

    private Root root;

    private NetworkCoreFactory(Builder builder) {
        enableServer = builder.enableServer;
        if (enableServer) {
            if (builder.allowRemote) {
                serverAddress = new InetSocketAddress(builder.serverPort);
            } else {
                serverAddress = new InetSocketAddress(
                        InetAddress.getLoopbackAddress(), builder.serverPort);
            }
            cidr = builder.cidr;
        } else {
            serverAddress = null;
            cidr = null;
        }
        services = builder.services;
        hubConfiguration = builder.hubConfiguration;
        childLauncher = builder.childLauncher;
        futureInfo = new CompletableFuture<>();
    }

    @Override
    public synchronized Root createCoreRoot(Hub.Accessor accessor, List<Root> extensions) {
        if (root != null) {
            throw new IllegalStateException("NetworkCoreFactory cannot be reused");
        }
        if (enableServer) {
            root = new ServerCoreRoot(accessor, extensions,
                    services, childLauncher, hubConfiguration,
                    serverAddress, cidr, futureInfo);
        } else {
            root = new NetworkCoreRoot(accessor, extensions,
                    services, childLauncher, hubConfiguration);
        }
        return root;
    }

    @Override
    public synchronized Lookup extendLookup(Lookup lookup) {
        if (root instanceof ServerCoreRoot) {
            return Lookup.of(lookup, ((ServerCoreRoot) root).getResourceResolver());
        } else {
            return lookup;
        }
    }

    /**
     * Get {@link Info} for the started core root. If the server is enabled,
     * this will block until a network connection is established and the socket
     * address and port is available.
     *
     * @param timeout the maximum time to wait
     * @param unit the unit of timeout
     * @return info
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    public Info awaitInfo(long timeout, TimeUnit unit) throws
            InterruptedException, ExecutionException, TimeoutException {
        return futureInfo.get(timeout, unit);
    }

    /**
     * Create a NetworkCoreFactory builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for NetworkCoreFactory.
     */
    public final static class Builder {

        private boolean enableServer;
        private int serverPort;
        private boolean allowRemote;
        private CIDRUtils cidr;
        private List<Class<? extends Service>> services;
        private ChildLauncher childLauncher;
        private HubConfiguration hubConfiguration;

        private Builder() {
            services = List.of();
        }

        /**
         * Enable a server so that other hubs can connect to and control this
         * one. By default the port number will be automatic and the socket
         * bound to the loopback address to only allow local connections.
         *
         * @return this for chaining
         */
        public Builder enableServer() {
            this.enableServer = true;
            return this;
        }

        /**
         * Specify a port for the server. Only has effect when server is
         * enabled.
         *
         * @param port between 1 and 65535, or 0 for automatic
         * @return this for chaining
         */
        public Builder serverPort(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Out of range");
            }
            this.serverPort = port;
            return this;
        }

        /**
         * Allow remote connections. Only has effect when server is enabled.
         * Allowing connections from other than localhost may require
         * consideration of security concerns.
         *
         * @return this for chaining
         */
        public Builder allowRemoteServerConnection() {
            return allowRemoteServerConnection(null);
        }

        /**
         * Allow remote connections. Inbound connections will be matched against
         * the provide CIDR mask if specified. See
         * <a href="https://en.wikipedia.org/wiki/Classless_Inter-Domain_Routing">https://en.wikipedia.org/wiki/Classless_Inter-Domain_Routing</a>.
         * <p>
         * Only has effect when server is enabled. Allowing connections from
         * other than localhost may require consideration of security concerns.
         *
         * @param cidr mask
         * @return this for chaining
         */
        public Builder allowRemoteServerConnection(String cidr) {
            this.allowRemote = true;
            if (cidr == null) {
                this.cidr = null;
            } else {
                try {
                    this.cidr = new CIDRUtils(cidr);
                } catch (UnknownHostException ex) {
                    throw new IllegalArgumentException("Invalid net mask", ex);
                }
            }
            return this;
        }

        /**
         * List of services that will be exposed to connected hubs. Currently
         * only used by child proxies, and will override their default
         * implementation.
         *
         * @param services list of services to expose
         * @return this for chaining
         */
        public Builder exposeServices(List<Class<? extends Service>> services) {
            this.services = List.copyOf(services);
            return this;
        }

        /**
         * Provide an implementation of {@link ChildLauncher} for proxies that
         * require to auto-launch a local child process of the current one.
         *
         * @param launcher child launcher implementation
         * @return this for chaining
         */
        public Builder childLauncher(ChildLauncher launcher) {
            this.childLauncher = launcher;
            return this;
        }

        /**
         * Provide a {@link HubConfiguration} programmatically. This will lock
         * the configuration and stop it being configurable via the
         * hub-configure command.
         *
         * @param configuration hub configuration
         * @return this for chaining
         */
        public Builder hubConfiguration(HubConfiguration configuration) {
            this.hubConfiguration = configuration;
            return this;
        }

        /**
         * Build a NetworkCoreFactory based on the builder configuration.
         *
         * @return NetworkCoreFactory
         */
        public NetworkCoreFactory build() {
            return new NetworkCoreFactory(this);
        }

    }

    /**
     * Information about the launched network hub. Returned from
     * {@link #awaitInfo(long, java.util.concurrent.TimeUnit)}
     */
    public final static class Info {

        private final SocketAddress localAddress;

        Info() {
            this(null);
        }

        Info(SocketAddress localAddress) {
            this.localAddress = localAddress;
        }

        /**
         * Socket address of the launched hub, if available.
         *
         * @return optional of socket address
         */
        public Optional<SocketAddress> serverAddress() {
            return Optional.ofNullable(localAddress);
        }

    }

}
