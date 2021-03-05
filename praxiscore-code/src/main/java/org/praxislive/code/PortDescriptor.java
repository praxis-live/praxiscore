/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith.
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
package org.praxislive.code;

import org.praxislive.core.Port;
import org.praxislive.core.PortInfo;

/**
 * A PortDescriptor wraps a Port in a CodeContext. It includes ID, category,
 * order and info, as well as configuring the port on attachment and reset. The
 * underlying port may be configured from the previous iteration or carried
 * across.
 */
public abstract class PortDescriptor {

    public static enum Category {

        /**
         * Ports that correspond to input.
         */
        In,
        /**
         * Ports that correspond to actions (triggers).
         */
        Action,
        /**
         * Ports that correspond to output.
         */
        Out,
        /**
         * Ports that correspond to properties.
         */
        Property,
        /**
         * Ports that correspond to auxiliary input.
         */
        AuxIn,
        /**
         * Ports that correspond to auxiliary output.
         */
        AuxOut
    }

    private final String id;
    private final Category category;
    private final int index;

    /**
     * Create a PortDescriptor.
     *
     * @param id the ID (must be a valid port ID)
     * @param category the category
     * @param index the index within the category (used for ordering - must be
     * unique)
     */
    protected PortDescriptor(String id, Category category, int index) {
        this.id = id;
        this.category = category;
        this.index = index;
    }

    /**
     * Get the ID.
     *
     * @return id
     */
    public final String getID() {
        return id;
    }

    /**
     * Get the category.
     *
     * @return category
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Get the index.
     *
     * @return index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Configure the port for the provided context. The previous port with the
     * same ID is provided - it may be null or of a different type. If the
     * previous port cannot be carried over, then this method must handle
     * disconnecting and/or reconnecting as appropriate.
     * <p>
     * Note : any port passed in as previous will not be disposed
     *
     * @param context context being attached to
     * @param previous previous port with the same ID, may be null or different
     * type
     */
    // @TODO this method should take a PortDescriptor in future versions
    public abstract void attach(CodeContext<?> context, Port previous);

    /**
     * Get the wrapped port. Should only be called when attached - behaviour is
     * otherwise undefined.
     *
     * @return port
     */
    public abstract Port getPort();

    /**
     * Get port info.
     *
     * @return info
     */
    public abstract PortInfo getInfo();

    /**
     * Hook called to reset during attachment / detachment, or execution context
     * state changes. Full reset happens on execution context changes.
     *
     * @param full true if execution context state
     */
    public void reset(boolean full) {
    }

    /**
     * Deprecated hook - no op!
     *
     * @deprecated
     */
    @Deprecated
    public void stopping() {
    }

    /**
     * Hook called on code context disposal for any port descriptors not carried
     * over.
     */
    public void dispose() {
    }
}
