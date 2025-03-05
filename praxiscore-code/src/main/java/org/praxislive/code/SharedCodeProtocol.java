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
package org.praxislive.code;

import java.util.stream.Stream;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Protocol;
import org.praxislive.core.types.PMap;

/**
 * Protocol for a container that provides shared code that can be used by code
 * in child components. The {@code shared-code} property wraps a map of full
 * class names to Java source code. The {@code shared-code-add} and
 * {@code shared-code-merge} functions allow for updating the property with a
 * partial map.
 */
public class SharedCodeProtocol implements Protocol {

    /**
     * Standard control ID for shared code property.
     */
    public static final String SHARED_CODE = "shared-code";
    /**
     * Standard control ID for shared code add function.
     */
    public static final String SHARED_CODE_ADD = "shared-code-add";

    /**
     * Standard control ID for shared code merge function.
     */
    public static final String SHARED_CODE_MERGE = "shared-code-merge";

    /**
     * Control info for the shared code property control.
     */
    public static final ControlInfo SHARED_CODE_INFO
            = Info.control(c -> c.property()
            .input(PMap.class)
            .defaultValue(PMap.EMPTY));

    /**
     * Control info for the shared code add control. The input map is merged
     * with the existing shared code using the rules of {@link PMap#IF_ABSENT}.
     */
    public static final ControlInfo SHARED_CODE_ADD_INFO
            = Info.control(c -> c.function()
            .inputs(a -> a.type(PMap.class))
            .outputs(a -> a.type(PMap.class)));

    /**
     * Control info for the shared code merge control. The input map is merged
     * with the existing shared code using the rules of {@link PMap#REPLACE}.
     */
    public static final ControlInfo SHARED_CODE_MERGE_INFO
            = Info.control(c -> c.function()
            .inputs(a -> a.type(PMap.class))
            .outputs(a -> a.type(PMap.class)));

    public static final ComponentInfo API_INFO = Info.component(cmp -> cmp
            .protocol(SharedCodeProtocol.class)
            .control(SHARED_CODE, SHARED_CODE_INFO)
            .control(SHARED_CODE_ADD, SHARED_CODE_ADD_INFO)
            .control(SHARED_CODE_MERGE, SHARED_CODE_MERGE_INFO)
    );

    @Override
    public Stream<String> controls() {
        return Stream.of(SHARED_CODE, SHARED_CODE_ADD, SHARED_CODE_MERGE);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        return switch (control) {
            case SHARED_CODE ->
                SHARED_CODE_INFO;
            case SHARED_CODE_ADD ->
                SHARED_CODE_ADD_INFO;
            case SHARED_CODE_MERGE ->
                SHARED_CODE_MERGE_INFO;
            default ->
                throw new IllegalArgumentException(control);
        };
    }

}
