/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
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

/**
 * Annotate a method as a function. Functions will be exposed as controls on the
 * component.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FN {

    /**
     * Relative weight compared to other @FN elements. Functions will be sorted
     * by weight, and then alphabetically. Higher weight elements will sort
     * after lower weight elements.
     *
     * @return weight
     */
    int value() default 0;

    /**
     * Annotate a method as a {@link Watch} function.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Watch {

        /**
         * The mime type of the returned data. Will be included in the Watch
         * info.
         *
         * @return data mime type
         */
        String mime();

        /**
         * Optional name of a port on the component to relate the watch data to.
         *
         * @return related port ID
         */
        String relatedPort() default "";

        /**
         * Relative weight compared to other @FN elements. Functions will be
         * sorted by weight, and then alphabetically. Higher weight elements
         * will sort after lower weight elements.
         *
         * @return weight
         */
        int weight() default 0;

    }

}
