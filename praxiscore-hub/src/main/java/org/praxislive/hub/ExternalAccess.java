/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2026 Neil C Smith.
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
package org.praxislive.hub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PString;

final class ExternalAccess extends AbstractRoot {

    private static final String SCRIPT_CONTROL_ID = "_ext-eval";

    private final Map<String, Control> controls;
    private final Map<Integer, CompletableFuture<List<Value>>> evalPending;

    ExternalAccess() {
        this.evalPending = new HashMap<>();
        controls = Map.of(SCRIPT_CONTROL_ID, this::processEvalResponse);
    }

    @Override
    protected void activating() {
        setRunning();
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        try {
            controls.get(call.to().controlID()).call(call, router);
        } catch (Exception ex) {
            router.route(call.error(PError.of(ex)));
        }
    }

    @Override
    protected void terminating() {
        evalPending.forEach((i, f) -> f.completeExceptionally(new IllegalStateException()));
        evalPending.clear();
    }

    Future<List<Value>> eval(String script) {
        CompletableFuture<List<Value>> future = new CompletableFuture<>();
        invokeLater(() -> processEvalRequest(script, future));
        return future;
    }

    private void processEvalRequest(String script, CompletableFuture<List<Value>> future) {
        try {
            ControlAddress to = ControlAddress.of(findService(ScriptService.class),
                    ScriptService.EVAL);
            ControlAddress from = ControlAddress.of(getAddress(), SCRIPT_CONTROL_ID);
            Call request = Call.create(to, from,
                    getExecutionContext().getTime(), PString.of(script));
            getRouter().route(request);
            evalPending.put(request.matchID(), future);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }
    }

    private void processEvalResponse(Call call, PacketRouter router) {
        if (call.isRequest()) {
            throw new UnsupportedOperationException();
        }
        CompletableFuture<List<Value>> future = evalPending.remove(call.matchID());
        if (future != null) {
            if (call.isReply()) {
                future.complete(call.args());
            } else if (call.isError()) {
                Exception ex = Call.findError(call)
                        .<Exception>map(PError.WrapperException::new)
                        .orElseGet(Exception::new);
                future.completeExceptionally(ex);
            }
        }
    }

}
