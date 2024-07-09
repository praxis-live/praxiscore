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
package org.praxislive.code;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.praxislive.core.ComponentType;

/**
 * Shared APIs for code root containers and code containers.
 */
public interface ContainerDelegateAPI {

    /**
     * Annotation to filter available child types and register custom child
     * types.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface SupportedTypes {

        /**
         * Filter to apply to available types. The filter is a glob style String
         * pattern supporting wildcards - eg.
         * {@code core:container|core:math:*}.
         * <p>
         * If {@link #system()} is {@code false} then this filter had no effect.
         *
         * @return type filter
         */
        String filter() default "";

        /**
         * Whether to include system registered component types.
         *
         * @return include system registered types
         */
        boolean system() default true;

        /**
         * Registered custom component types.
         *
         * @return custom types
         */
        CustomType[] custom() default {};

    }

    /**
     * Register a custom component type. The type must be a valid String
     * representation of a {@link ComponentType}. The base must be a suitable
     * {@link CodeDelegate} subclass. The base may be included in shared code.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomType {

        /**
         * Component type as String. The value must be a valid representation of
         * a {@link ComponentType}.
         *
         * @return component type
         */
        String type();

        /**
         * The base code delegate. The base may be included in shared code.
         *
         * @return base delegate
         */
        Class<? extends CodeDelegate> base();
    }

}
