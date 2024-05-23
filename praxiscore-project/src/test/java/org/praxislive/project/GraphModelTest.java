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
package org.praxislive.project;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Connection;
import org.praxislive.core.Info;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class GraphModelTest {

    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");

    private static final String GRAPH_SCRIPT = textGraph();
    private static final String SUBGRAPH_SCRIPT = textSubgraph();
    private static final PMap GRAPH_SERIALIZED = serializedGraph();
    private static final URI PARENT_CONTEXT = URI.create("file:/parent/");

    public GraphModelTest() {
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("START TEST : " + info.getDisplayName());
        }
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("END TEST : " + info.getDisplayName());
            System.out.println("=====================================");
        }
    }

    @Test
    public void testParseGraph() throws ParseException {
        GraphModel model = GraphModel.parse(PARENT_CONTEXT, GRAPH_SCRIPT);
        if (VERBOSE) {
            System.out.println("Constructed model");
            System.out.println(model.root());
        }
        verifyFullGraphModel(model);
        assertEquals(1, model.root().comments().size());
        assertEquals("%graph.x 42", model.root().comments().get(0).text());
        assertEquals(PARENT_CONTEXT, model.context().orElseThrow());
    }

    @Test
    public void testParseGraphWithCommands() throws ParseException {
        String script = """
                        libraries {
                            pkg:maven/org.praxislive/praxiscore-api
                        }
                        # comment
                        """ + GRAPH_SCRIPT;
        GraphModel model = GraphModel.parse(PARENT_CONTEXT, script);
        if (VERBOSE) {
            System.out.println("Constructed model");
            System.out.println(model.root());
        }
        verifyFullGraphModel(model);
        assertEquals(1, model.root().comments().size());
        assertEquals("%graph.x 42", model.root().comments().get(0).text());
        assertEquals(PARENT_CONTEXT, model.context().orElseThrow());
        assertEquals(1, model.root().commands().size());
        List<Token> command = model.root().commands().get(0).tokens();
        assertEquals(2, command.size());
        assertEquals(Token.Type.PLAIN, command.get(0).getType());
        assertEquals("libraries", command.get(0).getText());
        assertEquals(Token.Type.BRACED, command.get(1).getType());
        assertEquals("pkg:maven/org.praxislive/praxiscore-api",
                command.get(1).getText().strip());
    }

    @Test
    public void testParseSubGraph() throws ParseException {
        GraphModel model = GraphModel.parseSubgraph(PARENT_CONTEXT, SUBGRAPH_SCRIPT);
        if (VERBOSE) {
            System.out.println("Constructed subgraph model");
            System.out.println(model.root());
        }
        assertTrue(model.root().isSynthetic());
        assertEquals("", model.root().id());
        assertEquals(List.of("child1", "container"), List.copyOf(model.root().children().keySet()));
        assertEquals(PResource.of(PARENT_CONTEXT.resolve("text")),
                model.root().children()
                        .get("container").children()
                        .get("child1").properties().get("p1").value()
        );
        assertEquals(List.of(
                "child1", "out", "container", "in",
                "container", "ready", "child1", "trigger"),
                model.root().connections().stream()
                        .flatMap(c -> Stream.of(c.sourceComponent(), c.sourcePort(),
                        c.targetComponent(), c.targetPort())
                        ).toList());
        assertEquals(PARENT_CONTEXT, model.context().orElseThrow());
    }

    @Test
    public void testParseSubGraphWithCommands() throws ParseException {
        String script = """
                        libraries {
                            pkg:maven/org.praxislive/praxiscore-api
                        }
                        shared-code {
                            SHARED.Test {
                                // code
                            }
                        }
                        """ + SUBGRAPH_SCRIPT;
        GraphModel model = GraphModel.parseSubgraph(PARENT_CONTEXT, script);
        if (VERBOSE) {
            System.out.println("Constructed subgraph model");
            System.out.println(model.root());
        }
        assertTrue(model.root().isSynthetic());
        assertEquals("", model.root().id());
        assertEquals(List.of("child1", "container"), List.copyOf(model.root().children().keySet()));
        assertEquals(PResource.of(PARENT_CONTEXT.resolve("text")),
                model.root().children()
                        .get("container").children()
                        .get("child1").properties().get("p1").value()
        );
        assertEquals(List.of(
                "child1", "out", "container", "in",
                "container", "ready", "child1", "trigger"),
                model.root().connections().stream()
                        .flatMap(c -> Stream.of(c.sourceComponent(), c.sourcePort(),
                        c.targetComponent(), c.targetPort())
                        ).toList());
        assertEquals(PARENT_CONTEXT, model.context().orElseThrow());
        assertEquals(2, model.root().commands().size());
        List<Token> command = model.root().commands().get(0).tokens();
        assertEquals(2, command.size());
        assertEquals(Token.Type.PLAIN, command.get(0).getType());
        assertEquals("libraries", command.get(0).getText());
        assertEquals(Token.Type.BRACED, command.get(1).getType());
        assertEquals("pkg:maven/org.praxislive/praxiscore-api",
                command.get(1).getText().strip());
        command = model.root().commands().get(1).tokens();
        assertEquals(2, command.size());
        assertEquals(Token.Type.PLAIN, command.get(0).getType());
        assertEquals("shared-code", command.get(0).getText());
        assertEquals(Token.Type.BRACED, command.get(1).getType());
    }

    @Test
    public void testInvalidGraphs() {
        String script1 = """
                        allowed-command
                        @ /root test:root {
                            disallowed-command
                            .property 2
                        }
                        """;
        assertThrows(ParseException.class, () -> {
            GraphModel.parse(script1);
        });
        String script2 = """
                        allowed-command
                        @ /root test:root {
                            .property 2
                        }
                        disallowed-command
                        """;
        assertThrows(ParseException.class, () -> {
            GraphModel.parse(script2);
        });
    }

    @Test
    public void testFromSerializedRoot() {
        GraphModel model = GraphModel.fromSerializedRoot("root", GRAPH_SERIALIZED);
        if (VERBOSE) {
            System.out.println("Constructed model");
            System.out.println(model.root());
        }
        verifyFullGraphModel(model);
    }

    @Test
    public void testFromSerializedComponent() {
        GraphModel model = GraphModel.fromSerializedComponent("foo",
                PMap.from(GRAPH_SERIALIZED.get("@container")).orElseThrow());
        if (VERBOSE) {
            System.out.println("Constructed model");
            System.out.println(model.root());
        }
        assertTrue(model.root().isSynthetic());
        assertEquals(List.of("foo"), List.copyOf(model.root().children().keySet()));
        assertEquals("core:container", model.root().children().get("foo").type().toString());
        assertEquals(List.of("child1"), List.copyOf(model.root().children().get("foo").children().keySet()));
        assertEquals(PResource.of(PARENT_CONTEXT.resolve("text")),
                model.root().children()
                        .get("foo").children()
                        .get("child1").properties().get("p1").value()
        );
    }

    @Test
    public void testFromSerializedSubgraph() {
        GraphModel model = GraphModel.fromSerializedSubgraph(GRAPH_SERIALIZED,
                id -> List.of("child1", "container").contains(id));
        if (VERBOSE) {
            System.out.println("Constructed subgraph model");
            System.out.println(model.root());
        }
        assertTrue(model.root().isSynthetic());
        assertEquals("", model.root().id());
        assertNull(model.root().properties().get("p1"));
        assertNull(model.root().properties().get("p2"));
        assertEquals(List.of("child1", "container"), List.copyOf(model.root().children().keySet()));
        assertEquals(PResource.of(PARENT_CONTEXT.resolve("text")),
                model.root().children()
                        .get("container").children()
                        .get("child1").properties().get("p1").value()
        );
        assertEquals(List.of(
                "child1", "out", "container", "in",
                "container", "ready", "child1", "trigger"),
                model.root().connections().stream()
                        .flatMap(c -> Stream.of(c.sourceComponent(), c.sourcePort(),
                        c.targetComponent(), c.targetPort())
                        ).toList());
    }
    
    @Test
    public void testWriteGraph() throws ParseException {
        GraphModel model = GraphModel.fromSerializedRoot("root", GRAPH_SERIALIZED)
                .withContext(PARENT_CONTEXT);
        
        String script = model.writeToString();
        if (VERBOSE) {
            System.out.println("Written graph");
            System.out.println(script);
        }
        String expected = GRAPH_SCRIPT.lines()
                .filter(l -> !l.contains("#"))
                .collect(Collectors.joining("\n", "", "\n"));
        assertEquals(expected, script);
        
        GraphModel roundTrip = GraphModel.parse(PARENT_CONTEXT, script);
        assertEquals(model.root(), roundTrip.root());
    }
    
    @Test
    public void testWriteSubgraph() throws ParseException {
        GraphModel model = GraphModel.parseSubgraph(PARENT_CONTEXT, SUBGRAPH_SCRIPT);
        String script = model.writeToString();
        if (VERBOSE) {
            System.out.println("Written subgraph");
            System.out.println(script);
        }
        assertEquals(SUBGRAPH_SCRIPT, script);
        GraphModel roundTrip = GraphModel.parseSubgraph(PARENT_CONTEXT, script);
        assertEquals(model.root(), roundTrip.root());
    }
    
    private void verifyFullGraphModel(GraphModel model) {
        assertEquals("root", model.root().id());
        assertFalse(model.root().isSynthetic());
        assertEquals("root:custom", model.root().type().toString());
        assertInstanceOf(PNumber.class, model.root().properties().get("p1").value());
        assertInstanceOf(PBoolean.class, model.root().properties().get("p2").value());
        assertEquals(List.of("child1", "child2", "container"), List.copyOf(model.root().children().keySet()));
        assertEquals("core:subchild",
                model.root().children()
                        .get("container").children()
                        .get("child1").type().toString()
        );
        assertInstanceOf(PMap.class, model.root().children()
                .get("child2").properties().get("p2").value());
        assertEquals(PResource.of(PARENT_CONTEXT.resolve("text")),
                model.root().children()
                        .get("container").children()
                        .get("child1").properties().get("p1").value()
        );
        assertEquals(List.of(
                "child1", "out", "child2", "in",
                "child1", "out", "container", "in",
                "container", "ready", "child1", "trigger"),
                model.root().connections().stream()
                        .flatMap(c -> Stream.of(c.sourceComponent(), c.sourcePort(),
                        c.targetComponent(), c.targetPort())
                        ).toList());
    }

    private static String textGraph() {
        return """
               @ /root root:custom {
                 # %graph.x 42
                 .p1 1
                 .p2 true
                 @ ./child1 core:type1 {
                   .p1 value
                 }
                 @ ./child2 core:type2 {
                   .p1 42
                   .p2 [map key1 [array 1 2]]
                 }
                 @ ./container core:container {
                   @ ./child1 core:subchild {
                     .p1 [file "text"]
                   }
                 }
                 ~ ./child1!out ./child2!in
                 ~ ./child1!out ./container!in
                 ~ ./container!ready ./child1!trigger
               }
               """;
    }

    private static String textSubgraph() {
        return """
               @ ./child1 core:type1 {
                 .p1 value
               }
               @ ./container core:container {
                 @ ./child1 core:subchild {
                   .p1 [file "text"]
                 }
               }
               ~ ./child1!out ./container!in
               ~ ./container!ready ./child1!trigger
               """;
    }

    private static PMap serializedGraph() {
        var builder = PMap.builder();
        builder.put("%type", ComponentType.of("root:custom"));
        builder.put("%info", Info.component(cmp
                -> cmp.protocol(ComponentProtocol.class)
                        .protocol(ContainerProtocol.class)
                        .control("p1", c -> c.property().input(PNumber.class))
                        .control("p2", c -> c.property().input(PBoolean.class))
        ));
        builder.put("%custom", PString.of("FOO"));
        builder.put("p1", "1");
        builder.put("p2", "true");
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
                .put("p2", PMap.of("key1", PArray.of(PNumber.of(1), PNumber.of(2))))
                .build()
        );
        builder.put("@container", PMap.builder()
                .put("%type", ComponentType.of("core:container"))
                .put("%info", Info.component()
                        .protocol(ComponentProtocol.class)
                        .protocol(ContainerProtocol.class)
                        .build())
                .put("@child1", PMap.builder()
                        .put("%type", ComponentType.of("core:subchild"))
                        .put("%info", ComponentProtocol.API_INFO)
                        .put("p1", PResource.of(URI.create("file:/parent/text")))
                        .build()
                )
                .build()
        );
        builder.put("%connections", PArray.of(
                new Connection("child1", "out", "child2", "in").dataArray(),
                new Connection("child1", "out", "container", "in").dataArray(),
                new Connection("container", "ready", "child1", "trigger").dataArray()
        ));
        return builder.build();
    }

}
