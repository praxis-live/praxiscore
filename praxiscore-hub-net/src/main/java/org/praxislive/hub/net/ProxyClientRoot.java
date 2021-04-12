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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.Clock;
import org.praxislive.core.Control;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.internal.osc.OSCClient;
import org.praxislive.internal.osc.OSCListener;
import org.praxislive.internal.osc.OSCMessage;
import org.praxislive.internal.osc.OSCPacket;

/**
 *
 */
class ProxyClientRoot extends AbstractRoot {

    private final static Logger LOG = Logger.getLogger(ProxyClientRoot.class.getName());
    private final static String HLO = "/HLO";
    private final static String BYE = "/BYE";

    private final ProxyInfo proxyInfo;
    private final List<Class<? extends Service>> services;
    private final ChildLauncher childLauncher;
    private final FileServer.Info fileServerInfo;
    private final PraxisPacketCodec codec;
    private final Dispatcher dispatcher;
    private final Control addRootControl;
    private final Control removeRootControl;

    private OSCClient client;
    private long lastPurgeTime;
    private Watchdog watchdog;
    private Process execProcess;
    private SocketAddress socketAddress;

    ProxyClientRoot(ProxyInfo proxyInfo,
            List<Class<? extends Service>> services,
            ChildLauncher childLauncher,
            FileServer.Info fileServerInfo) {
        this.proxyInfo = proxyInfo;
        this.services = services;
        this.childLauncher = childLauncher;
        this.fileServerInfo = fileServerInfo;
        codec = new PraxisPacketCodec();
        dispatcher = new Dispatcher(codec);
        addRootControl = new RootControl(true);
        removeRootControl = new RootControl(false);
    }

    @Override
    protected void activating() {
        lastPurgeTime = getExecutionContext().getTime();
        dispatcher.remoteSysPrefix = getAddress().toString() + "/_remote";
        setRunning();
    }

    @Override
    protected void terminating() {
        super.terminating();
        if (client != null) {
            LOG.fine("Terminating - sending /BYE");
            try {
                client.send(new OSCMessage(BYE));
            } catch (IOException ex) {
                LOG.log(Level.FINE, null, ex);
            }
        }
        dispose();
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
        if (call.to().component().equals(getAddress())) {
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
        } else if (client != null) {
            dispatcher.handleCall(call);
        } else {
            connect();
            if (client != null) {
                dispatcher.handleCall(call);
            } else {
                getRouter().route(call.error(PError.of("")));
            }
        }
    }

    @Override
    protected void update() {
        var source = getExecutionContext();
        if ((source.getTime() - lastPurgeTime) > TimeUnit.SECONDS.toNanos(1)) {
//            LOG.fine("Triggering dispatcher purge");
            dispatcher.purge(10, TimeUnit.SECONDS);
            lastPurgeTime = source.getTime();
        }
        if (watchdog != null) {
            watchdog.tick();
        }
    }

    private void messageReceived(OSCMessage msg, SocketAddress sender, long timeTag) {
        dispatcher.handleMessage(msg, timeTag);
    }

    private void send(OSCPacket packet) {
        if (client != null) {
            try {
                client.send(packet);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "", ex);
                dispose();
            }
        }
    }

    private void connect() {
        try {
            checkAndExecChild();
            client = OSCClient.newUsing(codec, OSCClient.TCP);
            client.setBufferSize(65536);
            client.setTarget(socketAddress);
            watchdog = new Watchdog(getRootHub().getClock(), client);
            watchdog.start();

            // HLO request
            CountDownLatch hloLatch = new CountDownLatch(1);
            client.addOSCListener(new Receiver(hloLatch));
            client.start();
            client.send(new OSCMessage(HLO, new Object[]{buildHLOParams().toString()}));
            if (hloLatch.await(10, TimeUnit.SECONDS)) {
                LOG.fine("/HLO received OK");
            } else {
                LOG.severe("Unable to connect");
                dispose();
            }

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to connect", ex);
            dispose();
        }
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
        params.put(Utils.KEY_REMOTE_SERVICES, buildServiceMap());
        if (!proxyInfo.isLocal()) {
            params.put(Utils.KEY_MASTER_USER_DIRECTORY, Utils.getUserDirectory().toURI().toString());
            if (fileServerInfo != null) {
                params.put(Utils.KEY_FILE_SERVER_PORT, fileServerInfo.getPort());
            }
        }
        return params.build();
    }

    private PMap buildServiceMap() {
        PMap.Builder srvs = PMap.builder(services.size());
        services.forEach(service -> {
            try {
                srvs.put(service.getName(), findService(service));
            } catch (ServiceUnavailableException ex) {
                Logger.getLogger(ProxyClientRoot.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        return srvs.build();
    }

    private void dispose() {
        if (client != null) {
            client.dispose();
            client = null;
        }
        if (watchdog != null) {
            watchdog.shutdown();
            watchdog = null;
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
                    LOG.log(Level.SEVERE, "Child process won't quit", ex);
                }
            }
            ChildRegistry.INSTANCE.remove(execProcess);
            execProcess = null;
        }
    }

    private class Dispatcher extends OSCDispatcher {

        private String remoteSysPrefix;

        private Dispatcher(PraxisPacketCodec codec) {
            super(codec, new Clock() {
                @Override
                public long getTime() {
                    return getExecutionContext().getTime();
                }
            });
        }

        @Override
        void send(OSCPacket packet) {
            ProxyClientRoot.this.send(packet);
        }

        @Override
        void send(Call call) {
            getRouter().route(call);
        }

        @Override
        String getRemoteSysPrefix() {
            assert remoteSysPrefix != null;
            return remoteSysPrefix;
        }

    }

    private class Watchdog extends Thread {

        private final Clock clock;
        private final OSCClient client;

        private volatile long lastTickTime;
        private volatile boolean active;

        private Watchdog(Clock clock, OSCClient client) {
            this.clock = clock;
            this.client = client;
            lastTickTime = clock.getTime();
            setDaemon(true);
        }

        @Override
        public void run() {
            while (active) {
                if ((clock.getTime() - lastTickTime) > TimeUnit.SECONDS.toNanos(10)) {
                    client.dispose();
                    active = false;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    // not a problem
                }
            }
        }

        private void tick() {
            lastTickTime = clock.getTime();
        }

        private void shutdown() {
            active = false;
            interrupt();
        }

    }

    private class Receiver implements OSCListener {

        private CountDownLatch hloLatch;

        private Receiver(CountDownLatch hloLatch) {
            this.hloLatch = hloLatch;
        }

        @Override
        public void messageReceived(final OSCMessage msg, final SocketAddress sender,
                final long timeTag) {
            if (hloLatch != null && HLO.equals(msg.getName())) {
                hloLatch.countDown();
                hloLatch = null;
            }
            invokeLater(new Runnable() {

                @Override
                public void run() {
                    ProxyClientRoot.this.messageReceived(msg, sender, timeTag);
                }
            });
        }

    }

    private class RootControl implements Control {

        private final boolean add;

        private RootControl(boolean add) {
            this.add = add;
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                if (client != null) {
                    dispatch(call);
                } else {
                    connect();
                    if (client != null) {
                        dispatch(call);
                    } else {
                        router.route(call.error(PError.of("Couldn't connect to client")));
                    }
                }
            }
        }

        private void dispatch(Call call) {
            if (add) {
                dispatcher.handleAddRoot(call);
            } else {
                dispatcher.handleRemoveRoot(call);
            }
        }

    }

}
