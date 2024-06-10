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
package org.praxislive.core.protocols;

import java.util.stream.Stream;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Protocol;
import org.praxislive.core.types.PMap;

/**
 * Basic component protocol, providing access to the component info, and
 * optional support for attaching metadata.
 */
public final class ComponentProtocol implements Protocol {

    /**
     * Name of the info control.
     */
    public static final String INFO = "info";

    /**
     * Name of the optional meta control.
     */
    public static final String META = "meta";

    /**
     * Name of the optional meta control.
     */
    public static final String META_MERGE = "meta-merge";

    /**
     * Control info for the {@code info} control. A read-only property that
     * returns the component info. The response to calling this control should
     * be the same as calling {@link Component#getInfo}.
     */
    public static final ControlInfo INFO_INFO
            = Info.control(c -> c.readOnlyProperty().output(ComponentInfo.class));

    /**
     * Control info for the optional {@code meta} property. This property allows
     * for arbitrary metadata to be attached to a component. Implementations of
     * this property may only allow the value to be set when the existing value
     * is empty or the current value is empty. The {@code meta-merge} control
     * should be used to update an existing meta map - see
     * {@link #META_MERGE_INFO}.
     */
    public static final ControlInfo META_INFO
            = Info.control(c -> c.property().input(PMap.class));

    /**
     * Control info for the optional {@code meta-merge} control. This control
     * accepts a map to merge into the existing meta map, and returns the full
     * resulting value.
     * <p>
     * The merge is done according to the specification of {@link PMap#REPLACE}.
     */
    public static final ControlInfo META_MERGE_INFO
            = Info.control(c -> c.function()
            .inputs(a -> a.type(PMap.class))
            .outputs(a -> a.type(PMap.class)));

    /**
     * A component info for this protocol. Can be used with
     * {@link Info.ComponentInfoBuilder#merge(org.praxislive.core.ComponentInfo)}.
     */
    public static final ComponentInfo API_INFO = Info.component(cmp -> cmp
            .protocol(ComponentProtocol.class)
            .control(INFO, INFO_INFO)
    );

    @Override
    public Stream<String> controls() {
        return Stream.of(INFO);
    }

    @Override
    public Stream<String> optionalControls() {
        return Stream.of(META, META_MERGE);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        return switch (control) {
            case INFO ->
                INFO_INFO;
            case META ->
                META_INFO;
            case META_MERGE ->
                META_MERGE_INFO;
            default ->
                throw new IllegalArgumentException();
        };
    }

}
