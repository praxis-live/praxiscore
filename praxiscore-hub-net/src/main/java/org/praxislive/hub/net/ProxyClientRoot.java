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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Control;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Protocol;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

import static java.lang.System.Logger.Level;

/**
 *
 */
class ProxyClientRoot extends AbstractRoot {

    private final static System.Logger LOG = System.getLogger(ProxyClientRoot.class.getName());

    private final ProxyInfo proxyInfo;
    private final EventLoopGroup eventLoopGroup;
    private final List<Class<? extends Service>> exposedServices;
    private final ChildLauncher childLauncher;
    private final FileServer.Info fileServerInfo;
    private final Dispatcher dispatcher;
    private final Control addRootControl;
    private final Control removeRootControl;

    private Channel clientChannel;
    private long lastPurgeTime;
    private Process execProcess;
    private SocketAddress socketAddress;
    private String remoteSysPrefix;

    ProxyClientRoot(ProxyInfo proxyInfo,
            EventLoopGroup eventLoopGroup,
            List<Class<? extends Service>> exposedServices,
            ChildLauncher childLauncher,
            FileServer.Info fileServerInfo) {
        this.proxyInfo = proxyInfo;
        this.eventLoopGroup = eventLoopGroup;
        this.exposedServices = exposedServices;
        this.childLauncher = childLauncher;
        this.fileServerInfo = fileServerInfo;
        dispatcher = new Dispatcher();
        addRootControl = new RootControl(true);
        removeRootControl = new RootControl(false);
    }

    @Override
    protected void activating() {
        lastPurgeTime = getExecutionContext().getTime();
        remoteSysPrefix = getAddress().toString() + "/_remote";
        setRunning();
    }

    @Override
    protected void terminating() {
        super.terminating();
        if (clientChannel != null) {
            clientChannel.writeAndFlush(List.of(new Message.System(
                    0,
                    Message.System.GOODBYE,
                    PMap.EMPTY
            )));
        }
        dispose();
        try {
            eventLoopGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).sync();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Error closing down proxy client", ex);
        }
        destroyChild();
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        if (getState() != State.ACTIVE_RUNNING) {
            if (call.isReplyRequired()) {
                router.route(call.error(PError.of("Terminated")));
            }
            return;
        }
        var address = getAddress();
        var toComponent = call.to().component();
        if (toComponent.equals(address)) {
            try {
                switch (call.to().controlID()) {
                    case RootManagerService.ADD_ROOT:
                        addRootControl.call(call, router);
                        break;
                    case RootManagerService.REMOVE_ROOT:
                        removeRootControl.call(call, router);
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            } catch (Exception ex) {
                router.route(call.error(PError.of(ex)));
            }
            return;
        }
        if (clientChannel == null) {
            connect();
            if (clientChannel == null) {
                getRouter().route(call.error(PError.of("")));
                return;
            }
        }
        if (toComponent.rootID().equals(address.rootID())
                && toComponent.depth() == 3
                && "services".equals(toComponent.componentID(1))) {
            dispatcher.handleServiceCall(call, toComponent.componentID(2), call.to().controlID());
        } else {
            dispatcher.handleCall(call);

        }
    }

    @Override
    protected void update() {
        var source = getExecutionContext();
        if ((source.getTime() - lastPurgeTime) > TimeUnit.SECONDS.toNanos(1)) {
//            LOG.fine("Triggering dispatcher purge");
            dispatcher.purge(NetworkCoreRoot.TIMEOUT, TimeUnit.SECONDS);
            lastPurgeTime = source.getTime();
        }
    }

    private void connect() {
        try {
            checkAndExecChild();
            var helloLatch = new CountDownLatch(1);
            var receiver = new Receiver(helloLatch);
            var bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(
                                    new IonEncoder(),
                                    new IonDecoder(),
                                    receiver);
                        }
                    }
                    );
            clientChannel = bootstrap.connect(socketAddress).sync().channel();

            // HLO request
            clientChannel.writeAndFlush(List.of(new Message.System(
                    0,
                    Message.System.HELLO,
                    buildHLOParams()
            ))).sync();
            if (helloLatch.await(10, TimeUnit.SECONDS)) {
                LOG.log(Level.DEBUG, "/HLO received OK");
            } else {
                LOG.log(Level.ERROR, "Unable to connect");
                dispose();
            }

        } catch (Exception ex) {
            LOG.log(Level.ERROR, "Unable to connect", ex);
            dispose();
        }
    }

    private void handleMessages(List<Message> messages) {
        for (var msg : messages) {
            if (!handleMessage(msg)) {
                break;
            }
        }
    }

    private boolean handleMessage(Message message) {
        if (message instanceof Message.System systemMessage) {
            return switch (systemMessage.type()) {
                case Message.System.HELLO_OK -> {
                    yield handleHelloOKMessage(systemMessage);
                }
                case Message.System.HELLO_ERROR -> {
                    yield handleHelloErrorMessage(systemMessage);
                }
                default -> {
                    LOG.log(Level.WARNING, "Unexpected system message {0}", systemMessage);
                    yield true;
                }
            };
        } else {
            dispatcher.handleMessage(socketAddress, message);
            return true;
        }

    }

    private boolean handleHelloOKMessage(Message.System helloOK) {
        return true;
    }

    private boolean handleHelloErrorMessage(Message.System helloError) {
        return false;
    }

    private void checkAndExecChild() throws Exception {
        if (execProcess != null) {
            if (execProcess.isAlive()) {
                LOG.log(Level.INFO, "Child process already running");
                return;
            } else {
                throw new IllegalStateException("Child process terminated");
            }
        }
        ProxyInfo.Exec exec = proxyInfo.exec().orElse(null);
        if (exec == null) {
            socketAddress = proxyInfo.socketAddress();
            return;
        }
        String cmd = exec.command().orElse(null);
        if (cmd == null) {
            if (childLauncher == null) {
                throw new IllegalStateException("No child launcher for exec");
            }
            var childInfo = childLauncher.launch(exec.javaOptions(), exec.arguments());
            execProcess = childInfo.handle();
            socketAddress = childInfo.address();
            ChildRegistry.INSTANCE.add(execProcess);
        } else {
            throw new UnsupportedOperationException("Only default command supported at present");
        }
    }

    private PMap buildHLOParams() {
        PMap.Builder params = PMap.builder();
        params.put(Utils.KEY_REMOTE_SERVICES, buildServices());
        if (!proxyInfo.isLocal()) {
            params.put(Utils.KEY_MASTER_USER_DIRECTORY, Utils.getUserDirectory().toURI().toString());
            if (fileServerInfo != null) {
                params.put(Utils.KEY_FILE_SERVER_PORT, fileServerInfo.port());
            }
        }
        return params.build();
    }

    private PArray buildServices() {
        return exposedServices.stream()
                .map(Protocol.Type::of)
                .map(Protocol.Type::name)
                .map(PString::of)
                .collect(PArray.collector());
    }

    private void dispose() {
        if (clientChannel != null) {
            clientChannel.close();
            clientChannel = null;
        }
        dispatcher.purge(0, TimeUnit.NANOSECONDS);
    }

    private void destroyChild() {
        if (execProcess != null) {
            boolean exited = false;
            try {
                execProcess.destroy();
                exited = execProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                // fall through
            }
            if (!exited) {
                execProcess.destroyForcibly();
                try {
                    execProcess.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    LOG.log(Level.ERROR, "Child process won't quit", ex);
                }
            }
            ChildRegistry.INSTANCE.remove(execProcess);
            execProcess = null;

        }
    }

    private class Dispatcher extends MessageDispatcher {

        @Override
        void dispatchCall(Call call) {
            getRouter().route(call);
        }

        @Override
        void dispatchMessage(SocketAddress remote, Message msg) throws Exception {
            if (!remote.equals(socketAddress)) {
                throw new IllegalArgumentException("Unknown remote address");
            }
            clientChannel.writeAndFlush(List.of(msg));
        }

        @Override
        ComponentAddress findService(Class<? extends Service> service) throws ServiceUnavailableException {
            return getLookup().find(Services.class)
                    .flatMap(sm -> {
                        return sm.locateAll(service)
                                .filter(cmp -> !cmp.rootID().equals(getAddress().rootID()))
                                .findFirst();
                    })
                    .orElseThrow(() -> new ServiceUnavailableException(service.toString()));
        }

        @Override
        SocketAddress getPrimaryRemoteAddress() {
            return socketAddress;
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

        private CountDownLatch hloLatch;

        private Receiver(CountDownLatch hloLatch) {
            this.hloLatch = hloLatch;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, List<Message> msg) throws Exception {
            if (hloLatch != null) {
                hloLatch.countDown();
                hloLatch = null;
            }
            invokeLater(() -> handleMessages(msg));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            invokeLater(() -> dispose());
        }

    }

    private class RootControl implements Control {

        private final String service;
        private final String serviceControl;

        private RootControl(boolean add) {
            service = Protocol.Type.of(RootManagerService.class).name();
            this.serviceControl = add ? RootManagerService.ADD_ROOT
                    : RootManagerService.REMOVE_ROOT;
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                if (clientChannel != null) {
                    dispatcher.handleServiceCall(call, service, serviceControl);
                } else {
                    connect();
                    if (clientChannel != null) {
                        dispatcher.handleServiceCall(call, service, serviceControl);
                    } else {
                        router.route(call.error(PError.of("Couldn't connect to client")));
                    }
                }
            }
        }

    }

}
