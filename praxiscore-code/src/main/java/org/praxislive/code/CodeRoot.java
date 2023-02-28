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

import java.lang.reflect.AnnotatedElement;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PError;

/**
 * A {@link Root} component instance that is rewritable at runtime. The CodeRoot
 * itself remains constant, but passes most responsibility to a {@link Context}
 * wrapping a {@link CodeRootDelegate} (user code). This component handles
 * switching from one context to the next. A CodeRoot cannot be created directly
 * - see {@link CodeFactory}.
 *
 * @param <D> wrapped delegate type
 */
public class CodeRoot<D extends CodeRootDelegate> extends CodeComponent<D> implements Root {

    private final RootImpl root;
    private final Control startControl;
    private final Control stopControl;
    private final Control isRunningControl;

    CodeRoot() {
        root = new RootImpl(this);
        startControl = (call, router) -> {
            root.start();
            router.route(call.reply());
        };
        stopControl = (call, router) -> {
            root.stop();
            router.route(call.reply());
        };
        isRunningControl = (call, router) -> {
            router.route(call.reply(PBoolean.of(root.isRunning())));
        };
    }

    @Override
    public Controller initialize(String ID, RootHub hub) {
        var controller = root.initialize(ID, hub);
        hierarchyChanged();
        return controller;
    }

    @Override
    public Lookup getLookup() {
        return root.getLookup();
    }

    @Override
    public void parentNotify(Container parent) throws VetoException {
        throw new VetoException();
    }

    @Override
    ComponentAddress getAddress() {
        return root.address();
    }

    void processCall(Call call, PacketRouter router) {
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

    Control findControl(ControlAddress address) {
        if (address.component().depth() == 1) {
            return getControl(address.controlID());
        } else {
            return null;
        }
    }

    /**
     * CodeContext subclass for CodeRoots.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Context<D extends CodeRootDelegate> extends CodeContext<D> {

        public Context(CodeConnector<D> connector) {
            super(connector);
        }

        @Override
        void setComponent(CodeComponent<D> cmp) {
            setComponentImpl((CodeRoot<D>) cmp);
        }

        private void setComponentImpl(CodeRoot<D> cmp) {
            super.setComponent(cmp);
        }

        @Override
        public CodeRoot<D> getComponent() {
            return (CodeRoot<D>) super.getComponent();
        }

    }

    /**
     * CodeConnector subclass for CodeRoots.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Connector<D extends CodeRootDelegate> extends CodeConnector<D> {

        public Connector(CodeFactory.Task<D> task, D delegate) {
            super(task, delegate);
        }

        @Override
        protected void addDefaultControls() {
            super.addDefaultControls();
            addControl(new RootControlDescriptor(StartableProtocol.START, getInternalIndex()));
            addControl(new RootControlDescriptor(StartableProtocol.STOP, getInternalIndex()));
            addControl(new RootControlDescriptor(StartableProtocol.IS_RUNNING, getInternalIndex()));
        }

        @Override
        protected void buildBaseComponentInfo(Info.ComponentInfoBuilder cmp) {
            super.buildBaseComponentInfo(cmp);
            cmp.merge(StartableProtocol.API_INFO);
        }

        @Override
        public void addPort(PortDescriptor port) {
            getLog().log(LogLevel.ERROR, "Cannot add port to root component");
        }

        @Override
        public boolean shouldAddPort(AnnotatedElement element) {
            return false;
        }

    }

    private static class RootImpl extends AbstractRoot {

        private final CodeRoot<?> wrapper;

        private RootImpl(CodeRoot<?> wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        protected void processCall(Call call, PacketRouter router) {
            wrapper.processCall(call, router);
        }

        private void start() {
            setRunning();
        }

        private void stop() {
            setIdle();
        }

        private boolean isRunning() {
            return getState() == State.ACTIVE_RUNNING;
        }

        private ComponentAddress address() {
            return getAddress();
        }

    }

    private static class RootControlDescriptor extends ControlDescriptor {

        private final String controlID;

        private CodeRoot<?> root;

        private RootControlDescriptor(String controlID, int index) {
            super(controlID, Category.Internal, index);
            this.controlID = controlID;
        }

        @Override
        public void attach(CodeContext<?> context, Control previous) {
            root = (CodeRoot<?>) context.getComponent();
        }

        @Override
        public Control getControl() {
            switch (controlID) {
                case StartableProtocol.START:
                    return root.startControl;
                case StartableProtocol.STOP:
                    return root.stopControl;
                case StartableProtocol.IS_RUNNING:
                    return root.isRunningControl;
            }
            return null;
        }

        @Override
        public ControlInfo getInfo() {
            switch (controlID) {
                case StartableProtocol.START:
                    return StartableProtocol.START_INFO;
                case StartableProtocol.STOP:
                    return StartableProtocol.STOP_INFO;
                case StartableProtocol.IS_RUNNING:
                    return StartableProtocol.IS_RUNNING_INFO;
            }
            return null;
        }

    }

}
