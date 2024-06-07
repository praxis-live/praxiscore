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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.SerializableProtocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;

import static org.praxislive.project.ModelUtils.validateContext;

/**
 * Model for graph and subgraph scripts, encompassing the element tree and
 * related information.
 * <p>
 * A graph model can be parsed from a script (eg. contents of .pxr / .pxg file),
 * or created from the serialization data returned from
 * {@link SerializableProtocol}. A graph model can also be written back out to a
 * script for execution or saving to a file.
 * <p>
 * A graph model, and the underlying tree, are immutable. Transformative methods
 * return a new model instance.
 */
public final class GraphModel {

    private final GraphElement.Root root;
    private final URI context;

    private GraphModel(GraphElement.Root root, URI context) {
        this.root = root;
        this.context = context;
    }

    /**
     * Access the root of the element tree. For a subgraph, the root will be
     * synthetic.
     *
     * @return root element
     */
    public GraphElement.Root root() {
        return root;
    }

    /**
     * Access the optional context (eg. working dir) for resolving relative file
     * values.
     *
     * @return optional context
     */
    public Optional<URI> context() {
        return Optional.ofNullable(context);
    }

    /**
     * Create a new graph model based on this one, with a different context. The
     * context is used to relativize resources when writing. Use {@code null} to
     * create a model without context.
     *
     * @param context new resource context
     * @return new graph model
     */
    public GraphModel withContext(URI context) {
        return new GraphModel(root, validateContext(context));
    }

    /**
     * Create a new graph model based on this one, with a renamed root. All
     * other elements are kept the same. This method will throw an exception if
     * called on a (subgraph) model with a synthetic root.
     *
     * @param id new root ID
     * @return new graph model
     */
    public GraphModel withRename(String id) {
        if (root.isSynthetic()) {
            throw new IllegalStateException("Cannot rename synthetic root");
        }
        GraphBuilder.Root builder = GraphBuilder.root(id, root.type());
        root.comments().forEach(builder::comment);
        root.commands().forEach(builder::command);
        root.properties().forEach(builder::property);
        root.children().forEach(builder::child);
        root.connections().forEach(builder::connection);
        return new GraphModel(builder.build(), context);
    }

    /**
     * Create a new graph model based on this one after applying the provided
     * transform function. The builder passed into the transform will be
     * pre-configured with all the elements of this model.
     *
     * @param transform transforming builder consumer
     * @return new graph model
     */
    public GraphModel withTransform(Consumer<GraphBuilder.Root> transform) {
        GraphBuilder.Root builder = GraphBuilder.root(root);
        transform.accept(builder);
        return new GraphModel(builder.build(), context);
    }

    /**
     * Write the model as a script to the given target.
     *
     * @param target write destination
     * @throws IOException
     */
    public void write(Appendable target) throws IOException {
        GraphWriter.write(this, target);
    }

    /**
     * Write the graph model to a String. This is shorthand for passing in a
     * {@link StringBuilder} to {@link #write(java.lang.Appendable)}.
     * <p>
     * The output of this method is suitable for parsing back into a model, as
     * distinct from the output of {@link #toString()}.
     *
     * @return model as script
     */
    public String writeToString() {
        StringBuilder sb = new StringBuilder();
        try {
            write(sb);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this
                || obj instanceof GraphModel other
                && Objects.equals(root, other.root)
                && Objects.equals(context, other.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, context);
    }

    @Override
    public String toString() {
        return "GraphModel {\n  Context : " + context + "\n  Graph :\n" + writeToString().indent(4) + "\n}";
    }

    /**
     * Create a graph model from the serialization data of a single component.
     * The data should be in the format specified by
     * {@link SerializableProtocol}. The graph model will consist of a synthetic
     * root with a single child component element with the given ID and data.
     *
     * @param componentID id of the component
     * @param data serialization data
     * @return created graph model
     */
    public static GraphModel fromSerializedComponent(String componentID, PMap data) {
        GraphElement.Root root = GraphBuilder.syntheticRoot()
                .child(componentID, typeFromData(data),
                        cmp -> buildSerializedComponent(cmp, data, true, null))
                .build();
        return new GraphModel(root, null);
    }

    /**
     * Create a graph model from the serialization data of a complete root. The
     * data should be in the format specified by {@link SerializableProtocol}.
     *
     * @param rootID if of the root
     * @param data serialization data
     * @return created graph model
     */
    public static GraphModel fromSerializedRoot(String rootID, PMap data) {
        GraphBuilder.Root rootBuilder = GraphBuilder.root(rootID, typeFromData(data));
        buildSerializedComponent(rootBuilder, data, true, null);
        return new GraphModel(rootBuilder.build(), null);
    }

    /**
     * Create a graph model from the serialization data of a container. The data
     * should be in the format specified by {@link SerializableProtocol}. The
     * graph model will consist of a synthetic root with all the children and
     * connections of the container. Properties of the container itself will be
     * ignored.
     *
     * @param data container serialization data
     * @return created graph model
     */
    public static GraphModel fromSerializedSubgraph(PMap data) {
        return fromSerializedSubgraph(data, null);
    }

    /**
     * Create a graph model from the serialization data of a container. The data
     * should be in the format specified by {@link SerializableProtocol}. The
     * graph model will consist of a synthetic root with all the children of the
     * container that pass the given child ID filter. Connections will be
     * filtered to those between included components. Properties of the
     * container itself will be ignored.
     *
     * @param data container serialization data
     * @param filter child ID filter
     * @return created graph model
     */
    public static GraphModel fromSerializedSubgraph(PMap data, Predicate<String> filter) {
        GraphBuilder.Root rootBuilder = GraphBuilder.syntheticRoot();
        buildSerializedComponent(rootBuilder, data, false, filter);
        return new GraphModel(rootBuilder.build(), null);
    }

    /**
     * Create a graph model of the provided root element.
     *
     * @param root root element
     * @return created graph model
     */
    public static GraphModel of(GraphElement.Root root) {
        return new GraphModel(Objects.requireNonNull(root), null);
    }

    /**
     * Create a graph model of the provided root element and context.
     *
     * @param root root element
     * @param context resource context
     * @return created graph model
     */
    public static GraphModel of(GraphElement.Root root, URI context) {
        return new GraphModel(Objects.requireNonNull(root),
                validateContext(Objects.requireNonNull(context)));
    }

    /**
     * Parse the given graph script into a graph model. The script must be a
     * valid full root graph.
     *
     * @param graph graph script
     * @return created graph model
     * @throws ParseException if the graph is invalid
     */
    public static GraphModel parse(String graph) throws ParseException {
        GraphElement.Root root = GraphParser.parse(graph);
        return new GraphModel(root, null);
    }

    /**
     * Parse the given graph script into a graph model. Relative resources will
     * be resolved against the provided context. The script must be a full root
     * graph.
     *
     * @param context resource context
     * @param graph graph script
     * @return created graph model
     * @throws ParseException if the graph is invalid
     */
    public static GraphModel parse(URI context, String graph) throws ParseException {
        GraphElement.Root root = GraphParser.parse(validateContext(context), graph);
        return new GraphModel(root, context);
    }

    /**
     * Parse the given subgraph script into a graph model. The script must be a
     * valid subgraph script.
     *
     * @param graph subgraph script
     * @return created graph model
     * @throws ParseException if the subgraph is invalid
     */
    public static GraphModel parseSubgraph(String graph) throws ParseException {
        GraphElement.Root root = GraphParser.parseSubgraph(graph);
        return new GraphModel(root, null);
    }

    /**
     * Parse the given subgraph script into a graph model. Relative resources
     * will be resolved against the provided context. The script must be a valid
     * subgraph script.
     *
     * @param context resource context
     * @param graph subgraph script
     * @return created graph model
     * @throws ParseException if the subgraph is invalid
     */
    public static GraphModel parseSubgraph(URI context, String graph) throws ParseException {
        GraphElement.Root root = GraphParser.parseSubgraph(validateContext(context), graph);
        return new GraphModel(root, context);
    }

    private static void buildSerializedComponent(GraphBuilder.Base<?> component,
            PMap data,
            boolean includeProperties,
            Predicate<String> filter) {
        ComponentInfo info = Optional.ofNullable(data.get("%info"))
                .flatMap(ComponentInfo::from)
                .orElse(null);
        data.asMap().forEach((key, value) -> {
            if (key.startsWith("@")) {
                String id = key.substring(1);
                if (filter == null || filter.test(id)) {
                    PMap childData = PMap.from(value).orElseThrow(IllegalArgumentException::new);
                    component.child(id, typeFromData(childData),
                            cmp -> buildSerializedComponent(cmp, childData, true, null));
                }
            } else if (key.startsWith("%")) {
                if ("%connections".equals(key)) {
                    buildConnectionsList(value, filter).forEach(c -> component.connection(c));
                }
            } else if (includeProperties && ControlAddress.isValidID(key)) {
                component.property(key, coerceTypeFromInfo(key, info, value));
            }
        });

    }

    private static ComponentType typeFromData(PMap data) {
        return Optional.ofNullable(data.get("%type"))
                .flatMap(ComponentType::from)
                .orElseThrow(() -> new IllegalArgumentException("No type in data map"));
    }

    private static Value coerceTypeFromInfo(String id, ComponentInfo info, Value value) {
        Value.Type<?> type = Optional.ofNullable(info.controlInfo(id))
                .map(ControlInfo::inputs)
                .filter(ins -> !ins.isEmpty())
                .map(ins -> ins.get(0))
                .flatMap(in -> Value.Type.fromName(in.argumentType()))
                .orElse(null);
        if (type != null) {
            Value coerced = type.converter().apply(value).orElse(null);
            if (coerced != null) {
                return coerced;
            }
        }
        return value;
    }

    private static List<GraphElement.Connection> buildConnectionsList(Value connections,
            Predicate<String> filter) {
        return PArray.from(connections).orElseThrow(() -> new IllegalArgumentException("Connections is not an array : " + connections))
                .stream()
                .map(connection -> {
                    PArray arr = PArray.from(connection)
                            .filter(a -> a.size() == 4)
                            .orElseThrow(() -> new IllegalArgumentException("Not a valid connection : " + connection));
                    String sourceComponent = arr.get(0).toString();
                    String sourcePort = arr.get(1).toString();
                    String targetComponent = arr.get(2).toString();
                    String targetPort = arr.get(3).toString();
                    return GraphElement.connection(
                            sourceComponent, sourcePort, targetComponent, targetPort);
                })
                .filter(c -> filter == null ? true
                : filter.test(c.sourceComponent()) && filter.test(c.targetComponent()))
                .toList();

    }

}
