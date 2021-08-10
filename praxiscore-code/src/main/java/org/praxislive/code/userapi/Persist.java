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
package org.praxislive.code.userapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a field to be persisted between code changes. Unlike injected
 * fields, persisted fields are not automatically created. Fields may be of any
 * type, and field types must match exactly between iterations (including
 * generics) for values to be persisted.
 * <p>
 * By default, values will be reset when the root is stopped (idled), and
 * {@link AutoCloseable} references will be closed when disposed.
 * <p>
 * Persisted fields should be used sparingly and with care!
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Persist {

    /**
     * Control whether AutoCloseable field values are closed on disposal.
     *
     * @return auto close on dispose
     */
    boolean autoClose() default true;

    /**
     * Control whether to reset values on root stop (idle).
     *
     * @return auto reset values
     */
    boolean autoReset() default true;

}
