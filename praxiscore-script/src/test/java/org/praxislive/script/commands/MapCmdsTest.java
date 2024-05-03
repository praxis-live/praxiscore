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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.InlineCommand;

import static org.junit.jupiter.api.Assertions.*;
import static org.praxislive.script.commands.Utils.*;

/**
 *
 */
public class MapCmdsTest {

    private static final String K1 = "Key1";
    private static final String K2 = "Key2";
    private static final String K3 = "Key3";
    private static final String K4 = "Key4";

    private static final int V1 = 42;
    private static final boolean V2 = true;
    private static final String V3 = "FOO";
    private static final PArray V4 = PArray.of(
            ControlAddress.of("/root.info"),
            ControlAddress.of("/root.meta")
    );

    private static final PMap BASE_MAP = PMap.of(
            K1, V1, K2, V2, K3, V3, K4, V4
    );

    private static final Map<String, Command> CMDS;

    static {
        CMDS = new HashMap<>();
        MapCmds.install(CMDS);
    }

    @Test
    public void testMapCommand() throws Exception {
        logTest("testMapCommand");
        InlineCommand map = (InlineCommand) CMDS.get("map");
        List<Value> resultList = map.process(env(), namespace(),
                List.of(
                        PString.of(K1), Value.ofObject(V1),
                        PString.of(K2), Value.ofObject(V2),
                        PString.of(K3), Value.ofObject(V3),
                        PString.of(K4), Value.ofObject(V4)
                ));
        logResult("Command result of map", resultList);
        assertEquals(1, resultList.size());
        PMap result = PMap.from(resultList.get(0)).orElseThrow();
        assertEquals(BASE_MAP, result);

        resultList = map.process(env(), namespace(), List.of());
        logResult("Command result of map-empty", resultList);
        assertEquals(1, resultList.size());
        result = PMap.from(resultList.get(0)).orElseThrow();
        assertSame(PMap.EMPTY, result);

        assertThrows(IllegalArgumentException.class, () -> {
            List<Value> failResult = map.process(env(), namespace(),
                    List.of(
                            PString.of(K1), Value.ofObject(V1),
                            PString.of(K2), Value.ofObject(V2),
                            PString.of(K3), Value.ofObject(V3),
                            PString.of(K4)
                    ));
        });

    }

    @Test
    public void testMapGetCommand() throws Exception {
        logTest("testMapGetCommand");
        InlineCommand mapGet = (InlineCommand) CMDS.get("map-get");
        List<Value> resultList = mapGet.process(env(), namespace(),
                List.of(BASE_MAP, PString.of(K3)));
        logResult("Command result of map-get key3", resultList);
        assertEquals(1, resultList.size());
        assertEquals(V3, resultList.get(0).toString());

        assertThrows(IllegalArgumentException.class, () -> {
            List<Value> failResult = mapGet.process(env(), namespace(),
                    List.of(PMap.EMPTY, PString.of(K3)));
        });

    }

    @Test
    public void testMapKeysCommand() throws Exception {
        logTest("testMapKeysCommand");
        InlineCommand mapKeys = (InlineCommand) CMDS.get("map-keys");
        List<Value> resultList = mapKeys.process(env(), namespace(), List.of(BASE_MAP));
        logResult("Command result of map-keys", resultList);
        assertEquals(1, resultList.size());
        PArray result = PArray.from(resultList.get(0)).orElseThrow();
        PArray expected = PArray.of(
                PString.of(K1),
                PString.of(K2),
                PString.of(K3),
                PString.of(K4)
        );
        assertEquals(expected, result);
    }

    @Test
    public void testMapSizeCommand() throws Exception {
        logTest("testMapSizeCommand");
        InlineCommand mapSize = (InlineCommand) CMDS.get("map-size");
        List<Value> resultList = mapSize.process(env(), namespace(), List.of(BASE_MAP));
        logResult("Command result of map-size", resultList);
        assertEquals(1, resultList.size());
        int result = PNumber.from(resultList.get(0)).orElseThrow().toIntValue();
        assertEquals(BASE_MAP.size(), result);
    }

}
