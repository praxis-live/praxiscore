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
package org.praxislive.script.commands;

import java.util.List;
import java.util.Map;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PortAddress;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;

class InfoCmds {

    private static final Map<String, Command> COMMANDS = Map.of(
            "info", new Info()
    );

    private InfoCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.putAll(COMMANDS);
    }

    private static class Info implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.isEmpty()) {
                return StackFrame.result(List.of(Value.ofObject(description())));
            } else if (args.size() != 1) {
                throw new IllegalArgumentException("Too many arguments");
            }
            String arg = args.getFirst().toString();
            if (arg.startsWith("/")) {
                return componentInfo(arg);
            } else {
                return StackFrame.supply(()
                        -> List.of(commandInfo(namespace, arg)));
            }

        }

        private StackFrame componentInfo(String addressString) {
            ComponentAddress component;
            if (addressString.contains(".")) {
                component = ControlAddress.of(addressString).component();
            } else if (addressString.contains("!")) {
                component = PortAddress.of(addressString).component();
            } else {
                component = ComponentAddress.of(addressString);
            }
            return StackFrame.call(component.control(ComponentProtocol.INFO), List.of())
                    .andThenMap(res
                            -> List.of(PString.of(res.getFirst().print())));
        }

        private Value commandInfo(Namespace namespace, String command) {
            return switch (command) {
                case "commands" ->
                    PString.of(commands(namespace).print());
                case "variables" ->
                    PString.of(variables(namespace).print());
                case "namespace" ->
                    PMap.of("commands", commands(namespace),
                    "variables", variables(namespace));
                case "last-error" ->
                    PString.of(namespace.getValue(Env.ERROR, PString.EMPTY).print());
                default ->
                    PString.of(namespace.getCommand(command).description());
            };
        }

        private PArray commands(Namespace namespace) {
            return namespace.commands().sorted()
                    .map(PString::of)
                    .collect(PArray.collector());
        }

        private PArray variables(Namespace namespace) {
            return namespace.variables()
                    .filter(v -> !v.startsWith("_"))
                    .sorted()
                    .map(PString::of)
                    .collect(PArray.collector());
        }

        @Override
        public String description() {
            return CoreCommands.message("info.description");
        }

    }

}
