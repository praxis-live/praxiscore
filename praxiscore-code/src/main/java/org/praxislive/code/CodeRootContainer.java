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
package org.praxislive.code;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;
import org.praxislive.base.AbstractContainer;
import org.praxislive.base.FilteredTypes;
import org.praxislive.base.MapTreeWriter;
import org.praxislive.code.CodeContainerSupport.AddChildControl;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.SerializableProtocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 * A {@link Root} container instance that is rewritable at runtime. The
 * CodeRootContainer itself remains constant, but passes most responsibility to
 * a {@link Context} wrapping a {@link CodeRootContainerDelegate} (user code).
 * This component handles switching from one context to the next. A CodeRoot
 * cannot be created directly - see {@link CodeFactory}.
 *
 * @param <D> wrapped delegate type
 */
public class CodeRootContainer<D extends CodeRootContainerDelegate> extends CodeRoot<D>
        implements Container {

    private final ContainerImpl container;
    private final AddChildControl addChildControl;
    private final Control removeChildControl;
    private final FilteredTypes filteredTypes;

    private Lookup lookup;
    private RefBus refBus;

    CodeRootContainer() {
        container = new ContainerImpl(this);
        addChildControl = new AddChildControl(this, container);
        removeChildControl = (call, router) -> {
            if (call.isRequest()) {
                container.removeChild(call.args().get(0).toString());
                notifyChildrenChanged(call.time());
                router.route(call.reply());
            }
        };
        filteredTypes = FilteredTypes.create(this,
                t -> addChildControl.supportedSystemType(t),
                () -> addChildControl.additionalTypes());
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
        container.write(writer);
    }

    @Override
    Context<D> getCodeContext() {
        return (Context<D>) super.getCodeContext();
    }

    @Override
    void install(CodeContext<D> cc) {
        if (cc instanceof Context<D> pending) {
            if (!addChildControl.isCompatible(pending.typesInfo)) {
                throw new IllegalStateException("Supported types is not compatible");
            }
            super.install(cc);
            addChildControl.install(pending.typesInfo);
            filteredTypes.reset();
        } else {
            throw new IllegalArgumentException();
        }
    }

    Control getContainerControl(String id) {
        return container.getControl(id);
    }

    RefBus getRefBus() {
        if (refBus == null) {
            refBus = new RefBus();
        }
        return refBus;
    }

    @Override
    PMap serialize(PMap configuration) {
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

    @Override
    Control findControl(ControlAddress address) {
        Component comp = findComponent(address.component());
        if (comp != null) {
            return comp.getControl(address.controlID());
        } else {
            return null;
        }
    }

    private Component findComponent(ComponentAddress address) {
        Component comp = this;
        for (int i = 1; i < address.depth(); i++) {
            if (comp instanceof Container) {
                comp = ((Container) comp).getChild(address.componentID(i));
            } else {
                return null;
            }
        }
        return comp;
    }

    private void notifyChildrenChanged(long time) {
        Context<?> ctx = getCodeContext();
        ctx.invoke(time, ctx::onChildrenChanged);
    }

    /**
     * CodeContext subclass for CodeRootContainers.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Context<D extends CodeRootContainerDelegate> extends CodeRoot.Context<D> {

        private final CodeContainerSupport.TypesInfo typesInfo;

        public Context(Connector<D> connector) {
            super(connector);
            typesInfo = connector.typesInfo == null
                    ? CodeContainerSupport.defaultRootTypesInfo()
                    : connector.typesInfo;
        }

        @Override
        void setComponent(CodeComponent<D> cmp) {
            setComponentImpl((CodeRootContainer<D>) cmp);
        }

        private void setComponentImpl(CodeRootContainer<D> cmp) {
            super.setComponent(cmp);
        }

        @Override
        public CodeRootContainer<D> getComponent() {
            return (CodeRootContainer<D>) super.getComponent();
        }

        /**
         * Hook called when the container children have changed.
         */
        protected void onChildrenChanged() {

        }

    }

    /**
     * CodeConnector subclass for CodeRootContainers.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Connector<D extends CodeRootContainerDelegate> extends CodeRoot.Connector<D> {

        private CodeContainerSupport.TypesInfo typesInfo;
        private PMap displayHint;

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
                    ctxt -> ctxt instanceof Context c ? c.getComponent().addChildControl : null));
            addControl(new WrapperControlDescriptor(
                    ContainerProtocol.REMOVE_CHILD,
                    ContainerProtocol.REMOVE_CHILD_INFO,
                    getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().removeChildControl : null));
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
        protected void analyseMethod(Method method) {
            super.analyseMethod(method);
            if (typesInfo == null) {
                typesInfo = CodeContainerSupport.analyseMethod(method, true);
            }
            var hint = method.getAnnotation(CodeRootContainerDelegate.DisplayTable.class);
            if (hint != null) {
                displayHint = PMap.of(
                        "type", "table",
                        "properties", Stream.of(hint.properties())
                                .map(PString::of)
                                .collect(PArray.collector())
                );
            }
        }

        @Override
        protected void buildBaseComponentInfo(Info.ComponentInfoBuilder cmp) {
            super.buildBaseComponentInfo(cmp);
            cmp.merge(ContainerProtocol.API_INFO);
            if (displayHint != null) {
                cmp.property(ComponentInfo.KEY_DISPLAY_HINT, displayHint);
            }
        }

        private ControlDescriptor containerControl(String id, ControlInfo info) {
            return new WrapperControlDescriptor(id, info, getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().getContainerControl(id) : null
            );
        }

    }

    private static class ContainerImpl extends AbstractContainer.Delegate
            implements CodeContainerSupport.AddChildCallbacks {

        private final CodeRootContainer<?> wrapper;

        private ContainerImpl(CodeRootContainer<?> wrapper) {
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
        public void addChild(String id, Component child) throws VetoException {
            super.addChild(id, child);
        }

        @Override
        public void recordChildType(Component child, ComponentType type) {
            super.recordChildType(child, type);
        }

        @Override
        public void notifyChildAdded(String id, long time) {
            wrapper.notifyChildrenChanged(time);
        }

        @Override
        protected Component removeChild(String id) {
            Component child = super.removeChild(id);
            if (child != null) {
                wrapper.addChildControl.notifyChildRemoved(id);
            }
            return child;
        }

        @Override
        protected void notifyChild(Component child) throws VetoException {
            child.parentNotify(wrapper);
        }

    }

}
