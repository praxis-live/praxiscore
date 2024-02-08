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
package org.praxislive.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.Packet;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PError;

/**
 * A default implementation of {@link ScriptService}.
 */
public final class DefaultScriptService extends AbstractRoot implements RootHub.ServiceProvider {

    private static final System.Logger LOG = System.getLogger(DefaultScriptService.class.getName());

    private final Map<String, Control> controls;
    private final Map<ControlAddress, ScriptContext> contexts;
    private int exID;

    public DefaultScriptService() {
        controls = new HashMap<>();
        controls.put(ScriptService.EVAL, new EvalControl());
        controls.put(ScriptService.CLEAR, new ClearControl());
        contexts = new HashMap<>();
    }

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(ScriptService.class);
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        try {
            controls.get(call.to().controlID()).call(call, router);
        } catch (Exception ex) {
            router.route(call.error(PError.of(ex)));
        }
    }

    private ScriptExecutor getExecutor(ControlAddress from) {
        ScriptContext ctxt = contexts.get(from);
        if (ctxt != null) {
            return ctxt.executor;
        }
        exID++;
        String id = "_exec_" + exID;
        EnvImpl env = new EnvImpl(ControlAddress.of(getAddress(), id));
        ScriptExecutor ex = new ScriptExecutor(env, from.component());
        controls.put(id, new ScriptControl(ex));
        contexts.put(from, new ScriptContext(id, ex));
        return ex;
    }

    private void clearContext(ControlAddress from) {
        ScriptContext ctxt = contexts.remove(from);
        if (ctxt == null) {
            return;
        }
        ctxt.executor.flushEvalQueue();
        controls.remove(ctxt.id);
    }

    private class EvalControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                getExecutor(call.from()).queueEvalCall(call);
            } else {
                throw new IllegalStateException("Eval control received unexpected call.\n" + call);
            }
        }

    }

    private class ClearControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                clearContext(call.from());
                if (call.isReplyRequired()) {
                    router.route(call.reply());
                }
            } else {
                throw new IllegalStateException("Claer control received unexpected call.\n" + call);
            }
        }

    }

    private class ScriptControl implements Control {

        private final ScriptExecutor executor;

        private ScriptControl(ScriptExecutor executor) {
            this.executor = executor;
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                if (call.to().equals(call.from())) {
                    executor.processScriptCall(call);
                } else {
                    throw new UnsupportedOperationException();
                }
            } else {
                executor.processScriptCall(call);
            }

        }

    }

    private class ScriptContext {

        private String id;
        private ScriptExecutor executor;

        private ScriptContext(String id, ScriptExecutor executor) {
            this.id = id;
            this.executor = executor;
        }

    }

    private class EnvImpl implements Env {

        private final ControlAddress address;
        private final Router router;

        private EnvImpl(ControlAddress address) {
            this.address = address;
            router = new Router();
        }

        @Override
        public Lookup getLookup() {
            return DefaultScriptService.this.getLookup();
        }

        @Override
        public long getTime() {
            return DefaultScriptService.this.getExecutionContext().getTime();
        }

        @Override
        public PacketRouter getPacketRouter() {
            return router;
        }

        @Override
        public ControlAddress getAddress() {
            return address;
        }
    }

    private class Router implements PacketRouter {

        @Override
        public void route(Packet packet) {
            LOG.log(System.Logger.Level.TRACE,
                    () -> "Sending Call : ---\n" + packet.toString());
            getRouter().route(packet);
        }

    }
}
