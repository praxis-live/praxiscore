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
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;

/**
 *
 */
class MapCmds {

    private static final CreateMap MAP = new CreateMap();
    private static final MapGet MAP_GET = new MapGet();
    private static final MapKeys MAP_KEYS = new MapKeys();
    private static final MapSize MAP_SIZE = new MapSize();

    private MapCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.put("map", MAP);
        commands.put("map-get", MAP_GET);
        commands.put("map-keys", MAP_KEYS);
        commands.put("map-size", MAP_SIZE);
    }

    private static class CreateMap implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.isEmpty()) {
                return List.of(PMap.EMPTY);
            }

            int size = args.size();
            if (size % 2 != 0) {
                throw new IllegalArgumentException("Map requires an even number of arguments");
            }

            var builder = PMap.builder();
            for (int i = 0; i < size; i += 2) {
                builder.put(args.get(i).toString(), args.get(i + 1));
            }

            return List.of(builder.build());
        }

    }

    private static class MapGet implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 2) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }

            PMap map = PMap.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("Argument is not a map"));
            String key = args.get(1).toString();

            Value result = map.get(key);
            if (result == null) {
                throw new IllegalArgumentException("Unknown map key");
            }
            return List.of(result);
        }

    }

    private static class MapKeys implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }

            PMap map = PMap.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("Argument is not a map"));

            PArray result = map.keys().stream()
                    .map(PString::of)
                    .collect(PArray.collector());

            return List.of(result);
        }

    }

    private static class MapSize implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }

            PMap map = PMap.from(args.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("Argument is not a map"));

            return List.of(PNumber.of(map.size()));
        }

    }

}
