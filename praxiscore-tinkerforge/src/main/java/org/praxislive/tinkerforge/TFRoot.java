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
package org.praxislive.tinkerforge;

import com.tinkerforge.Device;
import com.tinkerforge.IPConnection;
import com.tinkerforge.NotConnectedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.AbstractRootContainer;
import org.praxislive.core.Clock;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Value;
import org.praxislive.core.Lookup;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 *
 */
public class TFRoot extends AbstractRootContainer {

    private final static String DEFAULT_HOST = "localhost";
    private final static int DEFAULT_PORT = 4223;
    private final static Logger LOG = Logger.getLogger(TFRoot.class.getName());
    private final static long DEFAULT_PERIOD = TimeUnit.MILLISECONDS.toNanos(50);

    private final TFContext context;
    private final Status status;
    private final ComponentInfo info;

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private Lookup lookup;
    private volatile IPConnection ipcon;
    private ComponentEnumerator enumerator;

    private Thread thread;

    public TFRoot() {
        context = new TFContext(this);
        status = new Status();
        registerControl("host", new HostBinding());
        registerControl("port", new PortBinding());
        registerControl("status", status);

        info = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .merge(StartableProtocol.API_INFO)
                .control("host", c -> c.property().input(PString.class))
                .control("port", c -> c.property().input(a -> a
                .number().min(1).max(65535)
                .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)))
                .control("status", c -> c.readOnlyProperty().output(PString.class))
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
        Runner r = new Runner();
        attachDelegate(r);
        r.start();
    }

    @Override
    protected void stopping() {
        enumerator = null;
        context.removeAll();
        status.clear();
        if (ipcon != null) {
            try {
                ipcon.disconnect();
            } catch (NotConnectedException ex) {
                Logger.getLogger(TFRoot.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                ipcon = null;
            }
        }
        interrupt();
    }

    IPConnection getIPConnection() {
        return ipcon;
    }

    private class Runner extends Delegate {

        private void run() {
            LOG.info("Starting delegate runner");
            try {
                ipcon = new IPCon();
                ipcon.connect(host, port);
                enumerator = new ComponentEnumerator();
                ipcon.addEnumerateListener(enumerator);
                ipcon.enumerate();

                Clock clock = getRootHub().getClock();
                long target = clock.getTime();
                while (getState() == State.ACTIVE_RUNNING) {
                    target += DEFAULT_PERIOD;
                    try {
                        doUpdate(target);
                        while (target - clock.getTime() > 0) {
                            doTimedPoll(1, TimeUnit.MILLISECONDS);
                        }
                    } catch (Exception ex) {
                        continue;
                    }
                }
                LOG.info("Ending delegate");
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Can't start connection.", ex);
                setIdle();
            }
            detachDelegate(this);
        }

        private void start() {
            thread = getThreadFactory().newThread(this::run);
            thread.start();
        }
    }

    private class IPCon extends IPConnection {

        @Override
        protected void callConnectedListeners(final short connectReason) {
            invokeLater(() -> {
                IPCon.super.callConnectedListeners(connectReason);
            });
        }

        @Override
        protected void callDeviceListener(final Device device,
                final byte functionID, final byte[] data) {
            invokeLater(() -> {
                IPCon.super.callDeviceListener(device, functionID, data);
            });
        }

        @Override
        protected void callDisconnectedListeners(final short disconnectReason) {
            invokeLater(() -> {
                IPCon.super.callDisconnectedListeners(disconnectReason);
            });
        }

        @Override
        protected void callEnumerateListeners(final String uid,
                final String connectedUid,
                final char position,
                final short[] hardwareVersion,
                final short[] firmwareVersion,
                final int deviceIdentifier,
                final short enumerationType) {
            invokeLater(() -> {
                IPCon.super.callEnumerateListeners(uid, connectedUid,
                        position, hardwareVersion, firmwareVersion,
                        deviceIdentifier, enumerationType);
            });
        }

    }

    private class HostBinding extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set IP address while running");
            }
            host = arg.toString();
        }

        @Override
        protected Value get() {
            return PString.of(host);
        }
    }

    private class PortBinding extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set port while running");
            }
            port = PNumber.from(arg)
                    .map(PNumber::toIntValue)
                    .filter(p -> p >= 1 && p <= 65535)
                    .orElseThrow(IllegalArgumentException::new);
        }

        @Override
        protected Value get() {
            return PNumber.of(port);
        }
    }

    private class Status extends AbstractProperty {

        private final Map<String, String> infoMap = new LinkedHashMap<String, String>();
        private PString cache;

        @Override
        protected void set(long time, Value arg) throws Exception {
            throw new UnsupportedOperationException("Read-only property.");
        }

        @Override
        protected Value get() {
            if (cache == null) {
                StringBuilder builder = new StringBuilder();
                for (String line : infoMap.values()) {
                    builder.append(line);
                    builder.append("\n");
                }
                cache = PString.of(builder);
            }
            return cache;
        }

        private void add(String uid, String info) {
            infoMap.put(uid, info);
            cache = null;
        }

        private void remove(String uid) {
            infoMap.remove(uid);
            cache = null;
        }

        private void clear() {
            infoMap.clear();
            cache = null;
        }

    }

    private class ComponentEnumerator implements IPConnection.EnumerateListener {

//        private Set<String> uids;
        private ComponentEnumerator() {
//            uids = new HashSet<String>();
        }

        @Override
        public void enumerate(final String uid,
                String connectedUid,
                char position,
                short[] hardwareVersion,
                short[] firmwareVersion,
                int deviceID,
                short enumerationType) {
            assert Thread.currentThread() == thread;
            IPConnection ip = ipcon;
            if (ip == null) {
                LOG.fine("enumerate() called but IPConnection is null - ignoring!");
                return; // removed from under us?
            }
            if (enumerationType != IPConnection.ENUMERATION_TYPE_DISCONNECTED /*&&
                     !uids.contains(uid)*/) {
                LOG.log(Level.FINE, "Component connected - UID:{0} Name:{1}", new Object[]{uid, deviceID});
                try {
                    Class<? extends Device> type = TFUtils.getDeviceClass(deviceID);
//                    uids.add(uid);
                    if (getState() == State.ACTIVE_RUNNING
                            && enumerator == ComponentEnumerator.this) {
                        context.addDevice(uid, type);
                        status.add(uid, type.getSimpleName() + " UID:" + uid);
                    }
                } catch (Exception ex) {
                    LOG.log(Level.FINE, "Unable to create device for UID: {0} Name: {1}", new Object[]{uid, deviceID});
                    LOG.log(Level.FINE, "", ex);
                }
            } else if (enumerationType == IPConnection.ENUMERATION_TYPE_DISCONNECTED /*&&
                     uids.contains(uid)*/) {
                LOG.log(Level.FINE, "Component disconnected - UID:{0} Name:{1}", new Object[]{uid, deviceID});
//                uids.remove(uid);

                if (getState() == State.ACTIVE_RUNNING
                        && enumerator == ComponentEnumerator.this) {
                    context.removeDevice(uid);
                    status.remove(uid);
                }

            }
        }
    }
}
