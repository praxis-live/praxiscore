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
 *
 */
package org.praxislive.code;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.praxislive.code.userapi.Async;
import org.praxislive.code.userapi.FN;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.ValueMapper;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;

/**
 *
 */
class FunctionDescriptor extends ControlDescriptor<FunctionDescriptor> {

    private final Method method;
    private final ControlInfo info;
    private final boolean async;
    private final List<ValueMapper<?>> parameterMappers;
    private final ValueMapper<?> returnMapper;

    private FunctionControl control;

    private FunctionDescriptor(String id,
            int index,
            Method method,
            ControlInfo info,
            List<ValueMapper<?>> parameterMappers,
            ValueMapper<?> returnMapper,
            boolean async) {
        super(FunctionDescriptor.class, id, Category.Function, index);
        this.method = method;
        this.info = info;
        this.parameterMappers = parameterMappers;
        this.returnMapper = returnMapper;
        this.async = async;
    }

    @Override
    public void attach(CodeContext<?> context, FunctionDescriptor previous) {
        if (previous != null) {
            if (isCompatible(previous)) {
                control = previous.control;
            } else {
                previous.dispose();
            }
        }
        if (control == null) {
            if (async) {
                control = new AsyncFunctionControl(parameterMappers, returnMapper);
            } else {
                control = new DirectFunctionControl(parameterMappers, returnMapper);
            }
        }
        control.attach(context, method);
    }

    @Override
    public Control control() {
        return control;
    }

    @Override
    public ControlInfo controlInfo() {
        return info;
    }

    @Override
    public void dispose() {
        if (control != null) {
            control.dispose();
        }
    }

    @Override
    public void onStop() {
        if (control != null) {
            control.onStop();
        }
    }

    private boolean isCompatible(FunctionDescriptor other) {
        return method.getGenericReturnType().equals(other.method.getGenericReturnType())
                && Arrays.equals(method.getGenericParameterTypes(),
                        other.method.getGenericParameterTypes());
    }

    static FunctionDescriptor create(CodeConnector<?> connector, FN ann, Method method) {
        method.setAccessible(true);
        Class<?>[] parameterTypes = method.getParameterTypes();
        ValueMapper<?>[] parameterMappers = new ValueMapper<?>[parameterTypes.length];
        for (int i = 0; i < parameterMappers.length; i++) {
            Class<?> type = parameterTypes[i];
            ValueMapper<?> mapper = ValueMapper.find(type);
            if (mapper == null) {
                connector.getLog().log(LogLevel.ERROR,
                        "Unsupported parameter type " + type.getSimpleName()
                        + " in method " + method.getName());
                return null;
            }
            parameterMappers[i] = mapper;
        }

        boolean async = false;
        Class<?> returnType = method.getReturnType();
        ValueMapper<?> returnMapper;
        if (returnType == Void.TYPE) {
            returnMapper = null;
        } else if (returnType == Async.class) {
            async = true;
            Class<?> asyncReturnType = TypeUtils.extractRawType(
                    TypeUtils.extractTypeParameter(method.getGenericReturnType())
            );
            returnMapper = asyncReturnType == null ? null
                    : ValueMapper.find(asyncReturnType);
            if (returnMapper == null) {
                connector.getLog().log(LogLevel.ERROR,
                        "Unsupported Async type " + method.getGenericReturnType()
                        + " in method " + method.getName());
                return null;
            }
        } else {
            returnMapper = ValueMapper.find(returnType);
            if (returnMapper == null) {
                connector.getLog().log(LogLevel.ERROR,
                        "Unsupported return type " + returnType.getSimpleName()
                        + " in method " + method.getName());
                return null;
            }
        }
        String id = connector.findID(method);

        ArgumentInfo[] inputArgInfo = new ArgumentInfo[parameterMappers.length];
        for (int i = 0; i < inputArgInfo.length; i++) {
            Value.Type<?> type = parameterMappers[i].valueType();
            PMap properties = type.emptyValue()
                    .map(empty -> PMap.EMPTY)
                    .orElse(PMap.of(ArgumentInfo.KEY_ALLOW_EMPTY, true));
            inputArgInfo[i] = ArgumentInfo.of(type.asClass(), properties);
        }

        ArgumentInfo[] outputArgInfo;
        if (returnMapper != null) {
            Value.Type<?> type = returnMapper.valueType();
            PMap properties = type.emptyValue()
                    .map(empty -> PMap.EMPTY)
                    .orElse(PMap.of(ArgumentInfo.KEY_ALLOW_EMPTY, true));
            outputArgInfo = new ArgumentInfo[]{
                ArgumentInfo.of(type.asClass(), properties)
            };
        } else {
            outputArgInfo = new ArgumentInfo[0];
        }

        ControlInfo controlInfo = Info.control().function()
                .inputs(inputArgInfo)
                .outputs(outputArgInfo)
                .build();

        return new FunctionDescriptor(id, ann.value(), method, controlInfo,
                List.of(parameterMappers), returnMapper, async);
    }

    private static abstract class FunctionControl implements Control {

        abstract void attach(CodeContext<?> context, Method method);

        abstract void dispose();

        void onStop() {

        }

    }

    private static class DirectFunctionControl extends FunctionControl {

        private final List<ValueMapper<?>> parameterMappers;
        private final ValueMapper<Object> returnMapper;

        private CodeContext<?> context;
        private Method method;

        @SuppressWarnings("unchecked")
        private DirectFunctionControl(List<ValueMapper<?>> parameterMappers,
                ValueMapper<?> returnMapper) {
            this.parameterMappers = parameterMappers;
            this.returnMapper = (ValueMapper<Object>) returnMapper;
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                List<Value> args = call.args();
                int reqCount = parameterMappers.size();
                if (args.size() < reqCount) {
                    throw new IllegalArgumentException("Not enough arguments in call");
                }
                Object[] parameters = new Object[reqCount];
                for (int i = 0; i < parameters.length; i++) {
                    parameters[i] = parameterMappers.get(i).fromValue(args.get(i));
                }
                try {
                    Object response = context.invokeCallable(call.time(),
                            () -> method.invoke(context.getDelegate(), parameters));
                    if (call.isReplyRequired()) {
                        if (returnMapper != null) {
                            router.route(call.reply(returnMapper.toValue(response)));
                        } else {
                            router.route(call.reply());
                        }
                    }
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof Exception exc) {
                        throw exc;
                    } else {
                        throw ex;
                    }
                }
            }
        }

        @Override
        void attach(CodeContext<?> context, Method method) {
            this.context = context;
            this.method = method;
        }

        @Override
        void dispose() {
            this.context = null;
            this.method = null;
        }

    }

    private static class AsyncFunctionControl extends FunctionControl {

        private final List<ValueMapper<?>> parameterMappers;
        private final ValueMapper<Object> returnMapper;
        private final Map<Call, CompletableFuture<?>> pending;

        private CodeContext<?> context;
        private Method method;

        @SuppressWarnings("unchecked")
        private AsyncFunctionControl(List<ValueMapper<?>> parameterMappers,
                ValueMapper<?> returnMapper) {
            this.parameterMappers = parameterMappers;
            this.returnMapper = (ValueMapper<Object>) returnMapper;
            pending = new LinkedHashMap<>();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                List<Value> args = call.args();
                int reqCount = parameterMappers.size();
                if (args.size() < reqCount) {
                    throw new IllegalArgumentException("Not enough arguments in call");
                }
                Object[] parameters = new Object[reqCount];
                for (int i = 0; i < parameters.length; i++) {
                    parameters[i] = parameterMappers.get(i).fromValue(args.get(i));
                }
                try {
                    Object response = context.invokeCallable(call.time(),
                            () -> method.invoke(context.getDelegate(), parameters));
                    if (call.isReplyRequired()) {
                        Async<Object> async = (Async<Object>) response;
                        CompletableFuture<Object> future = Async.toCompletableFuture(async);
                        pending.put(call, future);
                        future.whenComplete((result, error) -> {
                            if (pending.remove(call) != null) {
                                PacketRouter rtr = context.getComponent().getPacketRouter();
                                if (result != null) {
                                    try {
                                        rtr.route(call.reply(returnMapper.toValue(result)));
                                    } catch (Exception ex) {
                                        rtr.route(call.error(PError.of(ex)));
                                    }
                                } else {
                                    PError pe = PError.of(error instanceof Exception e
                                            ? e : new Exception(error));
                                    rtr.route(call.error(pe));
                                }
                            }

                        });
                    }
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof Exception exc) {
                        throw exc;
                    } else {
                        throw ex;
                    }
                }
            }
        }

        @Override
        void attach(CodeContext<?> context, Method method) {
            this.context = context;
            this.method = method;
        }

        @Override
        void onStop() {
            if (!pending.isEmpty() && context != null) {
                List<CompletableFuture<?>> futures = new ArrayList<>(pending.values());
                futures.forEach(f -> f.cancel(false));
            }
            pending.clear();
        }

        @Override
        void dispose() {
            onStop();
            this.context = null;
            this.method = null;
        }

    }

}
