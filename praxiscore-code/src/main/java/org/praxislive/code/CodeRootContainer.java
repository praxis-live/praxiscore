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
package org.praxislive.code;

import java.util.stream.Stream;
import org.praxislive.base.AbstractContainer;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ContainerProtocol;

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

    CodeRootContainer() {
        container = new ContainerImpl(this);
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
    public void hierarchyChanged() {
        super.hierarchyChanged();
        container.hierarchyChanged();
    }

    Control getContainerControl(String id) {
        return container.getControl(id);
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

    /**
     * CodeContext subclass for CodeRootContainers.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Context<D extends CodeRootContainerDelegate> extends CodeRoot.Context<D> {

        public Context(CodeConnector<D> connector) {
            super(connector);
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

    }

    /**
     * CodeConnector subclass for CodeRootContainers.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Connector<D extends CodeRootContainerDelegate> extends CodeRoot.Connector<D> {

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

    }

    private static class ContainerImpl extends AbstractContainer.Delegate {

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
        protected void notifyChild(Component child) throws VetoException {
            child.parentNotify(wrapper);
        }

    }

    private static class ContainerControlDescriptor extends ControlDescriptor {

        private final ControlInfo info;

        private Control control;

        ContainerControlDescriptor(String id, ControlInfo info, int index) {
            super(id, ControlDescriptor.Category.Internal, index);
            this.info = info;
        }

        @Override
        public void attach(CodeContext<?> context, Control previous) {
            control = ((CodeRootContainer<?>) context.getComponent())
                    .getContainerControl(getID());
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
