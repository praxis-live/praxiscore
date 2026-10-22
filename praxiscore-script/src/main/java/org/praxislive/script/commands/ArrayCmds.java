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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.ScriptStackFrame;
import org.praxislive.script.StackFrame;

/**
 *
 */
class ArrayCmds {

    private static final Map<String, Command> COMMANDS = Map.of(
            "array", new Array(),
            "array-contains", new ArrayContains(),
            "array-get", new ArrayGet(),
            "array-join", new ArrayJoin(),
            "array-range", new ArrayRange(),
            "array-size", new ArraySize(),
            "explode", new Explode(),
            "foreach", new ForEach(),
            "range", new GenerateRange()
    );

    private ArrayCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.putAll(COMMANDS);
    }

    private static class Array implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.isEmpty()) {
                return List.of(PArray.EMPTY);
            }
            PArray ar = args.stream().collect(PArray.collector());
            return List.of(ar);
        }

        @Override
        public String description() {
            return CoreCommands.message("array.description");
        }

    }

    private static class ArrayContains implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 2) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }
            PArray array = PArray.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("First argument is not an array"));
            Value value = args.get(1);
            for (Value x : array) {
                if (x.equivalent(value) || value.equivalent(x)) {
                    return List.of(PBoolean.TRUE);
                }
            }
            return List.of(PBoolean.FALSE);
        }

        @Override
        public String description() {
            return CoreCommands.message("array-contains.description");
        }

    }

    private static class ArrayGet implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 2) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }

            PArray array = PArray.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("First argument is not an array"));

            int index = PNumber
                    .from(args.get(1))
                    .orElseThrow(() -> new IllegalArgumentException("Second argument is not a number"))
                    .toIntValue();

            return List.of(array.get(index));
        }

        @Override
        public String description() {
            return CoreCommands.message("array-get.description");
        }

    }

    private static class ArrayJoin implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            PArray result = args.stream()
                    .flatMap(v -> PArray.from(v).stream())
                    .flatMap(PArray::stream)
                    .collect(PArray.collector());
            return List.of(result);
        }

        @Override
        public String description() {
            return CoreCommands.message("array-join.description");
        }

    }

    private static class ArrayRange implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() < 2 || args.size() > 3) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }

            PArray array = PArray.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("First argument is not an array"));

            int from, to;
            if (args.size() == 2) {
                from = 0;
                to = PNumber
                        .from(args.get(1))
                        .orElseThrow(() -> new IllegalArgumentException("Second argument is not a number"))
                        .toIntValue();
            } else {
                from = PNumber
                        .from(args.get(1))
                        .orElseThrow(() -> new IllegalArgumentException("Second argument is not a number"))
                        .toIntValue();
                to = PNumber
                        .from(args.get(2))
                        .orElseThrow(() -> new IllegalArgumentException("Third argument is not a number"))
                        .toIntValue();
            }

            return List.of(PArray.of(array.asList().subList(from, to)));
        }

        @Override
        public String description() {
            return CoreCommands.message("array-range.description");
        }

    }

    private static class ArraySize implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }

            PArray array = PArray.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("Argument is not an array"));

            return List.of(PNumber.of(array.size()));
        }

        @Override
        public String description() {
            return CoreCommands.message("array-size.description");
        }

    }

    private static class Explode implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            return args.stream()
                    .flatMap(v -> PArray.from(v)
                            .orElseThrow(IllegalArgumentException::new)
                            .stream())
                    .toList();
        }

        @Override
        public String description() {
            return CoreCommands.message("explode.description");
        }

    }

    private static class ForEach implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 4) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }
            if (!"in".equals(args.get(1).toString())) {
                throw new IllegalArgumentException("Second argument must be in");
            }
            List<String> vars = PArray.from(args.get(0))
                    .orElseThrow(IllegalArgumentException::new)
                    .asListOf(String.class);
            if (vars.isEmpty()) {
                throw new IllegalArgumentException("Variable names cannot be empty");
            }
            List<Value> values = PArray.from(args.get(2))
                    .orElseThrow(IllegalArgumentException::new)
                    .asList();
            StackFrame frame = StackFrame.empty();
            if (values.isEmpty()) {
                return frame;
            }
            String script = args.get(3).toString();
            int varSize = vars.size();
            if (values.size() % varSize != 0) {
                throw new IllegalArgumentException("Array not a multiple of variable names");
            }
            for (int i = 0; i < values.size(); i += varSize) {
                int offset = i;
                frame = frame.andThen(v -> {
                    ScriptStackFrame.Builder builder
                            = ScriptStackFrame.forScript(namespace, script);
                    for (int j = 0; j < varSize; j++) {
                        builder.createConstant(vars.get(j), values.get(offset + j));
                    }
                    return builder.build();
                });
            }
            return frame.andThen(v -> StackFrame.empty());
        }

        @Override
        public String description() {
            return CoreCommands.message("foreach.description");
        }

    }

    private static class GenerateRange implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.isEmpty() || args.size() > 3) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }
            List<PNumber> params = OpCmds.asNumbers(args);
            if (OpCmds.allIntegers(params)) {
                return List.of(generateIntRange(params));
            } else {
                return List.of(generateDoubleRange(params));
            }
        }

        private PArray generateIntRange(List<PNumber> params) throws Exception {
            int start = params.size() == 1 ? 0 : params.get(0).toIntValue();
            int end = params.size() == 1 ? params.get(0).toIntValue() : params.get(1).toIntValue();
            int step = params.size() == 3 ? params.get(2).toIntValue() : (end < start) ? -1 : 1;
            if (step == 0) {
                throw new IllegalArgumentException();
            }
            boolean reverse = end < start;
            if ((reverse && step > 0) || (!reverse && step < 0)) {
                throw new IllegalArgumentException();
            }
            return IntStream.iterate(start,
                    i -> reverse ? i > end : i < end,
                    i -> i + step)
                    .mapToObj(PNumber::of)
                    .collect(PArray.collector());
        }

        private PArray generateDoubleRange(List<PNumber> params) throws Exception {
            double start = params.size() == 1 ? 0 : params.get(0).value();
            double end = params.size() == 1 ? params.get(0).value() : params.get(1).value();
            double step = params.size() == 3 ? params.get(2).value() : (end < start) ? -1 : 1;
            if (Math.abs(step) < 0.001) {
                throw new IllegalArgumentException();
            }
            boolean reverse = end < start;
            if ((reverse && step > 0) || (!reverse && step < 0)) {
                throw new IllegalArgumentException();
            }
            return DoubleStream.iterate(start,
                    i -> reverse ? i > end : i < end,
                    i -> i + step)
                    .mapToObj(PNumber::of)
                    .collect(PArray.collector());
        }

        @Override
        public String description() {
            return CoreCommands.message("range.description");
        }

    }

}
