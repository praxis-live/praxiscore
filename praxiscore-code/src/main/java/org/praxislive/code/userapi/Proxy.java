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
 * Mark a field referencing an interface implementation to be wrapped by an
 * interface proxy. The field must refer to the desired implementation after
 * delegate instantiation - the easiest way is to initialize the field with the
 * right value. The field value will be wrapped in a proxy, which will be set as
 * the new field value during code attachment. This way the implementation of
 * the interface may be freely changed, while the reference passed to external
 * code remains constant.
 * <p>
 * The field type must be that of the desired interface rather than the
 * implementation.
 * <p>
 * By default, the interface will be called on the active root thread, even if
 * the calling thread is different.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Proxy {

//    boolean direct() default false;
}
