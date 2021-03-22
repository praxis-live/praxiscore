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
package org.praxislive.base.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.praxislive.base.AbstractContainer;
import org.praxislive.base.AbstractProperty;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlPort;
import org.praxislive.core.Info;
import org.praxislive.core.Port;
import org.praxislive.core.PortAddress;
import org.praxislive.core.PortConnectionException;
import org.praxislive.core.PortInfo;
import org.praxislive.core.PortListener;
import org.praxislive.core.Value;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.types.PMap;

/**
 *
 */
public final class UserContainer extends AbstractContainer {
    
    private final static ComponentInfo BASE_INFO = Info.component(cmp -> cmp
            .merge(ComponentProtocol.API_INFO)
            .merge(ContainerProtocol.API_INFO)
            .control("ports", c -> c.property().input(PMap.class).defaultValue(PMap.EMPTY))
            .property(ComponentInfo.KEY_DYNAMIC, true)
    );
    
    private final static PortInfo FALLBACK_PORT_INFO =
            PortInfo.create(ControlPort.class, PortInfo.Direction.BIDI, PMap.EMPTY);

    private final Map<String, PortProxy> proxies;
    
    private ComponentInfo info;
    
    public UserContainer() {
        proxies = new LinkedHashMap<>();
        registerControl("ports", new PortsBinding());
    }

    @Override
    public Port getPort(String id) {
        var port = super.getPort(id);
        if (port != null) {
            return port;
        }
        var proxy = proxies.get(id);
        if (proxy != null) {
            return proxy.unproxy();
        }
        return null;
    }

    @Override
    protected void addChild(String id, Component child) throws VetoException {
        super.addChild(id, child);
        info = null;
    }

    @Override
    protected Component removeChild(String id) {
        info = null;
        return super.removeChild(id);
    }
    
    @Override
    public ComponentInfo getInfo() {
        if (info == null) {
            if (proxies.isEmpty()) {
                info = BASE_INFO;
            } else {
                var builder = Info.component()
                        .merge(BASE_INFO);
                boolean fallback = false;
                for (PortProxy proxy : proxies.values()) {
                    var portInfo = proxy.getInfo();
                    if (portInfo == FALLBACK_PORT_INFO && getChild(proxy.childID) != null) {
                        fallback = true;
                    }
                    builder.port(proxy.id, portInfo);
                }
                if (fallback) {
                    return builder.build();
                } else {
                    info = builder.build();
                }
            }
        }
        return info;
    }
    
    private class PortsBinding extends AbstractProperty {
        
        private PMap ports = PMap.EMPTY;

        @Override
        protected void set(long time, Value arg) throws Exception {
            PMap newPorts = PMap.from(arg).orElseThrow(IllegalArgumentException::new);
            if (ports.equals(newPorts)) {
                return;
            }
            List<PortProxy> lst = new ArrayList<>();
            List<String> newPortNames = newPorts.keys();
            for (String key : newPorts.keys()) {
                if (!PortAddress.isValidID(key)) {
                    throw new IllegalArgumentException("" + key + " : is not a valid port ID");
                }
                String s = newPorts.get(key).toString();
                String[] parts = s.split("!");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("" + s + " : is not a valid relative port");
                }
                String child = parts[0];
                String port = parts[1];
                if (!ComponentAddress.isValidID(child)) {
                    throw new IllegalArgumentException("" + child + " : is not a valid component ID");
                }
                if (!PortAddress.isValidID(port)) {
                    throw new IllegalArgumentException("" + key + " : is not a valid port ID");
                }
                lst.add(new PortProxy(key, child, port));
            }
            for (String id : ports.keys()) {
                if (!newPortNames.contains(id)) {
                    Port p = getPort(id);
                    p.disconnectAll();
                }
            }
            proxies.clear();
            for (PortProxy p : lst) {
                proxies.put(p.id, p);
            }
            ports = newPorts;
            info = null;
        }

        @Override
        protected Value get() {
            return ports;
        }
        
    }
    
    private class PortProxy implements Port {
        
        private final String id;
        private final String childID;
        private final String portID;
        
        PortProxy(String id, String childID, String portID) {
            this.id = id;
            this.childID = childID;
            this.portID = portID;
        }

        private PortInfo getInfo() {
            Component child = getChild(childID);
            if (child != null) {
                ComponentInfo info = child.getInfo();
                if (info != null) {
                    PortInfo portInfo = info.portInfo(portID);
                    if (portInfo != null) {
                        return portInfo;
                    }
                }
            }
            return FALLBACK_PORT_INFO;
        }
        
        private Port unproxy() {
            Component child = getChild(childID);
            if (child != null) {
                Port port = child.getPort(portID);
                if (port != null) {
                    return port;
                }
            }
            return this;
        }

        @Override
        public void connect(Port port) throws PortConnectionException {
            throw new PortConnectionException();
        }

        @Override
        public void disconnect(Port port) {
        }

        @Override
        public void disconnectAll() {
        }

        @Override
        public List<? extends Port> connections() {
            return List.of();
        }

        @Override
        public void addListener(PortListener listener) {
        }

        @Override
        public void removeListener(PortListener listener) {
        }
        
    }
    
    
}
