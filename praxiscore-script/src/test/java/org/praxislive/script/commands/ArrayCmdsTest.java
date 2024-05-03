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
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.InlineCommand;

import static org.junit.jupiter.api.Assertions.*;
import static org.praxislive.script.commands.Utils.*;

/**
 *
 */
public class ArrayCmdsTest {

    private static final int V0 = 42;
    private static final boolean V1 = true;
    private static final String V2 = "FOO";
    private static final PArray V3 = PArray.of(
            ControlAddress.of("/root.info"),
            ControlAddress.of("/root.meta")
    );

    private static final Map<String, Command> CMDS;

    static {
        CMDS = new HashMap<>();
        ArrayCmds.install(CMDS);
    }

    @Test
    public void testArrayCommand() throws Exception {
        logTest("testArrayCommand");
        InlineCommand array = (InlineCommand) CMDS.get("array");
        List<Value> values = List.of(
                Value.ofObject(V0),
                Value.ofObject(V1),
                Value.ofObject(V2),
                Value.ofObject(V3)
        );
        List<Value> resultList = array.process(env(), namespace(), values);
        logResult("Command result of array", resultList);
        assertEquals(1, resultList.size());
        PArray result = PArray.from(resultList.get(0)).orElseThrow();
        assertEquals(4, result.size());
        assertEquals(V0, PNumber.from(result.get(0)).orElseThrow().toIntValue());
        assertEquals(V1, PBoolean.from(result.get(1)).orElseThrow().value());
        assertEquals(V2, PString.from(result.get(2)).orElseThrow().value());
        assertEquals(V3, result.get(3));

        resultList = array.process(env(), namespace(), List.of());
        logResult("Command result of array no-args", resultList);
        assertEquals(1, resultList.size());
        result = PArray.from(resultList.get(0)).orElseThrow();
        assertSame(PArray.EMPTY, result);
    }

    @Test
    public void testArrayGetCommand() throws Exception {
        logTest("testArrayGetCommand");
        InlineCommand arrayGet = (InlineCommand) CMDS.get("array-get");
        PArray array = Stream.of(V0, V1, V2, V3)
                .map(Value::ofObject)
                .collect(PArray.collector());

        List<Value> resultList = arrayGet.process(env(), namespace(),
                List.of(array, PNumber.of(2)));
        logResult("Command result of array-get 2", resultList);
        assertEquals(1, resultList.size());
        Value result = resultList.get(0);
        assertEquals(V2, result.toString());
        resultList = arrayGet.process(env(), namespace(),
                List.of(array, PNumber.of(5)));
        logResult("Command result of array-get 5", resultList);
        assertEquals(1, resultList.size());
        result = resultList.get(0);
        assertEquals(V1, PBoolean.from(result).orElseThrow().value());
        resultList = arrayGet.process(env(), namespace(),
                List.of(array, PNumber.of(-1)));
        logResult("Command result of array-get -1", resultList);
        assertEquals(1, resultList.size());
        result = resultList.get(0);
        assertEquals(V3, result);

        resultList = arrayGet.process(env(), namespace(),
                List.of(PArray.EMPTY, PNumber.of(-1)));
        logResult("Command result of array-get -1 on empty array", resultList);
        assertEquals(1, resultList.size());
        result = resultList.get(0);
        assertSame(PArray.EMPTY, result);

        assertThrows(IllegalArgumentException.class, () -> {
            List<Value> noResult = arrayGet.process(env(), namespace(), List.of(PNumber.ONE));
        });

    }

    @Test
    public void testArrayJoinCommand() throws Exception {
        logTest("testArrayJoinCommand");
        InlineCommand arrayJoin = (InlineCommand) CMDS.get("array-join");
        PArray array1 = Stream.of(V0, V1, V2, V3)
                .map(Value::ofObject)
                .collect(PArray.collector());
        PArray array2 = Stream.of(V3, V2, V1, V0)
                .map(Value::ofObject)
                .collect(PArray.collector());
        List<Value> resultList = arrayJoin.process(env(), namespace(),
                List.of(array1, array2));
        logResult("Command result of array-join", resultList);
        assertEquals(1, resultList.size());
        PArray result = PArray.from(resultList.get(0)).orElseThrow();
        assertEquals(8, result.size());
        PArray expected = Stream.of(V0, V1, V2, V3, V3, V2, V1, V0)
                .map(Value::ofObject)
                .collect(PArray.collector());
        assertEquals(expected, result);

        resultList = arrayJoin.process(env(), namespace(), List.of(array1, PArray.EMPTY));
        logResult("Command result of array-join empty with empty", resultList);
        result = PArray.from(resultList.get(0)).orElseThrow();
        assertEquals(array1, result);

    }

    @Test
    public void testArrayRangeCommand() throws Exception {
        logTest("testArrayRangeCommand");
        InlineCommand arrayRange = (InlineCommand) CMDS.get("array-range");
        PArray array = Stream.of(V0, V1, V2, V3)
                .map(Value::ofObject)
                .collect(PArray.collector());
        List<Value> resultList = arrayRange.process(env(), namespace(),
                List.of(array, PNumber.of(3)));
        logResult("Command result of array-range 3", resultList);
        assertEquals(1, resultList.size());
        PArray result = PArray.from(resultList.get(0)).orElseThrow();
        assertEquals(3, result.size());
        PArray expected = Stream.of(V0, V1, V2)
                .map(Value::ofObject)
                .collect(PArray.collector());
        assertEquals(expected, result);
        resultList = arrayRange.process(env(), namespace(),
                List.of(array, PNumber.of(1), PNumber.of(3)));
        logResult("Command result of array-range 1 3", resultList);
        assertEquals(1, resultList.size());
        result = PArray.from(resultList.get(0)).orElseThrow();
        assertEquals(2, result.size());
        expected = Stream.of(V1, V2)
                .map(Value::ofObject)
                .collect(PArray.collector());
        assertEquals(expected, result);

        assertThrows(IndexOutOfBoundsException.class, () -> {
            List<Value> failResult = arrayRange.process(env(), namespace(),
                    List.of(array, PNumber.of(1), PNumber.of(5)));
        });

    }

    @Test
    public void testArraySizeCommand() throws Exception {
        logTest("testArraySizeCommand");
        InlineCommand arraySize = (InlineCommand) CMDS.get("array-size");
        PArray array = Stream.of(V0, V1, V2, V3)
                .map(Value::ofObject)
                .collect(PArray.collector());
        List<Value> resultList = arraySize.process(env(), namespace(),
                List.of(array));
        logResult("Command result of array-size", resultList);
        assertEquals(1, resultList.size());
        int result = PNumber.from(resultList.get(0)).orElseThrow().toIntValue();
        assertEquals(4, result);
    }

}
