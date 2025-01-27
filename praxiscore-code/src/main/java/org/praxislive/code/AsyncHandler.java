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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.praxislive.code.userapi.Async;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;

class AsyncHandler extends ControlDescriptor<AsyncHandler> implements Control {

    static final String ID = "_async-handler";
    private static final PError UNKNOWN_ERROR = PError.of("Unknown error");

    private final Map<Integer, AsyncReference<?>> resultMap;

    private CodeContext<?> context;

    AsyncHandler(int index) {
        super(AsyncHandler.class, ID, Category.Internal, index);
        this.resultMap = new HashMap<>();
    }

    @Override
    public void attach(CodeContext<?> context, AsyncHandler previous) {
        if (previous != null) {
            resultMap.putAll(previous.resultMap);
            previous.resultMap.clear();
        }
        this.context = context;
    }

    @Override
    public void call(Call call, PacketRouter router) throws Exception {
        if (call.isReply()) {
            AsyncReference<?> asyncRef = resultMap.remove(call.matchID());
            if (asyncRef != null) {
                context.invoke(call.time(), () -> asyncRef.complete(call));
            }
        } else if (call.isError()) {
            PError error = extractError(call.args());
            AsyncReference<?> asyncRef = resultMap.remove(call.matchID());
            if (asyncRef != null) {
                context.invoke(call.time(), () -> {
                    if (!asyncRef.completeWithError(error)) {
                        context.getLog().log(LogLevel.ERROR, error);
                    }
                });
            } else {
                context.getLog().log(LogLevel.ERROR, error);
                context.flush();
            }
        } else if (call.isRequest()) {
            if (call.to().equals(call.from()) && !call.isReplyRequired()) {
                Async<?> timeout = PReference.from(call.args().get(0))
                        .flatMap(ref -> ref.as(Async.class))
                        .orElseThrow();
                if (!timeout.done()) {
                    context.invoke(call.time(), () -> timeout.fail(PError.of("Timeout")));
                }
            } else {
                router.route(call.error(PError.of("Unexpected call")));
            }
        }
    }

    @Override
    public Control control() {
        return this;
    }

    @Override
    public ControlInfo controlInfo() {
        return null;
    }

    @Override
    public void dispose() {
        resultMap.forEach((id, ref) -> {
            ref.completeWithError(PError.of("Disposed"));
        });
        resultMap.clear();
    }

    void register(Call call, Async<Call> async) {
        register(call, async, Function.identity());
    }

    <T> void register(Call call, Async<T> async, Function<Call, T> converter) {
        resultMap.put(call.matchID(), new AsyncReference(call, async, converter));
    }

    private PError extractError(List<Value> args) {
        if (args.isEmpty()) {
            return UNKNOWN_ERROR;
        } else {
            return PError.from(args.get(0))
                    .orElseGet(() -> PError.of(args.get(0).toString()));
        }
    }

    private static class AsyncReference<T> {

        private final Call call;
        private final Async<T> async;
        private final Function<Call, T> converter;

        private AsyncReference(Call call, Async<T> async, Function<Call, T> converter) {
            this.call = call;
            this.async = async;
            this.converter = converter;
        }

        private Call call() {
            return call;
        }

        private void complete(Call call) {
            try {
                T value = converter.apply(call);
                async.complete(value);
            } catch (Exception ex) {
                async.fail(PError.of(ex));
            }
        }

        private boolean completeWithError(PError error) {
            return async.fail(error);
        }

    }

}
