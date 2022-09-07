/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2022 Neil C Smith.
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
import java.util.stream.Stream;

/**
 * Base class for user rewritable container code.
 */
public class CodeContainerDelegate extends CodeDelegate {

    public void init() {
    }

    /**
     * Stream of child IDs.
     *
     * @return stream of child IDs
     */
    public final Stream<String> children() {
        var ctxt = getContext();
        if (ctxt instanceof CodeContainer.Context) {
            return ((CodeContainer.Context<?>) ctxt).getComponent().children();
        } else {
            return Stream.empty();
        }
    }

    /**
     * Annotation to be used on the {@link #init()} method of the delegate to
     * control addition of child port proxying capability.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface ProxyPorts {
    }

}
