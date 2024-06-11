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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            System.out.println(model);
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
            System.out.println(model);
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
            System.out.println(model);
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
            System.out.println(model);
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
            System.out.println(model);
        }
        verifyFullGraphModel(model);
    }

    @Test
    public void testFromSerializedComponent() {
        GraphModel model = GraphModel.fromSerializedComponent("foo",
                PMap.from(GRAPH_SERIALIZED.get("@container")).orElseThrow());
        if (VERBOSE) {
            System.out.println("Constructed model");
            System.out.println(model);
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
            System.out.println(model);
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
        assertEquals(model, roundTrip);
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
        assertEquals(model, roundTrip);
    }

    @Test
    public void testRename() {
        GraphModel model = GraphModel.fromSerializedRoot("name1", GRAPH_SERIALIZED);
        assertEquals("name1", model.root().id());
        GraphModel renamed = model.withRename("name2");
        if (VERBOSE) {
            System.out.println("Renamed model");
            System.out.println(renamed);
        }
        assertEquals("name2", renamed.root().id());
        assertEquals(model.root().comments(), renamed.root().comments());
        assertEquals(model.root().commands(), renamed.root().commands());
        assertEquals(model.root().properties(), renamed.root().properties());
        assertEquals(model.root().children(), renamed.root().children());
        assertEquals(model.root().connections(), renamed.root().connections());
        assertSame(model.root().children().firstEntry().getValue(),
                renamed.root().children().firstEntry().getValue());
    }

    @Test
    public void testTransform() throws ParseException {
        String script = """
                        @ /root root:custom {
                          @ ./child1 core:variable {
                            # %graph.x 21
                            # %graph.y 42
                            # must be kept \\{
                          }
                          @ ./child2 core:property {
                            # %graph.x 63
                            # %graph.y 84
                            .meta [map foo bar]
                          }
                        }
                        """;
        GraphModel model = GraphModel.parse(script);
        GraphModel transformed = model.withTransform(root
                -> root.transformChildren(children -> children
                .map(c -> Map.entry(c.getKey(), rewriteMeta(c.getValue())))
                .toList()));
        if (VERBOSE) {
            System.out.println("Transformed model");
            System.out.println(transformed);
        }
        PMap attr1 = PMap.from(transformed.root().children()
                .get("child1").properties().get("meta").value()).orElseThrow();
        PMap attr2 = PMap.from(transformed.root().children()
                .get("child2").properties().get("meta").value()).orElseThrow();
        assertEquals(List.of("graph.x", "graph.y"), attr1.keys());
        assertEquals(List.of("foo", "graph.x", "graph.y"), attr2.keys());
        assertEquals("42", attr1.getString("graph.y", ""));
        assertEquals("63", attr2.getString("graph.x", ""));
        assertEquals("bar", attr2.getString("foo", ""));
        assertEquals(1, transformed.root().children().get("child1").comments().size());
        assertEquals("must be kept {", transformed.root().children().get("child1").comments().get(0).text());
        String expectedOutput = """
                        @ /root root:custom {
                          @ ./child1 core:variable {
                            # must be kept \\{
                            .meta [map graph.x 21 graph.y 42]
                          }
                          @ ./child2 core:property {
                            .meta [map foo bar graph.x 63 graph.y 84]
                          }
                        }
                        """;
        assertEquals(expectedOutput, transformed.writeToString());
    }

    private GraphElement.Component rewriteMeta(GraphElement.Component cmp) {
        PMap.Builder mb = PMap.builder();
        for (GraphElement.Comment comment : cmp.comments()) {
            String txt = comment.text().strip();
            if (txt.startsWith("%")) {
                int delim = txt.indexOf(" ");
                if (delim > 1) {
                    mb.put(txt.substring(1, delim), txt.substring(delim + 1));
                }
            }
        }
        PMap attr = mb.build();
        GraphBuilder.Component newCmp = GraphBuilder.component(cmp);
        newCmp.transformProperties(props -> {
            List<Map.Entry<String, GraphElement.Property>> result = new ArrayList<>(props.toList());
            int index = -1;
            for (int i = 0; i < result.size(); i++) {
                if ("meta".equals(result.get(i).getKey())) {
                    index = i;
                    break;
                }
            }
            if (index > -1) {
                PMap existing = PMap.from(result.get(index).getValue().value()).orElseThrow();
                result.set(index, Map.entry("meta",
                        GraphElement.property(PMap.merge(existing, attr, PMap.REPLACE))));
            } else {
                result.add(0, Map.entry("meta", GraphElement.property(attr)));
            }
            return result;
        });

        newCmp.transformComments(comments -> comments
                .filter(c -> !c.text().strip().startsWith("%"))
                .toList());
        return newCmp.build();
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
                Connection.of("child1", "out", "child2", "in"),
                Connection.of("child1", "out", "container", "in"),
                Connection.of("container", "ready", "child1", "trigger")
        ));
        return builder.build();
    }

}
