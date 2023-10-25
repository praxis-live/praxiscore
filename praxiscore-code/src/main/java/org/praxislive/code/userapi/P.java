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
package org.praxislive.code.userapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a field as a property. A control and port will be automatically
 * created so that values of the field can be queried or set from the UI or
 * other components. Field values will also be stored in the project, and
 * available when copying or exporting, unless the field is read-only or
 * transient.
 * <p>
 * The @P annotation may be used on fields of type {@link Property}, or any
 * field type that can be backed by a Property, such as String, double, float,
 * int, boolean, any Value type, any enum, etc.
 * <p>
 * The @P annotation can also be used on fields that represent a represent
 * loadable resource, in which case the port / control will accept a URL and the
 * field type will be the loaded resource. See specific custom code types for
 * what resources they support loading.
 * <p>
 * Use the various {@link Type} annotations to set default values, allowed
 * values, ranges, etc. Use {@link Config.Port @Config.Port(false)} to suppress
 * automatic creation of a port. Use {@link Transient @Transient} to stop the
 * field value being saved as part of the project. Use
 * {@link ReadOnly @ReadOnly} to stop the field being settable externally (it
 * can still be set in code).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface P {

    /**
     * Relative weight compared to other @P elements. Elements will be sorted by
     * weight, and then alphabetically. Higher weight elements will sort after
     * lower weight elements.
     *
     * @return weight
     */
    int value() default 0;

}
