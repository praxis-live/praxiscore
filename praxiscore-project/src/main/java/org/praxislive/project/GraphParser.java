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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.PortAddress;
import org.praxislive.core.Value;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;

import static org.praxislive.core.syntax.Token.Type.BRACED;
import static org.praxislive.core.syntax.Token.Type.COMMENT;
import static org.praxislive.core.syntax.Token.Type.EOL;
import static org.praxislive.core.syntax.Token.Type.PLAIN;

/**
 *
 */
class GraphParser {

    private final static String AT = "@";
    private final static String CONNECT = "~";
    private final static String PROPERTY_PREFIX = ".";
    private final static String RELATIVE_ADDRESS_PREFIX = "./";

    private final String script;
    private final boolean subgraph;
    private final URI context;

    private GraphParser(String script, boolean subgraph, URI context) {
        this.script = script;
        this.subgraph = subgraph;
        this.context = context;
    }

    private GraphElement.Root doParse() throws ParseException {
        if (subgraph) {
            return parseSubGraph();
        } else {
            return parseFullGraph();
        }
    }

    private GraphElement.Root parseFullGraph() throws ParseException {
        try {
            GraphBuilder.Root root = null;
            Iterator<Token> tokens = new Tokenizer(script).iterator();
            List<GraphElement.Command> commands = new ArrayList<>();
            while (tokens.hasNext()) {
                Token token = tokens.next();
                Token.Type type = token.getType();
                if (type == COMMENT || type == EOL) {
                    continue;
                }
                if (type == PLAIN) {
                    if (AT.equals(token.getText())) {
                        root = parseRoot(tokensToEOL(tokens));
                        break;
                    } else {
                        List<Token> toEOL = tokensToEOL(tokens);
                        commands.add(GraphElement.command(script.substring(token.getStartIndex(),
                                toEOL.isEmpty() ? token.getEndIndex() : toEOL.getLast().getEndIndex())));
                    }
                }
            }
            if (root == null) {
                throw new ParseException("No root element found");
            }
            while (tokens.hasNext()) {
                Token.Type type = tokens.next().getType();
                if (type != COMMENT && type != EOL) {
                    throw new ParseException("Unexpected content found after root element");
                }
            }
            commands.forEach(root::command);
            return root.build();

        } catch (ParseException pex) {
            throw pex;
        } catch (Exception ex) {
            throw new ParseException(ex);
        }
    }

    private GraphElement.Root parseSubGraph() throws ParseException {

        try {
            GraphBuilder.Root root = GraphBuilder.syntheticRoot();
            parseComponentBody(root, script);
            return root.build();
        } catch (Exception ex) {
            throw new ParseException(ex);
        }
    }

    private static List<Token> tokensToEOL(Iterator<Token> tokens) {
        List<Token> tks = new ArrayList<>();
        while (tokens.hasNext()) {
            Token t = tokens.next();
            if (t.getType() == EOL) {
                break;
            }
            tks.add(t);
        }
        return tks;
    }

    private GraphBuilder.Root parseRoot(List<Token> tokens) {
        if (tokens.size() < 2 || tokens.size() > 3) {
            throw new IllegalArgumentException("Unexpected number of tokens in parseComponent");
        }
        String id;
        ComponentType type;
        Token t = tokens.get(0);
        if (t.getType() == PLAIN) {
            ComponentAddress address = ComponentAddress.of(t.getText());
            if (address.depth() == 1) {
                id = address.componentID();
            } else {
                throw new IllegalArgumentException("Invalid root address " + address);
            }
        } else {
            throw new IllegalArgumentException("No root address found.");
        }
        t = tokens.get(1);
        if (t.getType() == PLAIN) {
            type = ComponentType.of(t.getText());
        } else {
            throw new IllegalArgumentException("No root type found.");
        }

        GraphBuilder.Root root = GraphBuilder.root(id, type);

        if (tokens.size() == 3) {
            t = tokens.get(2);
            if (t.getType() != BRACED) {
                throw new IllegalArgumentException("Invalid token at end of component line : " + tokens);
            }
            parseComponentBody(root, t.getText());
        }
        return root;
    }

    private void parseComponent(GraphBuilder.Base<?> parent, List<Token> tokens) {
        if (tokens.size() < 2 || tokens.size() > 3) {
            throw new IllegalArgumentException("Unexpected number of tokens in parseComponent");
        }
        // next token should be relative component address
        String id = null;
        ComponentType type = null;
        Token t = tokens.get(0);
        if (t.getType() == PLAIN && t.getText().startsWith(RELATIVE_ADDRESS_PREFIX)) {
            id = t.getText().substring(RELATIVE_ADDRESS_PREFIX.length());
        }
        t = tokens.get(1);
        if (t.getType() == PLAIN) {
            type = ComponentType.of(t.getText());
        }
        if (id == null || type == null) {
            throw new IllegalArgumentException("Invalid component creation line : " + tokens);
        }
        GraphBuilder.Component child = GraphBuilder.component(type);
        if (tokens.size() == 3) {
            t = tokens.get(2);
            if (t.getType() != BRACED) {
                throw new IllegalArgumentException("Invalid token at end of component line : " + tokens);
            }
            parseComponentBody(child, t.getText());
        }
        parent.child(id, child.build());
    }

    private void parseComponentBody(GraphBuilder.Base<?> component, String body) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }

        boolean allowCommands = component instanceof GraphBuilder.Root r && r.isSynthetic();
        Iterator<Token> tokens = new Tokenizer(body).iterator();
        while (tokens.hasNext()) {
            Token token = tokens.next();
            String txt = token.getText();
            switch (token.getType()) {
                case COMMENT ->
                    component.comment(SyntaxUtils.unescapeCommentText(txt));
                case PLAIN -> {
                    if (txt.startsWith(PROPERTY_PREFIX) && txt.length() > 1) {
                        parseProperty(component, txt.substring(1), tokensToEOL(tokens));
                        allowCommands = false;
                    } else if (AT.equals(txt)) {
                        parseComponent(component, tokensToEOL(tokens));
                        allowCommands = false;
                    } else if (CONNECT.equals(txt)) {
                        parseConnection(component, tokensToEOL(tokens));
                        allowCommands = false;
                    } else if (allowCommands && component instanceof GraphBuilder.Root root) {
                        List<Token> toEOL = tokensToEOL(tokens);
                        root.command(GraphElement.command(body.substring(token.getStartIndex(),
                                toEOL.isEmpty() ? token.getEndIndex() : toEOL.getLast().getEndIndex())));
                    } else {
                        throw new IllegalArgumentException("Unexpected PLAIN token : " + txt);
                    }
                }
                case EOL -> {
                    // no op
                }
                default ->
                    throw new IllegalArgumentException(
                            "Unexpected token of type : " + token.getType() + " , body : " + txt);

            }
        }

    }

    private void parseProperty(GraphBuilder.Base<?> component, String property, List<Token> tokens) {
        if (tokens.size() != 1) {
            throw new IllegalArgumentException("Empty tokens passed to parseProperty ." + property);
        }
        Value value;
        if (context != null) {
            value = SyntaxUtils.valueFromToken(context, tokens.get(0));
        } else {
            value = SyntaxUtils.valueFromToken(tokens.get(0));
        }
        component.property(property, value);
    }

    private void parseConnection(GraphBuilder.Base<?> parent, List<Token> tokens) {
        if (tokens.size() != 2) {
            throw new IllegalArgumentException("Unexpected number of tokens in parseConnection");
        }
        Token source = tokens.get(0);
        Token target = tokens.get(1);
        String sourceComponent = null;
        String sourcePort = null;
        String targetComponent = null;
        String targetPort = null;
        try {
            if (source.getType() == PLAIN && source.getText().startsWith(RELATIVE_ADDRESS_PREFIX)) {
                PortAddress address = PortAddress.of(source.getText().substring(1));
                sourceComponent = address.component().componentID();
                sourcePort = address.portID();
            }
            if (target.getType() == PLAIN && target.getText().startsWith(RELATIVE_ADDRESS_PREFIX)) {
                PortAddress address = PortAddress.of(target.getText().substring(1));
                targetComponent = address.component().componentID();
                targetPort = address.portID();
            }
            parent.connection(sourceComponent, sourcePort, targetComponent, targetPort);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid connection : " + tokens, ex);
        }
    }

    static GraphElement.Root parse(String script) throws ParseException {
        return new GraphParser(Objects.requireNonNull(script), false, null).doParse();
    }

    static GraphElement.Root parseSubgraph(String script) throws ParseException {
        return new GraphParser(Objects.requireNonNull(script), true, null).doParse();
    }

    static GraphElement.Root parse(URI context, String script) throws ParseException {
        return new GraphParser(Objects.requireNonNull(script), false,
                Objects.requireNonNull(context)).doParse();
    }

    static GraphElement.Root parseSubgraph(URI context, String script) throws ParseException {
        return new GraphParser(Objects.requireNonNull(script), true,
                Objects.requireNonNull(context)).doParse();
    }

}
