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
package org.praxislive.base;

import java.util.Optional;
import java.util.stream.Stream;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Port;
import org.praxislive.core.PortConnectionException;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.SerializableProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;

/**
 *
 */
public abstract class AbstractRootContainer extends AbstractRoot implements Container {

    private final ContainerImpl delegate;

    private Lookup lookup;

    protected AbstractRootContainer() {
        delegate = new ContainerImpl(this);
        registerControl(StartableProtocol.START, (call, router) -> {
            if (call.isRequest()) {
                setRunning();
                router.route(call.reply());
            }
        });
        registerControl(StartableProtocol.STOP, (call, router) -> {
            if (call.isRequest()) {
                setIdle();
                router.route(call.reply());
            }
        });
        registerControl(StartableProtocol.IS_RUNNING, (call, router) -> {
            if (call.isRequest()) {
                router.route(call.reply(PBoolean.of(getState() == State.ACTIVE_RUNNING)));
            }
        });
        registerControl(SerializableProtocol.SERIALIZE, (call, router) -> {
            if (call.isRequest()) {
                PMap config = call.args().isEmpty() ? PMap.EMPTY
                        : PMap.from(call.args().get(0)).orElseThrow(IllegalArgumentException::new);
                PMap response = serialize(config);
                router.route(call.reply(response));
            }
        });
    }

    @Override
    public Component getChild(String id) {
        return delegate.getChild(id);
    }

    @Override
    public Stream<String> children() {
        return delegate.children();
    }

    @Override
    public ComponentAddress getAddress(Component child) {
        return delegate.getAddress(child);
    }

    @Override
    public Container getParent() {
        return null;
    }

    @Override
    public void parentNotify(Container parent) throws VetoException {
        throw new VetoException();
    }

    @Override
    public void hierarchyChanged() {
    }

    @Override
    public Control getControl(String id) {
        return delegate.getControl(id);
    }

    @Override
    public Port getPort(String id) {
        return delegate.getPort(id);
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), FilteredTypes.create(this));
        }
        return lookup;
    }

    @Override
    public abstract ComponentInfo getInfo();

    @Override
    public void write(TreeWriter writer) {
        delegate.write(writer);
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        Control control = findControl(call.to());
        try {
            if (control != null) {
                control.call(call, router);
            } else {
                if (call.isRequest()) {
                    router.route(call.error(PError.of("Unknown control address : " + call.to())));
                }
            }
        } catch (Exception ex) {
            if (call.isRequest()) {
                router.route(call.error(PError.of(ex)));
            }
        }
    }

    protected final void addChild(String id, Component child) throws VetoException {
        delegate.addChild(id, child);
    }

    protected final Component removeChild(String id) {
        return delegate.removeChild(id);
    }

    protected final void connect(String component1, String port1, String component2, String port2) throws PortConnectionException {
        delegate.connect(component1, port1, component2, port2);
    }

    protected final void disconnect(String component1, String port1, String component2, String port2) {
        delegate.disconnect(component1, port1, component2, port2);
    }

    protected final void registerControl(String id, Control control) {
        delegate.registerControl(id, control);
    }

    protected final void unregisterControl(String id) {
        delegate.unregisterControl(id);
    }

    private Control findControl(ControlAddress address) {
        Component comp = findComponent(address.component());
        if (comp != null) {
            return comp.getControl(address.controlID());
        } else {
            return null;
        }
    }

    private Component findComponent(ComponentAddress address) {
        Component comp = delegate;
        for (int i = 1; i < address.depth(); i++) {
            if (comp instanceof Container) {
                comp = ((Container) comp).getChild(address.componentID(i));
            } else {
                return null;
            }
        }
        return comp;
    }

    private PMap serialize(PMap configuration) {
        configuration.keys().forEach(k -> {
            if (!SerializableProtocol.OPTION_SUBTREE.equals(k)) {
                throw new IllegalArgumentException("Unknown configuration key : " + k);
            }
        });
        var subtreeValue = configuration.get(SerializableProtocol.OPTION_SUBTREE);
        Component base;
        if (subtreeValue != null) {
            base = ComponentAddress.from(subtreeValue)
                    .filter(a -> a.rootID().equals(getAddress().rootID()))
                    .flatMap(a -> Optional.ofNullable(findComponent(a)))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid subtree : " + subtreeValue));
        } else {
            base = this;
        }
        var writer = new MapTreeWriter();
        base.write(writer);
        return writer.build();
    }

    private static class ContainerImpl extends AbstractContainer.Delegate {

        private final AbstractRootContainer wrapper;

        private ContainerImpl(AbstractRootContainer wrapper) {
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
        protected void notifyChild(Component child) throws VetoException {
            child.parentNotify(wrapper);
        }

    }

}
