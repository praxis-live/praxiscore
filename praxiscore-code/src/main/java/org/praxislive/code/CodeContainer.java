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
package org.praxislive.code;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.praxislive.base.*;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.ControlPort;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.Port;
import org.praxislive.core.PortAddress;
import org.praxislive.core.PortConnectionException;
import org.praxislive.core.PortInfo;
import org.praxislive.core.PortListener;
import org.praxislive.core.Value;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PMap;

/**
 *
 */
public class CodeContainer<D extends CodeContainerDelegate> extends CodeComponent<D>
        implements Container {

    private final static PortInfo FALLBACK_PORT_INFO
            = PortInfo.create(ControlPort.class, PortInfo.Direction.BIDI, PMap.EMPTY);

    private final ContainerImpl container;
    private final Map<String, PortProxy> proxies;
    private final Control proxyProperty;

    private PMap portMap;
    private ComponentInfo baseInfo;
    private ComponentInfo info;

    CodeContainer() {
        container = new ContainerImpl(this);
        proxies = new LinkedHashMap<>();
        portMap = PMap.EMPTY;
        proxyProperty = new AbstractProperty() {
            @Override
            protected Value get() {
                return getPortMap();
            }

            @Override
            protected void set(long time, Value arg) throws Exception {
                setPortMap(PMap.from(arg).orElseThrow(IllegalArgumentException::new));
            }
        };
    }

    @Override
    public Stream<String> children() {
        return container.children();
    }

    @Override
    public ComponentAddress getAddress(Component child) {
        return container.getAddress(child);
    }

    @Override
    public Component getChild(String id) {
        return container.getChild(id);
    }

    @Override
    public ComponentInfo getInfo() {
        var base = super.getInfo();
        if (baseInfo != base || info == null) {
            baseInfo = base;
            if (proxies.isEmpty()) {
                info = base;
            } else {
                var builder = Info.component()
                        .merge(base);
                var fallback = false;
                for (PortProxy proxy : proxies.values()) {
                    var portInfo = proxy.getInfo();
                    if (portInfo == FALLBACK_PORT_INFO && getChild(proxy.childID) != null) {
                        fallback = true;
                    }
                    builder.port(proxy.id, portInfo);
                }
                if (fallback) {
                    info = null;
                    return builder.build();
                } else {
                    info = builder.build();
                }
            }
        }
        return info;
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
    public Lookup getLookup() {
        return super.getLookup();
    }

    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        container.hierarchyChanged();
    }

    Control getContainerControl(String id) {
        return container.getControl(id);
    }

    void setPortMap(PMap ports) throws Exception {
        if (portMap.equals(ports)) {
            return;
        }
        List<PortProxy> lst = new ArrayList<>();
        List<String> newPortNames = ports.keys();
        for (String key : ports.keys()) {
            if (!PortAddress.isValidID(key)) {
                throw new IllegalArgumentException("" + key + " : is not a valid port ID");
            }
            String s = ports.get(key).toString();
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
        for (String id : portMap.keys()) {
            if (!newPortNames.contains(id)) {
                Port p = getPort(id);
                p.disconnectAll();
            }
        }
        proxies.clear();
        for (PortProxy p : lst) {
            proxies.put(p.id, p);
        }
        portMap = ports;
        info = null;
    }

    PMap getPortMap() {
        return portMap;
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


    public static class Context<D extends CodeContainerDelegate> extends CodeContext<D> {

        private final boolean hasPortProxies;
        
        public Context(CodeContainer.Connector<D> connector) {
            super(connector);
            hasPortProxies = connector.hasPortProxies;
        }

        @Override
        void setComponent(CodeComponent<D> cmp) {
            super.setComponent(cmp);
            if (!hasPortProxies) {
                try {
                    ((CodeContainer) cmp).setPortMap(PMap.EMPTY);
                } catch (Exception ex) {
                    getLog().log(LogLevel.ERROR, ex);
                }
            }
        }
        
    }

    public static class Connector<D extends CodeContainerDelegate> extends CodeConnector<D> {
        
        private boolean hasPortProxies;

        public Connector(CodeFactory.Task<D> task, D delegate) {
            super(task, delegate);
        }

        @Override
        protected void addDefaultControls() {
            super.addDefaultControls();
            addControl(new ContainerControlDescriptor(ContainerProtocol.ADD_CHILD,
                    ContainerProtocol.ADD_CHILD_INFO, getInternalIndex()));
            addControl(new ContainerControlDescriptor(ContainerProtocol.REMOVE_CHILD,
                    ContainerProtocol.REMOVE_CHILD_INFO, getInternalIndex()));
            addControl(new ContainerControlDescriptor(ContainerProtocol.CHILDREN,
                    ContainerProtocol.CHILDREN_INFO, getInternalIndex()));
            addControl(new ContainerControlDescriptor(ContainerProtocol.CONNECT,
                    ContainerProtocol.CONNECT_INFO, getInternalIndex()));
            addControl(new ContainerControlDescriptor(ContainerProtocol.DISCONNECT,
                    ContainerProtocol.DISCONNECT_INFO, getInternalIndex()));
            addControl(new ContainerControlDescriptor(ContainerProtocol.CONNECTIONS,
                    ContainerProtocol.CONNECTIONS_INFO, getInternalIndex()));
        }

        @Override
        protected void buildBaseComponentInfo(Info.ComponentInfoBuilder cmp) {
            super.buildBaseComponentInfo(cmp);
            cmp.merge(ContainerProtocol.API_INFO);
        }

        @Override
        protected void analyseMethod(Method method) {
            super.analyseMethod(method);
            var proxy = method.getAnnotation(CodeContainerDelegate.ProxyPorts.class);
            if (proxy != null && !hasPortProxies) {
                addControl(new PortProxiesControlDescriptor("ports", getInternalIndex()));
                hasPortProxies = true;
            }
        }

    }
    
    public static abstract class Factory<D extends CodeContainerDelegate> extends CodeFactory<D> {

        public Factory(ClassBodyContext<D> cbc, ComponentType type, Class<? extends D> defaultCls, String template) {
            super(cbc, type, defaultCls, template);
        }

        @Override
        public abstract FactoryTask<D> task();
        
    }
    
    public static abstract class FactoryTask<D extends CodeContainerDelegate> extends CodeFactory.Task<D> {
        
        public FactoryTask(Factory<D> factory) {
            super(factory);
        }

        @Override
        public CodeComponent<D> createComponent(D delegate) {
            CodeContainer<D> cmp = new CodeContainer<>();
            cmp.install(createContext(delegate));
            return cmp;
        }

    }

    private static class ContainerImpl extends AbstractContainer.Delegate {

        private final CodeContainer<?> wrapper;

        private ContainerImpl(CodeContainer<?> wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public ComponentInfo getInfo() {
            return wrapper.getInfo();
        }

        @Override
        public Lookup getLookup() {
            return wrapper.getLookup();
        }

        @Override
        protected ComponentAddress getAddress() {
            return wrapper.getAddress();
        }

        @Override
        protected void addChild(String id, Component child) throws VetoException {
            super.addChild(id, child);
            wrapper.info = null;
        }

        @Override
        protected Component removeChild(String id) {
            wrapper.info = null;
            return super.removeChild(id);
        }

        @Override
        protected void notifyChild(Component child) throws VetoException {
            child.parentNotify(wrapper);
        }

    }

    private static class ContainerControlDescriptor extends ControlDescriptor {

        private final ControlInfo info;

        private Control control;

        ContainerControlDescriptor(String id, ControlInfo info, int index) {
            super(id, Category.Internal, index);
            this.info = info;
        }

        @Override
        public void attach(CodeContext<?> context, Control previous) {
            control = ((CodeContainer) context.getComponent()).getContainerControl(getID());
        }

        @Override
        public Control getControl() {
            return control;
        }

        @Override
        public ControlInfo getInfo() {
            return info;
        }

    }

    private static class PortProxiesControlDescriptor extends ControlDescriptor {

        private final ControlInfo info;

        private Control control;
        
        PortProxiesControlDescriptor(String id, int index) {
            super(id, Category.Internal, index);
            info = Info.control().property()
                    .input(PMap.class).defaultValue(PMap.EMPTY).build();
        }

        @Override
        public void attach(CodeContext<?> context, Control previous) {
            control = ((CodeContainer) context.getComponent()).proxyProperty;
        }

        @Override
        public Control getControl() {
            return control;
        }

        @Override
        public ControlInfo getInfo() {
            return info;
        }        

    }

}
