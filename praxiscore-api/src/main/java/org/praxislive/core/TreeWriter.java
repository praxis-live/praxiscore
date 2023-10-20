/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2023 Neil C Smith.
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
package org.praxislive.core;

import java.util.function.Consumer;

/**
 * Interface for writing the configuration and state of a Component, or tree of
 * components, into a data structure.
 * <p>
 * A Component should write its type, component info and properties to the
 * writer, in that order. A Container should additionally write its children and
 * connections. Arbitrary annotations may be added.
 * <p>
 * The format and completeness of the output data is not specified.
 */
public interface TreeWriter {

    /**
     * Write the component type.
     *
     * @param type component type
     * @return this writer
     */
    public TreeWriter writeType(ComponentType type);

    /**
     * Write the component info.
     *
     * @param info component info
     * @return this writer
     */
    public TreeWriter writeInfo(ComponentInfo info);

    /**
     * Write a custom annotation.
     *
     * @param key annotation id
     * @param value annotation value
     * @return this writer
     */
    public TreeWriter writeAnnotation(String key, String value);

    /**
     * Write the value of a property.
     * <p>
     * A component may write properties that are at their defaults if this is
     * more efficient. Consumers of the resulting data may verify against the
     * provided info.
     *
     * @param id
     * @param value
     * @return this writer
     */
    public TreeWriter writeProperty(String id, Value value);

    /**
     * Write the data for a child component.
     * <p>
     * The processor will be called during the execution of this method. The
     * passed in writer may be the same writer, configured to refer to the
     * child, or a new writer instance. The writer should not be cached or used
     * outside of the processor.
     *
     * @param id child ID
     * @param processor writer consumer to process and write child
     * @return this writer
     */
    public TreeWriter writeChild(String id, Consumer<TreeWriter> processor);

    /**
     * Write a port connection between two child component.
     *
     * @param connection port connection
     * @return this writer
     */
    public TreeWriter writeConnection(Connection connection);

}
