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

import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;

/**
 * A ControlDescriptor wraps a Control in a CodeContext. It includes ID,
 * category, order and info, as well as configuring the control on attachment
 * and reset. The underlying control may be configured from the previous
 * iteration or carried across.
 */
public non-sealed abstract class ControlDescriptor<T extends ControlDescriptor<T>> extends Descriptor<T> {

    /**
     * Categories of control, which also affects broad ordering of controls.
     */
    public static enum Category {

        /**
         * Internal controls that are part of the base context implementation,
         * eg. the info and code properties.
         */
        Internal,
        /**
         * Synthetic controls used for injected resources that require a control
         * endpoint. Excluded from info and display.
         */
        Synthetic,
        /**
         * Controls that correspond to input.
         */
        In,
        /**
         * Controls that correspond to actions.
         */
        Action,
        /**
         * Controls that correspond to properties.
         */
        Property,
        /**
         * Controls that correspond to auxiliary input.
         */
        AuxIn,
        /**
         * Controls that represent functions.
         */
        Function
    }

    private final Category category;
    private final int index;

    /**
     * Create a ControlDescriptor.
     *
     * @param type type of the descriptor
     * @param id the ID (must be a valid control ID)
     * @param category the category
     * @param index the index within the category (used for ordering - must be
     * unique)
     */
    protected ControlDescriptor(Class<T> type, String id, Category category, int index) {
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
     * Get the control info for this control.
     *
     * @return info
     */
    public abstract ControlInfo controlInfo();

    /**
     * Get the wrapped control. Should only be called when attached - behaviour
     * is otherwise undefined.
     *
     * @return control
     */
    public abstract Control control();

}
