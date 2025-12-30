/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2025 Neil C Smith.
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Port;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PMap;

/**
 * Abstract base implementation of {@link Component} supporting {@link Control}
 * and {@link Port} management. A {@link ComponentProtocol#INFO} control is
 * added automatically, returning the information provided by the abstract
 * {@link #getInfo()} method.
 */
public abstract class AbstractComponent implements Component {

    private final Map<String, Control> controls;
    private final Map<String, Port> ports;
    private final MetaProperty meta;

    private Container parent;

    protected AbstractComponent() {
        controls = new LinkedHashMap<>();
        controls.put(ComponentProtocol.INFO, new InfoControl());
        meta = new MetaProperty();
        controls.put(ComponentProtocol.META, meta);
        controls.put(ComponentProtocol.META_MERGE, meta.getMergeControl());
        ports = new LinkedHashMap<>();
    }

    @Override
    public Container getParent() {
        return parent;
    }

    @Override
    public void parentNotify(Container parent) throws VetoException {
        if (parent == null) {
            if (this.parent != null) {
                this.parent = null;
                disconnectAll();
            }
        } else {
            if (this.parent != null) {
                throw new VetoException();
            }
            this.parent = parent;
        }
    }

    @Override
    public void hierarchyChanged() {
    }

    @Override
    public Control getControl(String id) {
        return controls.get(id);
    }

    @Override
    public Port getPort(String id) {
        return ports.get(id);
    }

    @Override
    public void write(TreeWriter writer) {
        writeTypeAndInfo(writer);
        writeMeta(writer);
    }

    /**
     * Write component type and info to {@link TreeWriter}.
     *
     * @param writer tree writer
     */
    protected final void writeTypeAndInfo(TreeWriter writer) {
        ComponentType type;
        if (parent == null) {
            // assume we're a root?!
            type = Optional.ofNullable(getInfo())
                    .map(info -> info.properties().get(ComponentInfo.KEY_COMPONENT_TYPE))
                    .flatMap(ComponentType::from)
                    .orElse(null);
        } else {
            type = parent.getType(this);
        }
        if (type != null) {
            writer.writeType(type);
        }
        ComponentInfo info = getInfo();
        if (info != null) {
            writer.writeInfo(info);
        }
    }

    /**
     * Write the {@link ComponentProtocol#META} property to the
     * {@link TreeWriter}.
     *
     * @param writer tree writer
     */
    protected final void writeMeta(TreeWriter writer) {
        PMap value = meta.getValue();
        if (!value.isEmpty() && getInfo().controls().contains(ComponentProtocol.META)) {
            writer.writeProperty(ComponentProtocol.META, value);
        }
    }

    /**
     * Get the address of this component, if installed in a container.
     *
     * @return address or null
     */
    protected ComponentAddress getAddress() {
        if (parent != null) {
            return parent.getAddress(this);
        } else {
            return null;
        }
    }

    /**
     * Get the {@link Lookup} for this component.
     *
     * @return lookup
     */
    protected Lookup getLookup() {
        return parent == null ? Lookup.EMPTY : parent.getLookup();
    }

    /**
     * Find the address for the provided {@link Service}.
     *
     * @param service class of service
     * @return address of service
     * @throws ServiceUnavailableException if service cannot be found
     */
    protected ComponentAddress findService(Class<? extends Service> service)
            throws ServiceUnavailableException {
        return getLookup().find(Services.class)
                .flatMap(sm -> sm.locate(service))
                .orElseThrow(ServiceUnavailableException::new);
    }

    /**
     * Disconnect all ports.
     */
    protected void disconnectAll() {
        ports.values().forEach(Port::disconnectAll);
    }

    /**
     * Register a control on this component.
     *
     * @param id control ID
     * @param control control implementation
     */
    protected final void registerControl(String id, Control control) {
        if (controls.putIfAbsent(Objects.requireNonNull(id),
                Objects.requireNonNull(control)) != null) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Remove a control from this component.
     *
     * @param id control ID
     */
    protected final void unregisterControl(String id) {
        controls.remove(id);
    }

    /**
     * Register a port on this component.
     *
     * @param id port ID
     * @param port port implementation
     */
    protected final void registerPort(String id, Port port) {
        if (ports.putIfAbsent(Objects.requireNonNull(id),
                Objects.requireNonNull(port)) != null) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Remove a port from this component.
     *
     * @param id port ID
     */
    protected final void unregisterPort(String id) {
        Port port = ports.remove(id);
        if (port != null) {
            port.disconnectAll();
        }
    }

    private class InfoControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isReplyRequired()) {
                router.route(call.reply(getInfo()));
            }
        }

    }

}
