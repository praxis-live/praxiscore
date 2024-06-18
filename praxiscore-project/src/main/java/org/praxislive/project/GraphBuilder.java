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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.OrderedMap;
import org.praxislive.core.OrderedSet;
import org.praxislive.core.Value;

/**
 * Builders for graph component elements.
 */
public final class GraphBuilder {

    private GraphBuilder() {
    }

    /**
     * Create a component element builder for the provided component type.
     *
     * @param type component type
     * @return component builder
     */
    public static Component component(ComponentType type) {
        return new Component(type);
    }

    /**
     * Create a component element builder initialized with the type and
     * sub-elements of the provided component element.
     *
     * @param component base component
     * @return builder
     */
    public static Component component(GraphElement.Component component) {
        return new Component(component);
    }

    /**
     * Create a root element builder for the provided ID and type.
     *
     * @param id root ID
     * @param type root type
     * @return builder
     */
    public static Root root(String id, ComponentType type) {
        return new Root(id, type);
    }

    /**
     * Create a root element builder initialized with the ID, type and
     * sub-elements of the provided root element. If the provided root is
     * synthetic then the builder will be too.
     *
     * @param root base root
     * @return builder
     */
    public static Root root(GraphElement.Root root) {
        return new Root(root);
    }

    /**
     * Create a root element builder for a synthetic root.
     *
     * @return builder
     */
    public static Root syntheticRoot() {
        return new Root("", GraphElement.Root.SYNTHETIC);
    }

    /**
     * Abstract base class of component and root element builders.
     *
     * @param <B> builder type
     */
    @SuppressWarnings("unchecked")
    public static sealed abstract class Base<B extends Base<B>> {

        final ComponentType type;
        final List<GraphElement.Comment> comments;
        final SequencedMap<String, GraphElement.Property> properties;
        final SequencedMap<String, GraphElement.Component> children;
        final SequencedSet<GraphElement.Connection> connections;

        private Base(ComponentType type) {
            this.type = Objects.requireNonNull(type);
            comments = new ArrayList<>();
            properties = new LinkedHashMap<>();
            children = new LinkedHashMap<>();
            connections = new LinkedHashSet<>();
        }

        private Base(GraphElement.Component component) {
            this(component.type());
            comments.addAll(component.comments());
            properties.putAll(component.properties());
            children.putAll(component.children());
            connections.addAll(component.connections());
        }

        /**
         * Add a child component element.
         *
         * @param id child ID
         * @param child child element
         * @return this
         */
        public B child(String id, GraphElement.Component child) {
            if (!ComponentAddress.isValidID(id)) {
                throw new IllegalArgumentException(id + " is not a valid child ID");
            }
            children.put(id, Objects.requireNonNull(child));
            return (B) this;
        }

        /**
         * Add a child of the given type, configured by the passed in builder
         * consumer.
         *
         * @param id child ID
         * @param type child component type
         * @param constructor builder consumer to configure the component
         * @return this
         */
        public B child(String id, ComponentType type, Consumer<Component> constructor) {
            Component childBuilder = new Component(type);
            constructor.accept(childBuilder);
            return child(id, childBuilder.build());
        }

        /**
         * Add a comment element.
         *
         * @param text comment text
         * @return this
         */
        public B comment(String text) {
            return comment(GraphElement.comment(text));
        }

        /**
         * Add a comment element.
         *
         * @param comment comment element
         * @return this
         */
        public B comment(GraphElement.Comment comment) {
            comments.add(Objects.requireNonNull(comment));
            return (B) this;
        }

        /**
         * Add a connection element.
         *
         * @param sourceComponent source component ID
         * @param sourcePort source port ID
         * @param targetComponent target component ID
         * @param targetPort target port ID
         * @return this
         */
        public B connection(String sourceComponent, String sourcePort,
                String targetComponent, String targetPort) {
            return connection(GraphElement.connection(sourceComponent, sourcePort,
                    targetComponent, targetPort));
        }

        /**
         * Add a connection element.
         *
         * @param connection connection element
         * @return this
         */
        public B connection(GraphElement.Connection connection) {
            connections.add(Objects.requireNonNull(connection));
            return (B) this;
        }

        /**
         * Add a property element.
         *
         * @param id property ID
         * @param value property value
         * @return this
         */
        public B property(String id, Value value) {
            return property(id, GraphElement.property(value));
        }

        /**
         * Add a property element.
         *
         * @param id property ID
         * @param property property element
         * @return this
         */
        public B property(String id, GraphElement.Property property) {
            if (!ControlAddress.isValidID(id)) {
                throw new IllegalArgumentException(id + " is not a valid property ID");
            }
            properties.put(id, Objects.requireNonNull(property));
            return (B) this;
        }

        /**
         * Clear the existing children.
         *
         * @return this
         */
        public B clearChildren() {
            children.clear();
            return (B) this;
        }

        /**
         * Clear the existing comments.
         *
         * @return this
         */
        public B clearComments() {
            comments.clear();
            return (B) this;
        }

        /**
         * Clear the existing connections.
         *
         * @return this
         */
        public B clearConnections() {
            connections.clear();
            return (B) this;
        }

        /**
         * Clear the existing properties.
         *
         * @return this
         */
        public B clearProperties() {
            properties.clear();
            return (B) this;
        }

        /**
         * Transform the existing children. The transform function is called
         * with a stream of the existing child map entries, and should return a
         * list of desired child elements. The returned list will be used to
         * replace the existing children. The map entries in the stream are
         * immutable, but the component elements may be reused.
         *
         * @param transform children transform function
         * @return this
         */
        public B transformChildren(
                Function<Stream<Map.Entry<String, GraphElement.Component>>, List<Map.Entry<String, GraphElement.Component>>> transform) {
            var transformed = transform.apply(children.entrySet().stream());
            clearChildren();
            transformed.forEach(c -> child(c.getKey(), c.getValue()));
            return (B) this;
        }

        /**
         * Transform the existing comments. The transform function is called
         * with a stream of the existing comment elements, and should return a
         * list of desired comment elements. The returned list will be used to
         * replace the existing comments.
         *
         * @param transform comment transform function
         * @return this
         */
        public B transformComments(
                Function<Stream<GraphElement.Comment>, List<GraphElement.Comment>> transform) {
            var transformed = transform.apply(comments.stream());
            clearComments();
            transformed.forEach(c -> comment(c));
            return (B) this;
        }

        /**
         * Transform the existing connections. The transform function is called
         * with a stream of the existing connection elements, and should return
         * a list of desired connection elements. The returned list will be used
         * to replace the existing connections.
         *
         * @param transform connection transform function
         * @return this
         */
        public B transformConnections(
                Function<Stream<GraphElement.Connection>, List<GraphElement.Connection>> transform) {
            var transformed = transform.apply(connections.stream());
            clearConnections();
            transformed.forEach(c -> connection(c));
            return (B) this;
        }

        /**
         * Transform the existing properties. The transform function is called
         * with a stream of the existing property map entries, and should return
         * a list of desired property elements. The returned list will be used
         * to replace the existing properties. The map entries in the stream are
         * immutable, but the property elements may be reused.
         *
         * @param transform property transform function
         * @return this
         */
        public B transformProperties(
                Function<Stream<Map.Entry<String, GraphElement.Property>>, List<Map.Entry<String, GraphElement.Property>>> transform) {
            var transformed = transform.apply(properties.entrySet().stream());
            clearProperties();
            transformed.forEach(p -> property(p.getKey(), p.getValue()));
            return (B) this;
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
         * Immutable snapshot of comments.
         *
         * @return comments
         */
        public List<GraphElement.Comment> comments() {
            return List.copyOf(comments);
        }

        /**
         * Immutable snapshot of properties.
         *
         * @return properties
         */
        public SequencedMap<String, GraphElement.Property> properties() {
            return OrderedMap.copyOf(properties);
        }

        /**
         * Immutable snapshot of children.
         *
         * @return children
         */
        public SequencedMap<String, GraphElement.Component> children() {
            return OrderedMap.copyOf(children);
        }

        /**
         * Immutable snapshot of connections.
         *
         * @return connections
         */
        public SequencedSet<GraphElement.Connection> connections() {
            return OrderedSet.copyOf(connections);
        }
    }

    /**
     * Component element builder.
     */
    public static final class Component extends Base<Component> {

        private Component(ComponentType type) {
            super(type);
        }

        private Component(GraphElement.Component component) {
            super(component);
        }

        /**
         * Build a component element from this builder.
         *
         * @return created component element
         */
        public GraphElement.Component build() {
            return GraphElement.component(type, comments, properties, children, connections);
        }

    }

    /**
     * Root element builder.
     */
    public static final class Root extends Base<Root> {

        private final String id;
        private final List<GraphElement.Command> commands;

        private Root(String id, ComponentType type) {
            super(type);
            this.id = id;
            this.commands = new ArrayList<>();
        }

        private Root(GraphElement.Root root) {
            super(root);
            this.id = root.id();
            this.commands = new ArrayList<>(root.commands());
        }

        /**
         * Add a command element.
         *
         * @param command command element
         * @return this
         */
        public Root command(GraphElement.Command command) {
            commands.add(Objects.requireNonNull(command));
            return this;
        }

        /**
         * Add a command element.
         *
         * @param command command line
         * @return this
         */
        public Root command(String command) {
            return command(GraphElement.command(command));
        }

        /**
         * Clear the existing commands.
         *
         * @return this
         */
        public Root clearCommands() {
            commands.clear();
            return this;
        }

        @Override
        public Root property(String id, GraphElement.Property property) {
            if (isSynthetic()) {
                throw new IllegalStateException("Synthetic roots cannot have properties.");
            }
            return super.property(id, property);
        }

        /**
         * Transform the existing commands. The transform function is called
         * with a stream of the existing command elements, and should return a
         * list of desired command elements. The returned list will be used to
         * replace the existing commands.
         *
         * @param transform command transform function
         * @return this
         */
        public Root transformCommands(
                Function<Stream<GraphElement.Command>, List<GraphElement.Command>> transform) {
            var transformed = transform.apply(commands.stream());
            clearCommands();
            transformed.forEach(c -> command(c));
            return this;
        }

        /**
         * Immutable snapshot of commands.
         *
         * @return commands
         */
        public List<GraphElement.Command> commands() {
            return List.copyOf(commands);
        }

        /**
         * Root ID.
         *
         * @return id
         */
        public String id() {
            return id;
        }

        /**
         * Query whether the root is synthetic.
         *
         * @return true if synthetic
         */
        public boolean isSynthetic() {
            return id.isEmpty();
        }

        /**
         * Build a root element from this builder.
         *
         * @return created root element
         */
        public GraphElement.Root build() {
            return GraphElement.root(id, type, comments, commands,
                    properties, children, connections);
        }

    }

}
