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
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PNumber;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;

/**
 *
 */
class ArrayCmds {

    private static final Array ARRAY = new Array();
    private static final ArrayGet ARRAY_GET = new ArrayGet();
    private static final ArrayJoin ARRAY_JOIN = new ArrayJoin();
    private static final ArrayRange ARRAY_RANGE = new ArrayRange();
    private static final ArraySize ARRAY_SIZE = new ArraySize();

    private ArrayCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.put("array", ARRAY);
        commands.put("array-get", ARRAY_GET);
        commands.put("array-join", ARRAY_JOIN);
        commands.put("array-range", ARRAY_RANGE);
        commands.put("array-size", ARRAY_SIZE);
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

    }

}
