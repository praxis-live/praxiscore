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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.praxislive.base.AbstractRoot;
import org.praxislive.base.MapTreeWriter;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.SerializableProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;

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

    private static final String SHARED_CODE = "shared-code";

    private final RootImpl root;
    private final Control startControl;
    private final Control stopControl;
    private final Control isRunningControl;
    private final Control serializeControl;
    private final SharedCodeProperty sharedCode;

    private Lookup lookup;

    CodeRoot() {
        root = new RootImpl(this);
        startControl = (call, router) -> {
            if (call.isRequest()) {
                root.start();
                router.route(call.reply());
            }
        };
        stopControl = (call, router) -> {
            if (call.isRequest()) {
                root.stop();
                router.route(call.reply());
            }
        };
        isRunningControl = (call, router) -> {
            if (call.isRequest()) {
                router.route(call.reply(PBoolean.of(root.isRunning())));
            }
        };
        serializeControl = (call, router) -> {
            if (call.isRequest()) {
                PMap config = call.args().isEmpty() ? PMap.EMPTY
                        : PMap.from(call.args().get(0)).orElseThrow(IllegalArgumentException::new);
                PMap response = serialize(config);
                router.route(call.reply(response));
            }
        };
        sharedCode = new SharedCodeProperty(this, log -> {
            Context<?> ctxt = getCodeContext();
            if (ctxt != null) {
                ctxt.log(log);
                ctxt.flush();
            }
        });
    }

    @Override
    public Controller initialize(String ID, RootHub hub) {
        var controller = root.initialize(ID, hub);
        hierarchyChanged();
        return controller;
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(root.getLookup(), sharedCode.getSharedCodeContext());
        }
        return lookup;
    }

    @Override
    public void parentNotify(Container parent) throws VetoException {
        throw new VetoException();
    }

    @Override
    public void hierarchyChanged() {
        lookup = null;
        super.hierarchyChanged();
    }

    @Override
    ComponentAddress getAddress() {
        return root.address();
    }

    Control findControl(ControlAddress address) {
        if (address.component().depth() == 1) {
            return getControl(address.controlID());
        } else {
            return null;
        }
    }

    @Override
    Context<D> getCodeContext() {
        return (Context<D>) super.getCodeContext();
    }

    @Override
    void install(CodeContext<D> cc) {
        if (cc instanceof Context<D> pending) {
            if (root.isRunning()) {
                Context<D> existing = getCodeContext();
                boolean compatible = (existing.driverDesc == null && pending.driverDesc == null)
                        || (existing.driverDesc != null && pending.driverDesc != null
                        && existing.driverDesc.isCompatible(pending.driverDesc));
                if (!compatible) {
                    throw new IllegalStateException("Stop root to change @Driver configuration");
                }
            }
            super.install(cc);
        } else {
            throw new IllegalArgumentException();
        }
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

    PMap serialize(PMap config) {
        if (!config.isEmpty()) {
            throw new IllegalArgumentException();
        }
        var writer = new MapTreeWriter();
        write(writer);
        return writer.build();
    }

    /**
     * CodeContext subclass for CodeRoots.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Context<D extends CodeRootDelegate> extends CodeContext<D> {

        private DriverDescriptor driverDesc;

        public Context(Connector<D> connector) {
            super(connector);
            this.driverDesc = connector.driverDesc;
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

        @Override
        protected void onStart() {
            if (driverDesc != null) {
                getComponent().root.installDelegate(driverDesc);
            }
        }

        @Override
        protected void onStop() {
            getComponent().root.uninstallDelegate();
        }

    }

    /**
     * CodeConnector subclass for CodeRoots.
     *
     * @param <D> wrapped delegate base type
     */
    public static class Connector<D extends CodeRootDelegate> extends CodeConnector<D> {

        private DriverDescriptor driverDesc;

        public Connector(CodeFactory.Task<D> task, D delegate) {
            super(task, delegate);
        }

        @Override
        protected void addDefaultControls() {
            addControl(createInfoControl(getInternalIndex()));
            addControl(createMetaControl(getInternalIndex()));
            addControl(createMetaMergeControl(getInternalIndex()));
            addControl(sharedCodeControl());
            addControl(createCodeControl(getInternalIndex()));
            addControl(new ResponseHandler(getInternalIndex()));
            addControl(new WrapperControlDescriptor(StartableProtocol.START,
                    StartableProtocol.START_INFO,
                    getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().startControl : null
            ));
            addControl(new WrapperControlDescriptor(StartableProtocol.STOP,
                    StartableProtocol.STOP_INFO,
                    getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().stopControl : null
            ));
            addControl(new WrapperControlDescriptor(StartableProtocol.IS_RUNNING,
                    StartableProtocol.IS_RUNNING_INFO,
                    getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().isRunningControl : null
            ));
            addControl(new WrapperControlDescriptor(SerializableProtocol.SERIALIZE,
                    SerializableProtocol.SERIALIZE_INFO,
                    getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().serializeControl : null
            ));
        }

        @Override
        protected void buildBaseComponentInfo(Info.ComponentInfoBuilder cmp) {
            super.buildBaseComponentInfo(cmp);
            cmp.merge(StartableProtocol.API_INFO);
            cmp.merge(SerializableProtocol.API_INFO);
        }

        @Override
        public void addPort(PortDescriptor port) {
            getLog().log(LogLevel.ERROR, "Cannot add port to root component");
        }

        @Override
        public boolean shouldAddPort(AnnotatedElement element) {
            return false;
        }

        @Override
        protected void analyseField(Field field) {
            CodeRootDelegate.Driver driver = field.getAnnotation(CodeRootDelegate.Driver.class);
            if (driver != null && analyseDriverField(driver, field)) {
                return;
            }
            super.analyseField(field);
        }

        private boolean analyseDriverField(CodeRootDelegate.Driver ann, Field field) {
            if (driverDesc != null) {
                getLog().log(LogLevel.ERROR, "Cannot specify more than one @Driver field");
            } else {
                driverDesc = DriverDescriptor.create(this, ann, field);
                if (driverDesc != null) {
                    addReference(driverDesc);
                }
            }

            return true;
        }

        private ControlDescriptor<?> sharedCodeControl() {
            return new WrapperControlDescriptor(SHARED_CODE,
                    SharedCodeProperty.INFO,
                    getInternalIndex(),
                    ctxt -> ctxt instanceof Context c ? c.getComponent().sharedCode : null,
                    (ctxt, writer) -> {
                        if (ctxt instanceof Context c) {
                            PMap value = c.getComponent().sharedCode.getValue();
                            if (!value.isEmpty()) {
                                writer.writeProperty(SHARED_CODE, value);
                            }
                        }
                    }
            );
        }

    }

    private static class RootImpl extends AbstractRoot {

        private final CodeRoot<?> wrapper;

        private DelegateImpl rootDelegate;

        private RootImpl(CodeRoot<?> wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        protected void processCall(Call call, PacketRouter router) {
            wrapper.processCall(call, router);
        }

        private ComponentAddress address() {
            return getAddress();
        }

        private void driverUpdate() {
            if (rootDelegate != null) {
                rootDelegate.handleUpdate(getRootHub().getClock().getTime());
            }
        }

        private void installDelegate(DriverDescriptor driverDesc) {
            rootDelegate = new DelegateImpl();
            attachDelegate(rootDelegate);
        }

        private void uninstallDelegate() {
            if (rootDelegate != null) {
                detachDelegate(rootDelegate);
                rootDelegate = null;
            }
        }

        private boolean isRunning() {
            return getState() == State.ACTIVE_RUNNING;
        }

        private void start() {
            setRunning();
        }

        private void stop() {
            setIdle();
        }

        private class DelegateImpl extends Delegate {

            private DelegateImpl() {
                super(delegateConfig()
                        .pollInBackground()
                        .forceUpdateAfter(1, TimeUnit.SECONDS)
                );
            }

            private void handleUpdate(long time) {
                doUpdate(time);
            }

        }

    }

    private static class DriverDescriptor extends ReferenceDescriptor<DriverDescriptor>
            implements ProxyContext.Handler {

        private final Field field;
        private final Object driver;

        private CodeRoot.Context<?> context;
        private DriverDescriptor next;

        private DriverDescriptor(Field field, Object delegate) {
            super(DriverDescriptor.class, field.getName());
            this.field = field;
            this.driver = delegate;
        }

        @Override
        public void attach(CodeContext<?> context, DriverDescriptor previous) {
            this.context = (Context<?>) context;
            if (previous != null) {
                if (!field.getType().equals(previous.field.getType())) {
                    previous.dispose();
                }
                previous.next = this;
            }
            try {
                var proxy = context.getComponent().getProxyContext()
                        .wrap(field.getType(), field.getName(), this, true);
                field.set(context.getDelegate(), proxy);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
        }

        @Override
        public void dispose() {
            if (context != null) {
                context.getComponent().getProxyContext().clear(field.getType(), field.getName());
            }
            context = null;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // update clock
            context.getComponent().root.driverUpdate();
            // update might replace us - pass down the chain!
            var active = this;
            if (next != null) {
                active = next;
                while (active.next != null) {
                    active = active.next;
                }
            }
            if (active.context != null && active.context.checkActive()) {
                return method.invoke(active.driver, args);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private boolean isCompatible(DriverDescriptor other) {
            return other.field.getName().equals(field.getName())
                    && other.field.getGenericType().equals(field.getGenericType());
        }

        private static DriverDescriptor create(CodeRoot.Connector<?> connector,
                CodeRootDelegate.Driver ann, Field field) {
            if (!field.getType().isInterface()) {
                connector.getLog().log(LogLevel.ERROR,
                        "@Driver annotated field " + field.getName() + " is not an interface type.");
                return null;
            }
            try {
                field.setAccessible(true);
                Object driver = field.get(connector.getDelegate());
                field.set(connector.getDelegate(), null);
                return new DriverDescriptor(field, driver);
            } catch (Exception ex) {
                connector.getLog().log(LogLevel.ERROR, ex,
                        "Cannot access @Driver annotated field " + field.getName());
                return null;
            }
        }

    }

}
