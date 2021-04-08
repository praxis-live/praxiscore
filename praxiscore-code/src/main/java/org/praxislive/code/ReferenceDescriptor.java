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

/**
 * A ReferenceDescriptor is used for wrapping references carried across from one
 * iteration of code context to the next that do not correspond to controls or
 * ports. They include an ID, but no ordering or categories.
 *
 */
public abstract class ReferenceDescriptor {

    private final String id;

    /**
     * Create a ReferenceDescriptor with the provided ID.
     *
     * @param id reference id
     */
    protected ReferenceDescriptor(String id) {
        this.id = id;
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
     * Configure the reference for the provided context during attachment. The
     * previous reference descriptor with the same ID is provided - it may be
     * null or of a different type. This method should handle disposal of the
     * previous reference descriptor in cases where necessary (eg. not same
     * type).
     *
     * @param context context being attached to
     * @param previous previous reference descriptor with same ID, may be null
     * or different type
     */
    public abstract void attach(CodeContext<?> context, ReferenceDescriptor previous);

    /**
     * Hook called to reset during attachment / detachment, or execution context
     * state changes. Full reset happens on execution context changes.
     *
     * @param full true if execution context state
     */
    public void reset(boolean full) {
    }

    /**
     * Hook called on code context disposal for any references descriptor not
     * carried over.
     */
    public void dispose() {
    }

}
