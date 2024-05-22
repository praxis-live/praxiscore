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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Connection;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 * A TreeWriter implementation that builds the data in the format specified by
 * {@link SerializableProtocol}.
 */
public final class MapTreeWriter implements TreeWriter {

    private static final String ANNOTATION_PREFIX = "%";
    private static final String CHILD_PREFIX = "@";

    private static final String TYPE_KEY = ANNOTATION_PREFIX + "type";
    private static final String INFO_KEY = ANNOTATION_PREFIX + "info";
    private static final String CONNECTIONS_KEY = ANNOTATION_PREFIX + "connections";

    private final Map<String, Value> map;

    private MapTreeWriter childWriter;
    private Set<Connection> connections;

    /**
     * Create a new writer.
     */
    public MapTreeWriter() {
        this.map = new LinkedHashMap<>();
        presetKeys();
    }

    @Override
    public MapTreeWriter writeAnnotation(String key, String value) {
        map.putIfAbsent("%" + key, PString.of(value));
        return this;
    }

    @Override
    public MapTreeWriter writeChild(String id, Consumer<TreeWriter> processor) {
        if (childWriter == null) {
            childWriter = new MapTreeWriter();
        }
        processor.accept(childWriter);
        map.put(CHILD_PREFIX + id, childWriter.build());
        childWriter.clear();
        return this;
    }

    @Override
    public MapTreeWriter writeConnection(Connection connection) {
        if (connections == null) {
            connections = new LinkedHashSet<>();
        }
        connections.add(Objects.requireNonNull(connection));
        return this;
    }

    @Override
    public MapTreeWriter writeInfo(ComponentInfo info) {
        map.put(INFO_KEY, info);
        return this;
    }

    @Override
    public MapTreeWriter writeProperty(String id, Value value) {
        map.put(id, value);
        return this;
    }

    @Override
    public MapTreeWriter writeType(ComponentType type) {
        map.put(TYPE_KEY, type);
        return this;
    }

    /**
     * Build the data map.
     *
     * @return data map
     */
    public PMap build() {
        map.computeIfPresent(TYPE_KEY, (k, v) -> v.isEmpty() ? null : v);
        map.computeIfPresent(INFO_KEY, (k, v) -> v.isEmpty() ? null : v);
        if (connections != null) {
            map.put(CONNECTIONS_KEY, connections.stream()
                    .map(Connection::dataArray)
                    .collect(PArray.collector()));
        }
        return PMap.ofMap(map);
    }

    /**
     * Clear the writer for reuse.
     *
     * @return this writer
     */
    public MapTreeWriter clear() {
        map.clear();
        if (childWriter != null) {
            childWriter.clear();
        }
        if (connections != null) {
            connections.clear();
        }
        presetKeys();
        return this;
    }

    private void presetKeys() {
        map.put(TYPE_KEY, PString.EMPTY);
        map.put(INFO_KEY, PString.EMPTY);
    }

}
