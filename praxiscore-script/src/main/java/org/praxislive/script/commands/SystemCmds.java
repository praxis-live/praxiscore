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
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.script.Command;
import org.praxislive.script.Namespace;

import org.praxislive.core.services.SystemManagerService;
import org.praxislive.core.types.PArray;
import org.praxislive.script.ScriptStackFrame;
import org.praxislive.script.StackFrame;

/**
 * Commands for interacting with the running hub.
 */
class SystemCmds {

    private static final Map<String, Command> COMMANDS = Map.of(
            "edit", new Edit(),
            "exit", new Exit(),
            "log", new Log(),
            "roots", new Roots()
    );

    private SystemCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.putAll(COMMANDS);
    }

    private static class Edit implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException();
            }
            ControlAddress prop = ControlAddress.from(args.get(0))
                    .orElseThrow(IllegalArgumentException::new);
            return StackFrame.call(
                    ControlAddress.of(prop.component(), ComponentProtocol.INFO), List.of())
                    .andThen(v -> {
                        ControlInfo info = ComponentInfo.from(v.getFirst())
                                .map(ci -> ci.controlInfo(prop.controlID()))
                                .orElseThrow();
                        if (info == null || info.controlType() != ControlInfo.Type.Property) {
                            throw new IllegalArgumentException("Control is not a writable property");
                        }
                        return ScriptStackFrame.forScript(namespace,
                                """
                                $PROP [user-input $PROP [$PROP] $INFO]; echo ""
                                """
                        )
                                .createConstant("PROP", prop)
                                .createConstant("INFO", info.inputs().getFirst())
                                .build();
                    });
        }

        @Override
        public String description() {
            return CoreCommands.message("edit.description");
        }

    }

    private static class Exit implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            return StackFrame.serviceCall(SystemManagerService.class,
                    SystemManagerService.SYSTEM_EXIT, args);
        }

        @Override
        public String description() {
            return CoreCommands.message("exit.description");
        }

    }

    private static class Log implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            List<Value> logArgs
                    = switch (args.size()) {
                case 1 ->
                    List.of(LogLevel.INFO.asPString(), args.getLast());
                case 2 ->
                    List.of(LogLevel.valueOf(args.getFirst().toString()).asPString(),
                    args.getLast());
                default ->
                    throw new IllegalArgumentException();
            };
            return StackFrame.serviceCall(LogService.class, LogService.LOG, logArgs);
        }

        @Override
        public String description() {
            return CoreCommands.message("log.description");
        }

    }

    private static class Roots implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            StackFrame serviceCall = StackFrame.serviceCall(RootManagerService.class,
                    RootManagerService.ROOTS, List.of());
            if (args.isEmpty()) {
                return serviceCall.andThenMap(v -> {
                    PArray filtered = PArray.from(v.getFirst()).orElseThrow()
                            .stream()
                            .filter(r -> !r.toString().startsWith("_"))
                            .collect(PArray.collector());
                    return List.of(filtered);
                });
            } else if (args.size() == 1 && "--all".equals(args.getFirst().toString())) {
                return serviceCall;
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String description() {
            return CoreCommands.message("roots.description");
        }

    }

}
