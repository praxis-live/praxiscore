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
package org.praxislive.script.commands;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.praxislive.core.Value;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.Namespace;
import org.praxislive.script.ScriptStackFrame;
import org.praxislive.script.StackFrame;

/**
 *
 */
class AtCmds {

    private final static At AT = new At();
    private final static NotAt NOT_AT = new NotAt();

    private AtCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.put("@", AT);
        commands.put("!@", NOT_AT);
    }

    private static class At implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {

            if (args.size() < 2 || args.size() > 3) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }

            ComponentAddress ctxt = ComponentAddress.from(args.get(0))
                    .orElseThrow(IllegalArgumentException::new);
            ComponentType type;
            String script;
            if (args.size() == 3) {
                type = ComponentType.from(args.get(1))
                        .orElseThrow(IllegalArgumentException::new);
                script = args.get(2).toString();
            } else {
                Value arg = args.get(1);
                if (!arg.toString().contains(" ")) {
                    try {
                        type = ComponentType.from(arg).get();
                        script = null;
                    } catch (Exception ex) {
                        type = null;
                        script = arg.toString();
                    }
                } else {
                    type = null;
                    script = arg.toString();
                }
            }
            StackFrame create = null;
            if (type != null) {
                if (ctxt.depth() == 1) {
                    create = StackFrame.serviceCall(RootManagerService.class, RootManagerService.ADD_ROOT,
                            List.of(PString.of(ctxt.rootID()), type));
                } else {
                    create = StackFrame.call(
                            ControlAddress.of(ctxt.parent(), ContainerProtocol.ADD_CHILD),
                            List.of(PString.of(ctxt.componentID()), type));
                }
            }
            Function<List<Value>, StackFrame> eval = null;
            if (script != null) {
                String s = script;
                eval = v -> {
                    return ScriptStackFrame.forScript(namespace, s)
                            .createConstant(Env.CONTEXT, ctxt)
                            .build();
                };

            }

            if (create != null) {
                if (eval != null) {
                    return create.andThen(eval);
                } else {
                    return create;
                }
            } else if (eval != null) {
                return eval.apply(List.of());
            } else {
                throw new IllegalStateException();
            }

        }
    }

    private static class NotAt implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            ComponentAddress component = ComponentAddress.from(args.get(0))
                    .orElseThrow(IllegalArgumentException::new);
            if (component.depth() == 1) {
                return StackFrame.serviceCall(RootManagerService.class,
                        RootManagerService.REMOVE_ROOT,
                        PString.of(component.componentID()));
            } else {
                return StackFrame.call(ControlAddress.of(component.parent(),
                        ContainerProtocol.REMOVE_CHILD),
                        PString.of(component.componentID()));
            }
        }

    }

}
