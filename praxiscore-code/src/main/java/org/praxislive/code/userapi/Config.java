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
package org.praxislive.code.userapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.praxislive.core.ComponentInfo;

/**
 * Various annotations and support to control component configuration.
 *
 */
public final class Config {

    private Config() {
    }

    /**
     * Control automatic port creation for properties, triggers, etc.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Port {

        /**
         * Whether or not to create a port.
         *
         * @return false to suppress port creation
         */
        boolean value();
    }

    /**
     * Mark a feature as "preferred" - particularly important for presenting to
     * humans.
     * <p>
     * This will add a key to the info for this feature. It is up to an editor
     * whether to use or ignore this key (eg. the PraxisLIVE graph editor will
     * show properties marked this way on the graph itself).
     * <p>
     * This option is deprecated in favour of {@link Expose}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Deprecated(forRemoval = true)
    public @interface Preferred {

    }

    /**
     * Default list of control IDs to give extra priority to exposing to the
     * user.
     * <p>
     * The list of controls is added under the key
     * {@link ComponentInfo#KEY_EXPOSE} in the component info.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Expose {

        /**
         * List of control IDs to expose.
         *
         * @return list of IDs
         */
        String[] value();
    }

}
