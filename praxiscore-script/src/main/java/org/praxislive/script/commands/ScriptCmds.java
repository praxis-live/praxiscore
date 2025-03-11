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
package org.praxislive.script.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Namespace;
import org.praxislive.script.ScriptStackFrame;
import org.praxislive.script.StackFrame;

/**
 *
 */
class ScriptCmds {

    public final static Command EVAL = new Eval();
    public final static Command INCLUDE = new Include();
    private final static Command TRY = new Try();

    private ScriptCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.put("eval", EVAL);
        commands.put("include", INCLUDE);
        commands.put("try", TRY);
    }

    private static class Eval implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args)
                throws Exception {
            if (args.isEmpty()) {
                throw new IllegalArgumentException("No script passed to eval");
            }
            Queue<Value> queue = new ArrayDeque<>(args);
            boolean inline = false;
            boolean trap = false;
            List<String> allowed = null;
            String script = null;
            while (!queue.isEmpty()) {
                String arg = queue.poll().toString();
                if ("--inline".equals(arg)) {
                    inline = true;
                } else if ("--trap-errors".equals(arg)) {
                    trap = true;
                    if (allowed == null) {
                        allowed = List.of();
                    }
                } else if ("--allowed-commands".equals(arg)) {
                    allowed = PArray.from(queue.poll()).orElseThrow().asListOf(String.class);
                } else {
                    script = arg;
                    break;
                }
            }
            if (!queue.isEmpty()) {
                throw new IllegalArgumentException("Additional arguments after script");
            }
            var bld = ScriptStackFrame.forScript(namespace, script);
            if (inline) {
                bld.inline();
            }
            if (trap) {
                bld.trapErrors();
            }
            if (allowed != null) {
                bld.allowedCommands(allowed);
            }
            return bld.build();
        }
    }

    private static class Include implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Wrong number of arguments");
            }
            Path path = PResource.from(args.get(0))
                    .map(PResource::value)
                    .map(Path::of)
                    .orElseThrow(IllegalArgumentException::new);
            return StackFrame.async(() -> PString.of(Files.readString(path)))
                    .andThen(v -> ScriptStackFrame.forScript(namespace, v.get(0).toString()).build());

        }

    }

    private static class Try implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            switch (args.size()) {
                case 1 -> {
                    return ScriptStackFrame.forScript(namespace, args.get(0).toString()).build()
                            .onError(err -> StackFrame.empty());
                }
                case 3 -> {
                    if ("catch".equals(args.get(1).toString())) {
                        return ScriptStackFrame.forScript(namespace, args.get(0).toString()).build()
                                .onError(err -> ScriptStackFrame.forScript(namespace, args.get(2).toString()).build());
                    } else {
                        throw new IllegalArgumentException("Unknown second argument : " + args.get(1));
                    }
                }
                default ->
                    throw new IllegalArgumentException("Invalid number of arguments for try");
            }
        }

    }
}
