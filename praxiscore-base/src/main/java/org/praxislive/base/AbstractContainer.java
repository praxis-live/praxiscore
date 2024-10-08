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
package org.praxislive.base;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Connection;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Port;
import org.praxislive.core.PortConnectionException;
import org.praxislive.core.PortListener;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.SupportedTypes;
import org.praxislive.core.services.ComponentFactoryService;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;

/**
 * Abstract base class for {@link Container} supporting all controls of
 * {@link ContainerProtocol}.
 * <p>
 * Use the {@link Delegate} subclass to reuse the functionality here inside an
 * alternative Container implementation (see eg. use in
 * {@link AbstractRootContainer} ).
 */
public abstract class AbstractContainer extends AbstractComponent implements Container {

    private final static System.Logger LOG = System.getLogger(AbstractContainer.class.getName());

    private final Map<String, Component> childMap;
    private final Map<Component, ComponentType> childTypeMap;
    private final Set<Connection> connections;

    protected AbstractContainer() {
        childMap = new LinkedHashMap<>();
        childTypeMap = new HashMap<>();
        connections = new LinkedHashSet<>();
        registerControl(ContainerProtocol.ADD_CHILD, new AddChildControl());
        registerControl(ContainerProtocol.REMOVE_CHILD, new RemoveChildControl());
        registerControl(ContainerProtocol.CHILDREN, new ChildrenControl());
        registerControl(ContainerProtocol.CONNECT, new ConnectControl());
        registerControl(ContainerProtocol.DISCONNECT, new DisconnectControl());
        registerControl(ContainerProtocol.CONNECTIONS, new ConnectionsControl());
        registerControl(ContainerProtocol.SUPPORTED_TYPES, (call, router) -> {
            router.route(call.reply(getLookup().find(SupportedTypes.class)
                    .map(types -> types.query().typesAsArray())
                    .orElse(PArray.EMPTY)));
        });
    }

    @Override
    public Component getChild(String id) {
        return childMap.get(id);
    }

    @Override
    public Stream<String> children() {
        return childMap.keySet().stream();
    }

    @Override
    public ComponentAddress getAddress(Component child) {
        ComponentAddress containerAddress = getAddress();
        String childID = getChildID(child);
        if (containerAddress == null || childID == null) {
            return null;
        } else {
            return ComponentAddress.of(containerAddress, childID);
        }
    }

    @Override
    public ComponentType getType(Component child) {
        return childTypeMap.computeIfAbsent(child, Container.super::getType);
    }

    @Override
    public void hierarchyChanged() {
        childMap.values().forEach(Component::hierarchyChanged);
    }

    @Override
    public Lookup getLookup() {
        return super.getLookup();
    }

    @Override
    public void write(TreeWriter writer) {
        super.write(writer);
        writeChildren(writer);
        writeConnections(writer);
    }

    protected final void writeChildren(TreeWriter writer) {
        childMap.forEach((id, child) -> writer.writeChild(id, child::write));
    }

    protected final void writeConnections(TreeWriter writer) {
        connections.forEach(writer::writeConnection);
    }

    protected void addChild(String id, Component child) throws VetoException {
        if (childMap.putIfAbsent(Objects.requireNonNull(id),
                Objects.requireNonNull(child)) != null) {
            throw new VetoException("Child ID already in use");
        }
        try {
            notifyChild(child);
        } catch (VetoException ex) {
            childMap.remove(id);
            throw new VetoException();
        }
        child.hierarchyChanged();
    }

    protected void recordChildType(Component child, ComponentType type) {
        childTypeMap.put(Objects.requireNonNull(child), Objects.requireNonNull(type));
    }

    protected void notifyChild(Component child) throws VetoException {
        child.parentNotify(this);
    }

    protected Component removeChild(String id) {
        Component child = childMap.remove(id);
        if (child != null) {
            try {
                child.parentNotify(null);
            } catch (VetoException ex) {
                // it is an error for children to throw exception on removal
                // should we throw an error?
                LOG.log(System.Logger.Level.ERROR, "Child throwing Veto on removal", ex);
            }
            child.hierarchyChanged();
            childTypeMap.remove(child);
        }
        return child;
    }

    protected String getChildID(Component child) {
        for (Map.Entry<String, Component> entry : childMap.entrySet()) {
            if (entry.getValue() == child) {
                return entry.getKey();
            }
        }
        return null;
    }

    protected void connect(String component1, String port1, String component2, String port2)
            throws PortConnectionException {
        handleConnection(true, component1, port1, component2, port2);
    }

    protected void disconnect(String component1, String port1, String component2, String port2) {
        try {
            handleConnection(false, component1, port1, component2, port2);
        } catch (PortConnectionException ex) {
            LOG.log(System.Logger.Level.ERROR, "", ex);
        }
    }

    private void handleConnection(boolean connect, String component1, String port1, String component2, String port2)
            throws PortConnectionException {
        try {
            Component c1 = getChild(component1);
            final Port p1 = c1.getPort(port1);
            Component c2 = getChild(component2);
            final Port p2 = c2.getPort(port2);

            final Connection connection = Connection.of(component1, port1, component2, port2);

            if (connect) {
                p1.connect(p2);
                connections.add(connection);
                PortListener listener = new ConnectionListener(p1, p2, connection);
                p1.addListener(listener);
                p2.addListener(listener);
            } else {
                p1.disconnect(p2);
                connections.remove(connection);
            }
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.DEBUG, "Can't connect ports.", ex);
            throw new PortConnectionException("Can't connect " + component1 + "!" + port1
                    + " to " + component2 + "!" + port2);
        }
    }

    protected class AddChildControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            List<Value> args = call.args();
            if (args.size() < 2) {
                throw new IllegalArgumentException("Invalid arguments");
            }
            if (!ComponentAddress.isValidID(args.get(0).toString())) {
                throw new IllegalArgumentException("Invalid Component ID");
            }
            ControlAddress to = ControlAddress.of(findService(ComponentFactoryService.class),
                    ComponentFactoryService.NEW_INSTANCE);
            return Call.create(to, call.to(), call.time(), args.get(1));
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            List<Value> args = call.args();
            if (args.size() < 1) {
                throw new IllegalArgumentException("Invalid response");
            }
            Component child = PReference.from(args.get(0))
                    .flatMap(r -> r.as(Component.class))
                    .orElseThrow();
            Call active = getActiveCall();
            String id = active.args().get(0).toString();
            ComponentType type = ComponentType.from(active.args().get(1)).orElse(null);
            addChild(id, child);
            recordChildType(child, type);
            return active.reply();
        }
    }

    protected class RemoveChildControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            removeChild(call.args().get(0).toString());
            router.route(call.reply());
        }

    }

    protected class ChildrenControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            PArray response = childMap.keySet().stream()
                    .map(PString::of)
                    .collect(PArray.collector());
            router.route(call.reply(response));
        }

    }

    protected class ConnectControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            handleConnection(true,
                    call.args().get(0).toString(),
                    call.args().get(1).toString(),
                    call.args().get(2).toString(),
                    call.args().get(3).toString());
            router.route(call.reply());
        }

    }

    protected class DisconnectControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            handleConnection(false,
                    call.args().get(0).toString(),
                    call.args().get(1).toString(),
                    call.args().get(2).toString(),
                    call.args().get(3).toString());
            router.route(call.reply());
        }

    }

    protected class ConnectionsControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            PArray response = PArray.of(connections);
            router.route(call.reply(response));
        }

    }

    private class ConnectionListener implements PortListener {

        Port p1;
        Port p2;
        Connection connection;

        private ConnectionListener(Port p1, Port p2, Connection connection) {
            this.p1 = p1;
            this.p2 = p2;
            this.connection = connection;
        }

        @Override
        public void connectionsChanged(Port source) {
            if (p1.isConnectedTo(p2) && p2.isConnectedTo(p1)) {
            } else {
                LOG.log(System.Logger.Level.TRACE, "Removing connection\n{0}", connection);
                connections.remove(connection);
                p1.removeListener(this);
                p2.removeListener(this);
            }
        }
    }

    /**
     * Delegate base class to be used inside a wrapper class implementing
     * {@link Container}. The abstract methods must be implemented to return the
     * information from the wrapper, or in the case of
     * {@link #notifyChild(org.praxislive.core.Component)} call through to
     * {@link Component#parentNotify(org.praxislive.core.Container)} with the
     * wrapper.
     */
    public static abstract class Delegate extends AbstractContainer {

        @Override
        public abstract Lookup getLookup();

        @Override
        protected abstract ComponentAddress getAddress();

        /**
         * Notify the child of its addition to the container by calling through
         * to {@link Component#parentNotify(org.praxislive.core.Container)} with
         * the wrapper instance.
         *
         * @param child child being notified
         * @throws VetoException if child vetoes being added
         */
        @Override
        protected abstract void notifyChild(Component child) throws VetoException;

    }

}
