/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Value;
import org.praxislive.core.types.PNumber;

/**
 * Annotations for setting meta-data about elements such as fields and
 * parameters, for use in property and function information. Values may also
 * affect input validation.
 * <p>
 * The base {@code @Type} annotation can be used for setting information about
 * any value type. Type specific annotations give access to additional
 * properties and validation.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Type {

    /**
     * Optional supported Value class. Leave unspecified to support all value
     * types or infer from the context (eg. field or parameter type).
     *
     * @return optional value type as class
     */
    Class<? extends Value> value() default Value.class;

    /**
     * Properties to add to the {@link ArgumentInfo}. The value is a String
     * array, which must contain an even number of values arranged as key
     * followed by value.
     * <p>
     * Properties added here may not affect input validation.
     *
     * @return optional argument info properties
     */
    java.lang.String[] properties() default {};

    /**
     * The optional default value for a property, specified as the String
     * representation of the value.
     *
     * @return optional default value
     */
    java.lang.String def() default "";

    /**
     * Annotate an element as a floating point number, with optional range and
     * skew.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Number {

        /**
         * Minimum supported value.
         *
         * @return optional minimum
         */
        double min() default PNumber.MIN_VALUE;

        /**
         * Maximum supported value.
         *
         * @return optional maximum
         */
        double max() default PNumber.MAX_VALUE;

        /**
         * Skew of the range. This may be used to affect presentation of the
         * element value. The skew is expressed as a power curve. The default
         * skew of 1 will give linear presentation. A skew greater than 1 will
         * give more emphasis to values at the lower end of the range, a skew
         * less than 1 to values higher in the range.
         *
         * @return optional skew
         */
        double skew() default 1;

        /**
         * The default value for a property. If unspecified this is zero.
         *
         * @return default value
         */
        double def() default 0;
    }

    /**
     * Annotate an element as an integer number, with optional range and
     * suggested values.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Integer {

        /**
         * Minimum supported value.
         *
         * @return optional minimum
         */
        int min() default PNumber.MIN_VALUE;

        /**
         * Maximum supported value.
         *
         * @return optional maximum
         */
        int max() default PNumber.MAX_VALUE;

        /**
         * The default value for a property. If unspecified this is zero.
         *
         * @return default value
         */
        int def() default 0;

        /**
         * Suggested values. May be used to present suggested values to the
         * user.
         *
         * @return optional suggested values
         */
        int[] suggested() default {};
    }

    /**
     * Annotate an element as text, with optional mime type, template, allowed
     * or suggested values.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface String {

        /**
         * Allowed values. Mark the element as only accepting these values. May
         * be used in validation or to present to the user.
         *
         * @return optional list of allowed values
         */
        java.lang.String[] allowed() default {};

        /**
         * Suggested values. May be used to present suggested common values to
         * the user.
         *
         * @return optional list of suggested values
         */
        java.lang.String[] suggested() default {};

        /**
         * Flag the empty value as representing a default state. May be used to
         * present state to the user.
         *
         * @return optional empty is default flag
         */
        boolean emptyIsDefault() default false;

        /**
         * Mime type of the text. May be used in user interfaces to open the
         * text value for editing in a suitable editor.
         *
         * @return optional mime type
         */
        java.lang.String mime() default "";

        /**
         * The default value for a property. If unspecified this is an empty
         * String.
         *
         * @return default value
         */
        java.lang.String def() default "";

        /**
         * Text template for editing if the value is empty. May be used in user
         * interfaces to pre-fill the editor with default text when editing an
         * empty value.
         *
         * @return optional template text
         */
        java.lang.String template() default "";
    }

    /**
     * Annotate an element as boolean.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Boolean {

        /**
         * The default value for a property. If unspecified this is
         * {@code false}.
         *
         * @return default value
         */
        boolean def() default false;
    }

    /**
     * Annotate an element as a resource - a file or network link.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Resource {

    }

}
