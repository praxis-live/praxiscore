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

import java.util.stream.Stream;

/**
 * Base class for user rewritable Root container code.
 */
public class CodeRootContainerDelegate extends CodeRootDelegate implements ContainerDelegateAPI {

    @Override
    public void init() {
    }

    /**
     * Stream of child IDs.
     *
     * @return stream of child IDs
     */
    public final Stream<String> children() {
        var ctxt = getContext();
        if (ctxt instanceof CodeRootContainer.Context) {
            return ((CodeRootContainer.Context<?>) ctxt).getComponent().children();
        } else {
            return Stream.empty();
        }
    }

}
