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
package org.praxislive.code;

import org.praxislive.core.Port;
import org.praxislive.core.PortInfo;

/**
 * A PortDescriptor wraps a Port in a CodeContext. It includes ID, category,
 * order and info, as well as configuring the port on attachment and reset. The
 * underlying port may be configured from the previous iteration or carried
 * across.
 */
public non-sealed abstract class PortDescriptor<T extends PortDescriptor<T>> extends Descriptor<T> {

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

    private final Category category;
    private final int index;

    /**
     * Create a PortDescriptor.
     *
     * @param type descriptor type
     * @param id the ID (must be a valid port ID)
     * @param category the category
     * @param index the index within the category (used for ordering - must be
     * unique)
     */
    protected PortDescriptor(Class<T> type, String id, Category category, int index) {
        super(type, id);
        this.category = category;
        this.index = index;
    }

    /**
     * Get the category.
     *
     * @return category
     */
    public Category category() {
        return category;
    }

    /**
     * Get the index.
     *
     * @return index
     */
    public int index() {
        return index;
    }

    /**
     * Get the wrapped port. Should only be called when attached - behaviour is
     * otherwise undefined.
     *
     * @return port
     */
    public abstract Port port();

    /**
     * Get port info.
     *
     * @return info
     */
    public abstract PortInfo portInfo();

    /**
     * Dispose the port descriptor. The default implementation calls
     * {@link Port#disconnectAll()}. When overriding this method, call the super
     * implementation or otherwise ensure that the port is disconnected.
     */
    @Override
    public void dispose() {
        var port = port();
        if (port != null) {
            port.disconnectAll();
        }
    }

}
