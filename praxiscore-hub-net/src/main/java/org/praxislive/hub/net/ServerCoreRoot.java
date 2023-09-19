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
package org.praxislive.hub.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Protocol;
import org.praxislive.core.Root;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PResource;
import org.praxislive.hub.Hub;

import static java.lang.System.Logger.Level;

/**
 *
 */
class ServerCoreRoot extends NetworkCoreRoot {

    private final static System.Logger LOG = System.getLogger(ServerCoreRoot.class.getName());

    private final InetSocketAddress localAddress;
    private final CIDRUtils clientValidator;
    private final Dispatcher dispatcher;
    private final ResourceResolver resourceResolver;
    private final Map<SocketAddress, Channel> connections;

    private EventLoopGroup eventLoopGroup;
    private Channel serverChannel;
    private SocketAddress parent;
    private long lastPurgeTime;
    private URI remoteUserDir;
    private URI remoteFileServer;
    private CompletableFuture<NetworkCoreFactory.Info> futureInfo;
    private String remoteSysPrefix;

    ServerCoreRoot(Hub.Accessor hubAccess,
            List<Root> exts,
            List<Class<? extends Service>> services,
            ChildLauncher childLauncher,
            HubConfiguration configuration,
            InetSocketAddress address,
            CIDRUtils clientValidator,
            CompletableFuture<NetworkCoreFactory.Info> futureInfo) {
        super(hubAccess, exts, services, childLauncher, configuration);
        this.localAddress = address;
        this.clientValidator = clientValidator;
        this.dispatcher = new Dispatcher();
        this.resourceResolver = new ResourceResolver();
        this.futureInfo = futureInfo;
        connections = new ConcurrentHashMap<>();
    }

    @Override
    protected void starting() {
        remoteSysPrefix = getAddress().toString() + "/_remote";
        eventLoopGroup = new NioEventLoopGroup();
        try {
            var bootstrap = new ServerBootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(localAddress)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(
                                    new IonEncoder(),
                                    new IonDecoder(),
                                    new Receiver()
                            );
                        }
                    });
            serverChannel = bootstrap.bind().sync().channel();
            if (futureInfo != null) {
                futureInfo.complete(new NetworkCoreFactory.Info(serverChannel.localAddress()));
                futureInfo = null;
            }
        } catch (Exception ex) {
            LOG.log(Level.ERROR, "Error starting server", ex);
            if (futureInfo != null) {
                futureInfo.completeExceptionally(ex);
                futureInfo = null;
            }
            forceTermination();
            throw new RuntimeException(ex);
        }
        super.starting();
    }

    @Override
    protected void terminating() {
        super.terminating();
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (eventLoopGroup != null) {
                eventLoopGroup.shutdownGracefully();
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Error shutting down server", ex);
        } finally {
            serverChannel = null;
            eventLoopGroup = null;
            parent = null;
        }
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        var address = getAddress();
        var toComponent = call.to().component();
        if (toComponent.equals(address)) {
            super.processCall(call, router);
        } else if (toComponent.rootID().equals(address.rootID())
                && toComponent.depth() == 3
                && "services".equals(toComponent.componentID(1))) {
            dispatcher.handleServiceCall(call, toComponent.componentID(2), call.to().controlID());
        } else {
            dispatcher.handleCall(call);
        }
    }

    PResource.Resolver getResourceResolver() {
        return resourceResolver;
    }

    @Override
    protected void update() {
        super.update();
        long time = getExecutionContext().getTime();
        if ((time - lastPurgeTime) > TimeUnit.SECONDS.toNanos(1)) {
            LOG.log(Level.TRACE, "Triggering dispatcher purge");
            dispatcher.purge(10, TimeUnit.SECONDS);
            lastPurgeTime = time;
        }
    }

    private void handleMessages(SocketAddress sender, List<Message> messages) {
        for (var msg : messages) {
            if (!handleMessage(sender, msg)) {
                break;
            }
        }
    }

    private boolean handleMessage(SocketAddress sender, Message message) {
        if (parent == null || !parent.equals(sender)) {
            if (message instanceof Message.System sysMsg
                    && Message.System.HELLO.equals(sysMsg.type())) {
                // fall through and handle HELLO
            } else {
                LOG.log(Level.WARNING, "Received unexpected message from {0}", sender);
                return false;
            }
        }

        if (message instanceof Message.System systemMessage) {
            return switch (systemMessage.type()) {
                case Message.System.HELLO -> {
                    yield handleHello(sender, systemMessage);
                }
                case Message.System.GOODBYE -> {
                    yield handleGoodbye(sender, systemMessage);
                }
                default -> {
                    LOG.log(Level.WARNING, "Unexpected system message {0}", systemMessage);
                    yield true;
                }
            };
        } else {
            dispatcher.handleMessage(sender, message);
            return true;
        }

    }

    private boolean handleHello(SocketAddress sender, Message.System helloMessage) {
        if (parent != null) {
            if (parent.equals(sender)) {
                LOG.log(Level.DEBUG, "Duplicate Hello message from {0}", sender);
                return true;
            } else {
                LOG.log(Level.ERROR, "Unexpected Hello message from {0}", sender);
                return false;
            }
        }
        try {
            if (validate(sender) && handleHelloData(sender, helloMessage.data())) {
                connections.get(sender).writeAndFlush(List.of(new Message.System(
                        helloMessage.matchID(),
                        Message.System.HELLO_OK,
                        PMap.EMPTY
                )));
                parent = sender;
                return true;
            }
        } catch (Exception ex) {
            LOG.log(Level.ERROR, "Error during hello handling", ex);
        }
        return false;
    }

    private boolean handleGoodbye(SocketAddress sender, Message.System goodbyeMessage) {
        if (parent != null && parent.equals(sender)) {
            parent = null;
            forceTermination();
        }
        return false;
    }

    private boolean validate(SocketAddress sender) {
        if (clientValidator == null) {
            // server forced local only
            return true;
        }
        if (sender instanceof InetSocketAddress inet) {
            try {
                return clientValidator.isInRange(inet.getHostString());
            } catch (UnknownHostException ex) {
                LOG.log(Level.ERROR, "Unable to validate connection", ex);
                // fall through
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean handleHelloData(SocketAddress sender, PMap data) {
        try {
            String masterUserDir = data.getString(Utils.KEY_MASTER_USER_DIRECTORY, null);
            if (masterUserDir != null) {
                remoteUserDir = URI.create(masterUserDir);
            }
            PArray services = PArray.parse(data.getString(Utils.KEY_REMOTE_SERVICES, ""));
            if (!services.isEmpty()) {
                for (var serviceName : services) {
                    Class<? extends Service> service;

                    try {
                        service = (Class<? extends Service>) Protocol.Type.fromName(serviceName.toString())
                                .map(Protocol.Type::asClass)
                                .filter(Service.class::isAssignableFrom)
                                .orElseThrow(ClassNotFoundException::new);
                        var serviceAddress = ComponentAddress.of(
                                getAddress() + "/services/" + serviceName);
                        getHubAccessor().registerService(service, serviceAddress);
                    } catch (ClassNotFoundException classNotFoundException) {
                        LOG.log(Level.DEBUG, "Service {0} not known.", serviceName);
                    }
                }
            }
            int fileServerPort = data.getInt(Utils.KEY_FILE_SERVER_PORT, 0);
            if (fileServerPort > 0) {
                remoteFileServer = URI.create("http://"
                        + ((InetSocketAddress) sender).getAddress().getHostAddress()
                        + ":" + fileServerPort);
            }
            return true;

        } catch (Exception ex) {
            LOG.log(Level.ERROR, "Error configuring hello parameters", ex);
            return false;

        }
    }

    private class Dispatcher extends MessageDispatcher {

        @Override
        void dispatchCall(Call call) {
            getRouter().route(call);
        }

        @Override
        void dispatchMessage(SocketAddress remote, Message msg) {
            connections.get(remote).writeAndFlush(List.of(msg));
        }

        @Override
        ComponentAddress findService(Class<? extends Service> service)
                throws ServiceUnavailableException {
            return getLookup().find(Services.class)
                    .flatMap(sm -> {
                        return sm.locateAll(service)
                                .filter(cmp -> !cmp.rootID().equals(getAddress().rootID())
                                || cmp.depth() == 1)
                                .findFirst();
                    })
                    .orElseThrow(() -> new ServiceUnavailableException(service.toString()));
        }

        @Override
        SocketAddress getPrimaryRemoteAddress() {
            return parent;
        }

        @Override
        long getTime() {
            return getExecutionContext().getTime();
        }

        @Override
        String getRemoteSysPrefix() {
            assert remoteSysPrefix != null;
            return remoteSysPrefix;
        }

    }

    private class Receiver extends SimpleChannelInboundHandler<List<Message>> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            connections.put(ctx.channel().remoteAddress(), ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, List<Message> msgs) throws Exception {
            var address = ctx.channel().remoteAddress();
            invokeLater(() -> handleMessages(address, msgs));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            connections.remove(ctx.channel().remoteAddress(),
                    ctx.channel());
        }

    }

    private class ResourceResolver implements PResource.Resolver {

        @Override
        public List<URI> resolve(PResource resource) {
            URI dir = remoteUserDir;
            URI srv = remoteFileServer;
            URI res = resource.value();
            if (dir == null && srv == null) {
                return Collections.singletonList(res);
            }

            if (!"file".equals(res.getScheme())) {
                return Collections.singletonList(res);
            }

            List<URI> uris = new ArrayList<>(2);

            if (dir != null) {
                uris.add(Utils.getUserDirectory().toURI().resolve(dir.relativize(res)));
            }

            if (srv != null) {
                uris.add(srv.resolve(res.getRawPath()));
            }

            return uris;

        }

    }

}
