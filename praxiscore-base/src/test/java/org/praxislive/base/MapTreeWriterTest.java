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
package org.praxislive.base;

import org.junit.jupiter.api.Test;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Connection;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class MapTreeWriterTest {

    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");

    public MapTreeWriterTest() {
    }

    @Test
    public void testWriter() {
        PMap comparison = buildComparison();
        var writer = new MapTreeWriter();
        PMap output = writer.writeAnnotation("custom", "FOO")
                .writeType(ComponentType.of("core:container"))
                .writeInfo(ContainerProtocol.API_INFO)
                .writeProperty("p1", PNumber.of(1))
                .writeProperty("p2", PBoolean.TRUE)
                .writeChild("child1", w -> {
                    w.writeType(ComponentType.of("core:type1"))
                            .writeInfo(ComponentProtocol.API_INFO)
                            .writeProperty("p1", PString.of("value"));
                })
                .writeChild("child2", w -> {
                    w.writeType(ComponentType.of("core:type2"))
                            .writeInfo(ComponentProtocol.API_INFO)
                            .writeProperty("p1", PNumber.of(42));
                })
                .writeConnection(Connection.of("child1", "out", "child2", "in"))
                .writeConnection(Connection.of("child2", "ready", "child1", "trigger"))
                .build();
        if (VERBOSE) {
            System.out.println("Writer output\n=================");
            System.out.println(output);
        }
        assertEquals(comparison, output);

    }

    private PMap buildComparison() {
        var builder = PMap.builder();
        builder.put("%type", ComponentType.of("core:container"));
        builder.put("%info", ContainerProtocol.API_INFO);
        builder.put("%custom", PString.of("FOO"));
        builder.put("p1", 1);
        builder.put("p2", true);
        builder.put("@child1", PMap.builder()
                .put("%type", ComponentType.of("core:type1"))
                .put("%info", ComponentProtocol.API_INFO)
                .put("p1", "value")
                .build()
        );
        builder.put("@child2", PMap.builder()
                .put("%type", ComponentType.of("core:type2"))
                .put("%info", ComponentProtocol.API_INFO)
                .put("p1", 42)
                .build()
        );
        builder.put("%connections", PArray.of(
                Connection.of("child1", "out", "child2", "in").dataArray(),
                Connection.of("child2", "ready", "child1", "trigger").dataArray()
        ));
        return builder.build();
    }

}
