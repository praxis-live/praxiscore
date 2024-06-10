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

import org.praxislive.base.MetaProperty;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Container;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ExecutionContext;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Port;
import org.praxislive.core.VetoException;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.ThreadContext;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.services.Services;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.LogService;
import org.praxislive.core.types.PMap;

/**
 * A CodeComponent is a Component instance that is rewritable at runtime. The
 * CodeComponent itself remains constant, but passes most responsibility to a
 * {@link CodeContext} wrapping a {@link CodeDelegate} (user code). This
 * component handles switching from one context to the next. A CodeComponent
 * cannot be created directly - see {@link CodeFactory}.
 *
 * @param <D> wrapped delegate type
 */
public class CodeComponent<D extends CodeDelegate> implements Component {

    private final Control infoProperty;
    private final MetaProperty metaProperty;

    private Container parent;
    private CodeContext<D> codeCtxt;
    private ComponentAddress address;
    private ExecutionContext execCtxt;
    private PacketRouter router;
    private LogInfo logInfo;
    private ProxyContext proxyContext;

    CodeComponent() {
        infoProperty = (call, router) -> {
            if (call.isReplyRequired()) {
                router.route(call.reply(getInfo()));
            }
        };
        metaProperty = new MetaProperty();
    }

    @Override
    public final Container getParent() {
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

    private void disconnectAll() {
        codeCtxt.portIDs().map(codeCtxt::getPort).forEach(Port::disconnectAll);
    }

    @Override
    public void hierarchyChanged() {
        execCtxt = null;
        router = null;
        logInfo = null;
        codeCtxt.handleHierarchyChanged();
        if (getAddress() == null) {
            codeCtxt.handleDispose();
        }
    }

    @Override
    public Control getControl(String id) {
        return codeCtxt.getControl(id);
    }

    @Override
    public Port getPort(String id) {
        return codeCtxt.getPort(id);
    }

    @Override
    public ComponentInfo getInfo() {
        return codeCtxt.getInfo();
    }

    @Override
    public void write(TreeWriter writer) {
        writer.writeType(codeCtxt.getComponentType());
        writer.writeInfo(getInfo());
        codeCtxt.writeDescriptors(writer);
    }

    Lookup getLookup() {
        if (parent != null) {
            return parent.getLookup();
        } else {
            return Lookup.EMPTY;
        }
    }

    void install(CodeContext<D> cc) {
        cc.setComponent(this);
        cc.handleConfigure(this, codeCtxt);
        if (codeCtxt != null) {
            codeCtxt.handleDispose();
        }
        codeCtxt = cc;
        codeCtxt.handleHierarchyChanged();
    }

    CodeContext<D> getCodeContext() {
        return codeCtxt;
    }

    ComponentAddress getAddress() {
        if (parent == null) {
            address = null;
        } else if (address == null) {
            address = parent.getAddress(this);
        }
        return address;
    }

    ExecutionContext getExecutionContext() {
        if (execCtxt == null) {
            execCtxt = getLookup().find(ExecutionContext.class)
                    .orElse(null);
        }
        return execCtxt;
    }

    PacketRouter getPacketRouter() {
        if (router == null) {
            router = getLookup().find(PacketRouter.class)
                    .orElse(null);
        }
        return router;
    }

    ProxyContext getProxyContext() {
        if (proxyContext == null) {
            ThreadContext threadCtxt = getLookup().find(ThreadContext.class)
                    .orElseThrow(UnsupportedOperationException::new);
            proxyContext = new ProxyContext(this, threadCtxt);
        }
        return proxyContext;
    }

    ControlAddress getLogToAddress() {
        if (logInfo == null) {
            initLogInfo();
        }
        return logInfo.toAddress;
    }

    private void initLogInfo() {
        ControlAddress toAddress = getLookup().find(Services.class)
                .flatMap(srvs -> srvs.locate(LogService.class))
                .map(srv -> ControlAddress.of(srv, LogService.LOG))
                .orElse(null);

        LogLevel level = getLookup().find(LogLevel.class).orElse(LogLevel.ERROR);

        if (toAddress == null) {
            level = LogLevel.ERROR;
        }

        logInfo = new LogInfo(level, toAddress);
    }

    static class ControlWrapper extends ControlDescriptor<ControlWrapper> {

        private final String controlID;

        private CodeComponent<?> component;

        ControlWrapper(String controlID, int index) {
            super(ControlWrapper.class, controlID, Category.Internal, index);
            this.controlID = controlID;
        }

        @Override
        public void attach(CodeContext<?> context, ControlWrapper previous) {
            component = context.getComponent();
        }

        @Override
        public Control control() {
            return switch (controlID) {
                case ComponentProtocol.INFO ->
                    component.infoProperty;
                case ComponentProtocol.META ->
                    component.metaProperty;
                case ComponentProtocol.META_MERGE ->
                    component.metaProperty.getMergeControl();
                default ->
                    null;
            };
        }

        @Override
        public ControlInfo controlInfo() {
            return switch (controlID) {
                case ComponentProtocol.INFO ->
                    ComponentProtocol.INFO_INFO;
                case ComponentProtocol.META ->
                    ComponentProtocol.META_INFO;
                case ComponentProtocol.META_MERGE ->
                    ComponentProtocol.META_MERGE_INFO;
                default ->
                    null;
            };
        }

        @Override
        public void write(TreeWriter writer) {
            switch (controlID) {
                case ComponentProtocol.META -> {
                    PMap value = component.metaProperty.getValue();
                    if (!value.isEmpty()) {
                        writer.writeProperty(ComponentProtocol.META, value);
                    }
                }
            }
        }

    }

    private static class LogInfo {

        private final LogLevel level;
        private final ControlAddress toAddress;

        private LogInfo(LogLevel level,
                ControlAddress toAddress) {
            this.level = level;
            this.toAddress = toAddress;
        }
    }

}
