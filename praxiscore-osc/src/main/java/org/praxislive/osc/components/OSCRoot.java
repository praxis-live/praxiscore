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
 *
 */
package org.praxislive.osc.components;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.AbstractRootContainer;
import org.praxislive.core.Clock;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.internal.osc.OSCListener;
import org.praxislive.internal.osc.OSCMessage;
import org.praxislive.internal.osc.OSCServer;

/**
 *
 */
public class OSCRoot extends AbstractRootContainer {

    private final static Logger LOG = Logger.getLogger(OSCRoot.class.getName());
    private final static int DEFAULT_PORT = 1234;
    private final static String DEFAULT_PROTOCOL = OSCServer.UDP;
    
    private final OSCContext context;
    private final BlockingQueue<OSCMessage> messages;
    private final ComponentInfo info;
    
    private int port = DEFAULT_PORT;
    private Lookup lookup;
    private OSCServer server;
    private OSCMessage lastMessage;

    public OSCRoot() {
        context = new OSCContext();
        messages = new LinkedBlockingQueue<>();
        registerControl("port", new PortBinding());
        registerControl("last-message", new LastMessageBinding());
        
        info = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .merge(StartableProtocol.API_INFO)
                .control("port", c -> c
                        .property()
                        .defaultValue(PNumber.of(DEFAULT_PORT))
                        .input(a -> a
                                .number()
                                .min(1)
                                .max(65535)
                        )
                )
                .control("last-message", c -> c
                        .readOnlyProperty()
                        .output(PString.class)
                )
        );
        
    }

    @Override
    public ComponentInfo getInfo() {
        return info;
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), context);
        }
        return lookup;
    }

    @Override
    protected void starting() {
        try {
            lastMessage = null;
            server = OSCServer.newUsing(DEFAULT_PROTOCOL, port);
            server.addOSCListener(new OSCListenerImpl());
            server.start();
            var r = new OSCRunnable(getRootHub().getClock());
            attachDelegate(r);
            r.start();
        } catch (IOException ex) {
            Logger.getLogger(OSCRoot.class.getName()).log(Level.SEVERE, null, ex);
            setIdle();
        }
    }

    @Override
    protected void stopping() {
        terminateServer();
    }

    @Override
    protected void terminating() {
        terminateServer();
    }

    private void terminateServer() {
        if (server == null) {
            return;
        }
        try {
            server.stop();
        } catch (IOException ex) {
            // not bothered?
        }
        server.dispose();
        server = null;
        messages.clear();
    }


    private class PortBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set port while running");
            }
            port = PNumber.from(value).map(PNumber::toIntValue)
                    .orElseThrow(IllegalArgumentException::new);
        }

        @Override
        public Value get() {
            return PNumber.of(port);
        }
    }

    private class LastMessageBinding extends AbstractProperty {

        @Override
        public Value get() {
            if (lastMessage != null) {
                StringBuilder sb = new StringBuilder(lastMessage.getName());
                for (int i = 0; i < lastMessage.getArgCount(); i++) {
                    sb.append(" ");
                    sb.append(lastMessage.getArg(i));
                }
                return PString.of(sb);
            } else {
                return PString.EMPTY;
            }
        }

        @Override
        protected void set(long time, Value arg) throws Exception {
            throw new UnsupportedOperationException();
        }

    }

    private class OSCListenerImpl implements OSCListener {

        @Override
        public void messageReceived(OSCMessage oscm, SocketAddress sa, long l) {
            messages.add(oscm);
        }
    }

    private class OSCRunnable extends Delegate {
        
        private final Clock clock;
        
        private OSCRunnable(Clock clock) {
            this.clock = clock;
        }

        private void run() {
            while (getState() == State.ACTIVE_RUNNING) {
                long time = clock.getTime();
                doUpdate(time);
                OSCMessage msg = null;
                try {
                    msg = messages.poll(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                }
                while (msg != null) {
                    LOG.log(Level.FINEST, "Handling message to {0}", msg.getName());
                    lastMessage = msg;
                    context.dispatch(msg, time);
                    msg = messages.poll();
                }
                if (server != null && !server.isActive()) {
                    setIdle();
                }
            }
            detachDelegate(this);
        }
        
        private void start() {
            Thread t = getThreadFactory().newThread(this::run);
            t.start();
        }
    }
}
