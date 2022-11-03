/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2022 Neil C Smith.
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

import java.lang.reflect.Method;
import java.util.List;
import org.praxislive.code.userapi.FN;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.ValueMapper;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PMap;

/**
 *
 */
class FunctionControl implements Control {

    private final Method method;
    private final List<ValueMapper<?>> parameterMappers;
    private final ValueMapper<?> returnMapper;

    private CodeContext<?> context;

    private FunctionControl(Method method,
            List<ValueMapper<?>> parameterMappers,
            ValueMapper<?> returnMapper) {
        this.method = method;
        this.parameterMappers = parameterMappers;
        this.returnMapper = returnMapper;
    }

    @Override
    public void call(Call call, PacketRouter router) throws Exception {
        if (call.isRequest()) {
            var args = call.args();
            if (args.size() != parameterMappers.size()) {
                throw new IllegalArgumentException("Invalid number of arguments");
            }
            Object[] parameters = new Object[args.size()];
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = parameterMappers.get(i).fromValue(args.get(i));
            }
            Object response = context.invokeCallable(call.time(),
                    () -> method.invoke(context.getDelegate(), parameters));
            if (call.isReplyRequired()) {
                if (returnMapper != null) {
                    @SuppressWarnings("unchecked")
                    var mapper = (ValueMapper<Object>) returnMapper;
                    router.route(call.reply(mapper.toValue(response)));
                } else {
                    router.route(call.reply());
                }
            }
        }
    }

    private void attach(CodeContext<?> context) {
        this.context = context;
    }

    private void detach() {
        this.context = null;
    }

    static class Descriptor extends ControlDescriptor {

        private final ControlInfo info;
        private final FunctionControl control;

        private Descriptor(String id,
                int index,
                Method method,
                ControlInfo info,
                List<ValueMapper<?>> parameterMappers,
                ValueMapper<?> returnMapper) {
            super(id, Category.Function, index);
            this.info = info;
            this.control = new FunctionControl(method, parameterMappers, returnMapper);
        }

        @Override
        public void attach(CodeContext<?> context, Control previous) {
            if (previous instanceof FunctionControl) {
                ((FunctionControl) previous).detach();
            }
            control.attach(context);
        }

        @Override
        public Control getControl() {
            return control;
        }

        @Override
        public ControlInfo getInfo() {
            return info;
        }

        static Descriptor create(CodeConnector<?> connector, FN ann, Method method) {
            method.setAccessible(true);
            var parameterTypes = method.getParameterTypes();
            var parameterMappers = new ValueMapper<?>[parameterTypes.length];
            for (int i = 0; i < parameterMappers.length; i++) {
                var type = parameterTypes[i];
                var mapper = ValueMapper.find(type);
                if (mapper == null) {
                    connector.getLog().log(LogLevel.ERROR,
                            "Unsupported parameter type " + type.getSimpleName()
                            + " in method " + method.getName());
                    return null;
                }
                parameterMappers[i] = mapper;
            }
            var returnType = method.getReturnType();
            ValueMapper<?> returnMapper;
            if (returnType == Void.TYPE) {
                returnMapper = null;
            } else {
                returnMapper = ValueMapper.find(returnType);
                if (returnMapper == null) {
                    connector.getLog().log(LogLevel.ERROR,
                            "Unsupported return type " + returnType.getSimpleName()
                            + " in method " + method.getName());
                    return null;
                }
            }
            var id = connector.findID(method);

            ArgumentInfo[] inputArgInfo = new ArgumentInfo[parameterMappers.length];
            for (int i = 0; i < inputArgInfo.length; i++) {
                var type = parameterMappers[i].valueType();
                var properties = type.emptyValue()
                        .map(empty -> PMap.EMPTY)
                        .orElse(PMap.of(ArgumentInfo.KEY_ALLOW_EMPTY, true));
                inputArgInfo[i] = ArgumentInfo.of(type.asClass(), properties);
            }

            ArgumentInfo[] outputArgInfo;
            if (returnMapper != null) {
                var type = returnMapper.valueType();
                var properties = type.emptyValue()
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

            return new Descriptor(id, ann.value(), method, controlInfo,
                    List.of(parameterMappers), returnMapper);
        }

    }

}
