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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.ScriptStackFrame;
import org.praxislive.script.StackFrame;
import org.praxislive.script.Variable;

/**
 *
 */
class ScriptCmds {

    public final static Command EVAL = new Eval();
    public final static Command INCLUDE = new Include();

    private static final Map<String, Command> COMMANDS = Map.of(
            "eval", EVAL,
            "function", new Function(),
            "global", new Global(),
            "if", new If(),
            "include", INCLUDE,
            "try", new Try(),
            "fail", new Fail()
    );

    private ScriptCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.putAll(COMMANDS);
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

        @Override
        public String description() {
            return CoreCommands.message("eval.description");
        }

    }

    private static class Global implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException();
            }
            Namespace global = Optional.ofNullable(
                    namespace.getVariable("_GLOBAL_NAMESPACE"))
                    .map(Variable::getValue)
                    .flatMap(PReference::from)
                    .flatMap(ref -> ref.as(Namespace.class))
                    .orElseThrow(() -> new IllegalStateException("No global namespace"));
            return ScriptStackFrame.forScript(global, args.get(0).toString())
                    .inline().build();
        }

        @Override
        public String description() {
            return CoreCommands.message("global.description");
        }

    }

    private static class If implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            switch (args.size()) {
                case 2 -> {
                    if (checkCondition(args)) {
                        return ScriptStackFrame.forScript(namespace, args.get(1).toString()).build();
                    } else {
                        return StackFrame.empty();
                    }
                }
                case 4 -> {
                    if ("else".equals(args.get(2).toString())) {
                        if (checkCondition(args)) {
                            return ScriptStackFrame.forScript(namespace, args.get(1).toString()).build();
                        } else {
                            return ScriptStackFrame.forScript(namespace, args.get(3).toString()).build();
                        }
                    } else {
                        throw new IllegalArgumentException("Unknown third argument : " + args.get(2));
                    }
                }
                default ->
                    throw new IllegalArgumentException("Invalid number of arguments for if");
            }
        }

        private boolean checkCondition(List<Value> args) {
            return PBoolean.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("First argument is not a boolean"))
                    .value();
        }

        @Override
        public String description() {
            return CoreCommands.message("if.description");
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

        @Override
        public String description() {
            return CoreCommands.message("include.description");
        }

    }

    private static class Function implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 3) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }
            String name = args.get(0).toString();
            List<String> params = PArray.from(args.get(1))
                    .orElseThrow(() -> new IllegalArgumentException("First argument is not an array"))
                    .asListOf(String.class);
            String body = args.get(2).toString();
            UserCommand command = new UserCommand(namespace, name, params, body);
            Command existing = namespace.getCommand(name);
            if (existing instanceof UserCommandWrapper wrapper) {
                wrapper.replace(command);
            } else {
                namespace.addCommand(name, new UserCommandWrapper(command));
            }
            return List.of(args.get(0));
        }

        @Override
        public String description() {
            return CoreCommands.message("function.description");
        }

        private static class UserCommandWrapper implements Command {

            private UserCommand command;

            private UserCommandWrapper(UserCommand command) {
                this.command = command;
            }

            @Override
            public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
                return command.createStackFrame(namespace, args);
            }

            @Override
            public String description() {
                return command.description();
            }

            private void replace(UserCommand command) {
                this.command = command;
            }

        }

        private static class UserCommand implements Command {

            private final Namespace namespace;
            private final String name;
            private final List<String> params;
            private final String body;

            private UserCommand(Namespace namespace, String name, List<String> params, String body) {
                this.namespace = namespace;
                this.name = name;
                this.params = params;
                this.body = body;
            }

            @Override
            public StackFrame createStackFrame(Namespace callerNS, List<Value> args) throws Exception {
                if (args.size() < params.size()) {
                    throw new IllegalArgumentException("Incorrect number of arguments");
                }
                ScriptStackFrame.Builder builder = ScriptStackFrame.forScript(namespace, body);
                for (int i = 0; i < params.size(); i++) {
                    builder.createConstant(params.get(i), args.get(i));
                }
                return builder.build();
            }

            @Override
            public String description() {
                return CoreCommands.message("user-function.description", name,
                        params.stream().map(p -> "<" + p + ">").collect(Collectors.joining(" ")));
            }

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
                                .onError(err -> {
                                    String body = args.get(2).toString();
                                    ScriptStackFrame.Builder builder
                                            = ScriptStackFrame
                                                    .forScript(namespace, body);
                                    if (!err.isEmpty()) {
                                        builder.createVariable(Env.ERROR, err.getFirst());
                                    }
                                    return builder.build();
                                });
                    } else {
                        throw new IllegalArgumentException("Unknown second argument : " + args.get(1));
                    }
                }
                default ->
                    throw new IllegalArgumentException("Invalid number of arguments for try");
            }
        }

        @Override
        public String description() {
            return CoreCommands.message("try.description");
        }

    }

    private static class Fail implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            Value arg = args.isEmpty() ? PString.EMPTY : args.getFirst();
            PError error = PError.from(arg).orElseGet(() -> PError.of(arg.toString()));
            throw new PError.WrapperException(error);
        }

        @Override
        public String description() {
            return CoreCommands.message("fail.description");
        }

    }
}
