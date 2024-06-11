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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.OrderedMap;
import org.praxislive.core.OrderedSet;
import org.praxislive.core.PortAddress;
import org.praxislive.core.Value;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;

/**
 * Elements of a graph tree.
 */
public sealed abstract class GraphElement {

    private GraphElement() {
    }

    /**
     * Create a Command element from the given script line. The command must be
     * a single line of script with a plain first token.
     *
     * @param command script line
     * @return command element
     * @throws IllegalArgumentException if the command fails to parse according
     * to the rules
     */
    public static Command command(String command) {
        Iterator<Token> itr = new Tokenizer(command).iterator();
        List<Token> tokens = new ArrayList<>();
        while (itr.hasNext()) {
            Token token = itr.next();
            if (tokens.isEmpty()) {
                if (token.getType() != Token.Type.PLAIN) {
                    throw new IllegalArgumentException("First token of a command must be plain");
                }
            }
            if (token.getType() == Token.Type.COMMENT) {
                throw new IllegalArgumentException("Invalid command - contains a comment");
            } else if (token.getType() == Token.Type.EOL) {
                break;
            }
            tokens.add(token);
        }
        if (itr.hasNext()) {
            throw new IllegalArgumentException("Invalid command - tokens found after EOL");
        }
        return new Command(command, tokens);
    }

    /**
     * Create a Comment element from the given text.
     *
     * @param text comment text
     * @return comment element
     */
    public static Comment comment(String text) {
        return new Comment(text);
    }

    /**
     * Create a Connection element between the given source component ID and
     * port ID, and target component ID and port ID.
     *
     * @param sourceComponent source component ID
     * @param sourcePort source port ID
     * @param targetComponent target component ID
     * @param targetPort target port ID
     * @return connection element
     */
    public static Connection connection(String sourceComponent, String sourcePort,
            String targetComponent, String targetPort) {
        return new GraphElement.Connection(sourceComponent, sourcePort, targetComponent, targetPort);
    }

    /**
     * Create a property element of the given value.
     *
     * @param value property value
     * @return property element
     */
    public static Property property(Value value) {
        return new Property(value);
    }

    static Component component(ComponentType type,
            List<Comment> comments,
            Map<String, Property> properties,
            Map<String, Component> children,
            Set<Connection> connections) {
        return new Component(type, comments, properties, children, connections);
    }

    static Root root(String id, ComponentType type,
            List<Comment> comments,
            List<Command> commands,
            Map<String, Property> properties,
            Map<String, Component> children,
            Set<Connection> connections) {
        return new Root(id, type, comments, commands, properties, children, connections);
    }

    /**
     * A component element. A component has a type, and may have associated
     * comments, properties, children and/or connections.
     * <p>
     * To create a Component element, see {@link GraphBuilder}.
     */
    public static sealed class Component extends GraphElement {

        private final ComponentType type;
        private final List<Comment> comments;
        private final OrderedMap<String, Property> properties;
        private final OrderedMap<String, Component> children;
        private final OrderedSet<Connection> connections;

        private Component(ComponentType type,
                List<Comment> comments,
                Map<String, Property> properties,
                Map<String, Component> children,
                Set<Connection> connections) {
            this.type = Objects.requireNonNull(type);
            this.comments = List.copyOf(comments);
            this.properties = OrderedMap.copyOf(properties);
            this.children = OrderedMap.copyOf(children);
            this.connections = OrderedSet.copyOf(connections);
        }

        /**
         * Component type.
         *
         * @return type
         */
        public ComponentType type() {
            return type;
        }

        /**
         * Immutable list of comment elements.
         *
         * @return comments
         */
        public List<Comment> comments() {
            return comments;
        }

        /**
         * Immutable ordered map of property elements by ID.
         *
         * @return properties
         */
        public SequencedMap<String, Property> properties() {
            return properties;
        }

        /**
         * Immutable ordered map of child component elements by ID.
         *
         * @return children
         */
        public SequencedMap<String, Component> children() {
            return children;
        }

        /**
         * Immutable ordered set of connection elements.
         *
         * @return connections
         */
        public SequencedSet<Connection> connections() {
            return connections;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, comments, properties, children, connections);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof Component c
                    && c.getClass() == Component.class
                    && Objects.equals(this.type, c.type)
                    && Objects.equals(this.comments, c.comments)
                    && Objects.equals(this.properties, c.properties)
                    && Objects.equals(this.children, c.children)
                    && Objects.equals(this.connections, c.connections);
        }

        @Override
        public String toString() {
            return "Component{" + "type=" + type + ",\ncomments=" + comments
                    + ",\nproperties=" + properties + ",\nchildren=" + children
                    + ",\nconnections=" + connections + "}";
        }

    }

    /**
     * A root component element. A normal root component has a type and ID, and
     * may have associated comments, commands, properties, children and/or
     * connections. A synthetic root is used for subgraphs, and has an empty ID
     * and no properties.
     * <p>
     * To create a Root element, see {@link GraphBuilder}.
     */
    public static final class Root extends Component {

        static final ComponentType SYNTHETIC = ComponentType.of("root:synthetic");

        private final String id;
        private final List<Command> commands;

        private Root(String id, ComponentType type,
                List<Comment> comments,
                List<Command> commands,
                Map<String, Property> properties,
                Map<String, Component> children,
                Set<Connection> connections) {
            super(type, comments, properties, children, connections);
            if (id.isEmpty()) {
                if (!SYNTHETIC.equals(type)) {
                    throw new IllegalArgumentException("Invalid type for synthetic root");
                }
                this.id = "";
            } else {
                if (!ComponentAddress.isValidID(id)) {
                    throw new IllegalArgumentException("Invalid root ID");
                }
                this.id = id;
            }
            this.commands = List.copyOf(commands);
        }

        /**
         * Root ID.
         *
         * @return ID
         */
        public String id() {
            return id;
        }

        /**
         * Immutable list of commands.
         *
         * @return commands
         */
        public List<Command> commands() {
            return commands;
        }

        /**
         * Query whether the root element is synthetic.
         *
         * @return true if synthetic
         */
        public boolean isSynthetic() {
            return id.isEmpty();
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, commands, type(), comments(), properties(), children(), connections());
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof Root r
                    && r.getClass() == Root.class
                    && Objects.equals(id, r.id)
                    && Objects.equals(commands, r.commands)
                    && Objects.equals(this.type(), r.type())
                    && Objects.equals(this.comments(), r.comments())
                    && Objects.equals(this.properties(), r.properties())
                    && Objects.equals(this.children(), r.children())
                    && Objects.equals(this.connections(), r.connections());
        }

        @Override
        public String toString() {
            return "Root{" + "id=" + id + ",\ncommands=" + commands
                    + "type=" + type() + ",\ncomments=" + comments()
                    + ",\nproperties=" + properties() + ",\nchildren=" + children()
                    + ",\nconnections=" + connections() + "}";
        }

    }

    public static final class Command extends GraphElement {

        private final String command;
        private final List<Token> tokens;

        Command(String command, List<Token> tokens) {
            this.command = Objects.requireNonNull(command);
            this.tokens = List.copyOf(tokens);
        }

        public String command() {
            return command;
        }

        public List<Token> tokens() {
            return tokens;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(command);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof Command c
                    && Objects.equals(this.command, c.command);
        }

        @Override
        public String toString() {
            return "Command{" + "command=" + command + "}";
        }

    }

    public static final class Comment extends GraphElement {

        private final String text;

        private Comment(String text) {
            this.text = Objects.requireNonNull(text);
        }

        public String text() {
            return text;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(text);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof Comment c
                    && Objects.equals(this.text, c.text);
        }

        @Override
        public String toString() {
            return "Comment{" + "text=" + text + "}";
        }

    }

    public static final class Property extends GraphElement {

        private final Value value;

        private Property(Value value) {
            this.value = Objects.requireNonNull(value);
        }

        public Value value() {
            return value;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof Property p
                    && Objects.equals(this.value, p.value);
        }

        @Override
        public String toString() {
            return "Property{" + "value=" + value + "}";
        }

    }

    public static final class Connection extends GraphElement {

        private final org.praxislive.core.Connection value;

        private Connection(String sourceComponent, String sourcePort, String targetComponent, String targetPort) {
            value = org.praxislive.core.Connection.of(sourceComponent, sourcePort, targetComponent, targetPort);
        }

        public String sourceComponent() {
            return value.sourceComponent();
        }

        public String sourcePort() {
            return value.sourcePort();
        }

        public String targetComponent() {
            return value.targetComponent();
        }

        public String targetPort() {
            return value.targetPort();
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || obj instanceof Connection c
                    && Objects.equals(this.value, c.value);
        }

        @Override
        public String toString() {
            return "Connection{" + "sourceComponent=" + sourceComponent()
                    + ", sourcePort=" + sourcePort()
                    + ", targetComponent=" + targetComponent()
                    + ", targetPort=" + targetPort() + "}";
        }

    }

}
