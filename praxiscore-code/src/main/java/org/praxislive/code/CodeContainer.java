/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2024 Neil C Smith.
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
import org.praxislive.code.CodeContainerSupport.ChildControl;
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
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PMap;

/**
 * A CodeContainer is a Container instance that is rewritable at runtime. The
 * CodeContainer itself remains constant, but passes most responsibility to a
 * {@link CodeContainer.Context} wrapping a {@link CodeContainerDelegate} (user
 * code). This component handles switching from one context to the next. A
 * CodeComponent cannot be created directly - see {@link CodeFactory}.
 * <p>
 * The CodeContainer supports a property for proxying ports of child components
 * on to the parent. This is automatically added when the
 * {@link CodeContainerDelegate.ProxyPorts} annotation is used on the
 * <code>init()</code> method of the delegate.
 *
 * @param <D> wrapped delegate base type
 */
public class CodeContainer<D extends CodeContainerDelegate> extends CodeComponent<D>
        implements Container {

    private final static PortInfo FALLBACK_PORT_INFO
            = PortInfo.create(ControlPort.class, PortInfo.Direction.BIDI, PMap.EMPTY);

    private final ContainerImpl container;
    private final ChildControl childControl;
    private final Map<String, PortProxy> proxies;
    private final Control proxyProperty;
    private final FilteredTypes filteredTypes;

    private Lookup lookup;
    private PMap portMap;
    private ComponentInfo baseInfo;
    private ComponentInfo info;
    private RefBus refBus;

    CodeContainer() {
        container = new ContainerImpl(this);
        childControl = new ChildControl(this, container::addChild, container::recordChildType);
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
        filteredTypes = FilteredTypes.create(this,
                t -> childControl.supportedSystemType(t),
                () -> childControl.additionalTypes(),
                false);
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
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), filteredTypes);
        }
        return lookup;
    }

    @Override
    public void hierarchyChanged() {
        lookup = null;
        filteredTypes.reset();
        super.hierarchyChanged();
        container.hierarchyChanged();
    }

    @Override
    public void write(TreeWriter writer) {
        super.write(writer);
        if (!portMap.isEmpty()) {
            writer.writeProperty("ports", portMap);
        }
        container.write(writer);
    }

    @Override
    Context<D> getCodeContext() {
        return (Context<D>) super.getCodeContext();
    }

    @Override
    void install(CodeContext<D> cc) {
        if (cc instanceof Context<D> pending) {
            if (!childControl.isCompatible(pending.typesInfo)) {
                throw new IllegalStateException("Supported types is not compatible");
            }
            super.install(cc);
            childControl.install(pending.typesInfo);
            filteredTypes.reset();
        } else {
            throw new IllegalArgumentException();
        }
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

    RefBus getRefBus() {
        if (refBus == null) {
            refBus = new RefBus();
        }
        return refBus;
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

    /**
     * CodeContext subclass for CodeContainers.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Context<D extends CodeContainerDelegate> extends CodeContext<D> {

        private final boolean hasPortProxies;
        private final CodeContainerSupport.TypesInfo typesInfo;

        public Context(CodeContainer.Connector<D> connector) {
            super(connector);
            hasPortProxies = connector.hasPortProxies;
            typesInfo = connector.typesInfo == null
                    ? CodeContainerSupport.defaultContainerTypesInfo()
                    : connector.typesInfo;
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

        @Override
        public CodeContainer<D> getComponent() {
            return (CodeContainer<D>) super.getComponent();
        }

    }

    /**
     * CodeConnector subclass for CodeContainers.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Connector<D extends CodeContainerDelegate> extends CodeConnector<D> {

        private boolean hasPortProxies;
        private CodeContainerSupport.TypesInfo typesInfo;

        public Connector(CodeFactory.Task<D> task, D delegate) {
            super(task, delegate);
        }

        @Override
        protected void addDefaultControls() {
            super.addDefaultControls();
            addControl(new WrapperControlDescriptor(
                    ContainerProtocol.ADD_CHILD,
                    ContainerProtocol.ADD_CHILD_INFO,
                    getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().childControl : null));
            addControl(containerControl(ContainerProtocol.REMOVE_CHILD,
                    ContainerProtocol.REMOVE_CHILD_INFO));
            addControl(containerControl(ContainerProtocol.CHILDREN,
                    ContainerProtocol.CHILDREN_INFO));
            addControl(containerControl(ContainerProtocol.CONNECT,
                    ContainerProtocol.CONNECT_INFO));
            addControl(containerControl(ContainerProtocol.DISCONNECT,
                    ContainerProtocol.DISCONNECT_INFO));
            addControl(containerControl(ContainerProtocol.CONNECTIONS,
                    ContainerProtocol.CONNECTIONS_INFO));
            addControl(containerControl(ContainerProtocol.SUPPORTED_TYPES,
                    ContainerProtocol.SUPPORTED_TYPES_INFO));
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
            if (typesInfo == null) {
                typesInfo = CodeContainerSupport.analyseMethod(method, true);
            }
        }

        private ControlDescriptor<?> containerControl(String id, ControlInfo info) {
            return new WrapperControlDescriptor(id, info, getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().getContainerControl(id) : null
            );
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
        protected void recordChildType(Component child, ComponentType type) {
            super.recordChildType(child, type);
        }

        @Override
        protected Component removeChild(String id) {
            wrapper.info = null;
            Component child = super.removeChild(id);
            if (child != null) {
                wrapper.childControl.notifyChildRemoved(id);
            }
            return child;
        }

        @Override
        protected void notifyChild(Component child) throws VetoException {
            child.parentNotify(wrapper);
        }

        @Override
        public void write(TreeWriter writer) {
            writeChildren(writer);
            writeConnections(writer);
        }

    }

    private static class PortProxiesControlDescriptor
            extends ControlDescriptor<PortProxiesControlDescriptor> {

        private final ControlInfo info;

        private Control control;

        PortProxiesControlDescriptor(String id, int index) {
            super(PortProxiesControlDescriptor.class, id, Category.Internal, index);
            info = Info.control().property()
                    .input(PMap.class).defaultValue(PMap.EMPTY).build();
        }

        @Override
        public void attach(CodeContext<?> context, PortProxiesControlDescriptor previous) {
            control = ((CodeContainer) context.getComponent()).proxyProperty;
        }

        @Override
        public Control control() {
            return control;
        }

        @Override
        public ControlInfo controlInfo() {
            return info;
        }

    }

}
