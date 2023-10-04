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

import java.util.Objects;

/**
 * Common interface of reference, port and control descriptors.
 *
 * @param <T> direct subtype
 */
public sealed abstract class Descriptor<T extends Descriptor<T>>
        permits ReferenceDescriptor, ControlDescriptor, PortDescriptor {

    private final Class<T> type;
    private final String id;

    protected Descriptor(Class<T> type, String id) {
        this.type = Objects.requireNonNull(type);
        this.id = Objects.requireNonNull(id);
    }

    /**
     * Get the descriptor ID.
     *
     * @return id
     */
    public final String id() {
        return id;
    }

    public final Class<T> type() {
        return type;
    }

    /**
     * Attach the descriptor to the provided context. The previous descriptor
     * with the same ID and type is provided, if it exists. The implementation
     * should handle disposal of the previous descriptor if required.
     * <p>
     * The {@link #onInit()} hook will be called after attachment if the code
     * context is active, or when the code context becomes active.
     *
     * @param context code context
     * @param previous previous descriptor or null
     */
    public abstract void attach(CodeContext<?> context, T previous);

    /**
     * Hook called when the descriptor becomes part of an active code context,
     * or after anything triggers a reset while active. The default
     * implementation does nothing.
     */
    public void onInit() {

    }

    /**
     * Hook called when the code context becomes active due to the execution
     * context state changing. The {@link #onInit()} hook will have been called
     * prior to this hook. The default implementation does nothing.
     */
    public void onStart() {

    }

    /**
     * Hook called when the code context becomes inactive due to the execution
     * context state changing. This hook will be called before the
     * {@link #onReset()} hook. The default implementation does nothing.
     */
    public void onStop() {

    }

    /**
     * Hook called when the code context becomes inactive, including on context
     * swap, or if anything else triggers a reset while active. The default
     * implementation does nothing.
     */
    public void onReset() {

    }

    /**
     * Hook called when the descriptor is disposed, when the code context is
     * changing or the component is being removed. This hook is not called by
     * default when a descriptor is passed in as the previous instance in
     * {@link #attach(org.praxislive.code.CodeContext, org.praxislive.code.Descriptor)}.
     * The default implementation does nothing.
     */
    public void dispose() {

    }
    
    void handleAttach(CodeContext<?> context, Descriptor<?> previous) {
        if (previous != null) {
            if (type().isInstance(previous)) {
                attach(context, type().cast(previous));
            } else {
                previous.dispose();
            }
        } else {
            attach(context, null);
        }
    }

}
