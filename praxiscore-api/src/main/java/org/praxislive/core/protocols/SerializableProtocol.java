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
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Protocol;
import org.praxislive.core.types.PMap;

/**
 * Protocol to serialize all or part of a component tree. The protocol will
 * usually be implemented by a root container.
 * <p>
 * The standard format is a hierarchical map. Annotation keys, including type,
 * info and connections, are prefixed by <code>%</code>. Child keys are prefixed
 * by <code>@</code> and the values are maps. Property keys are not prefixed.
 * <pre>
 * {@code
 * %type core:container
 * %info ...
 * %custom-annotation foo
 * property1 true
 * @child1 {
 *   %type core:custom
 *   %info ...
 *   property1 42
 * }
 * @child2 {
 *   %type core:custom
 *   %info ...
 * }
 * %connections { {child1 in child2 out} {child2 ready child1 trigger} }
 * }
 * </pre>
 *
 */
public final class SerializableProtocol implements Protocol {

    /**
     * Name of the serialize control.
     */
    public static final String SERIALIZE = "serialize";

    /**
     * Map key for the optional subtree configuration parameter. The value must
     * be a {@link ComponentAddress} that is a sub-component of the component
     * implementing this protocol. The returned data will be as if the subtree
     * component is at the root.
     */
    public static final String OPTION_SUBTREE = "subtree";

    /**
     * Control info for the serialize control. The control accepts an optional
     * configuration map.
     * <p>
     * Callers may use the configuration key {@link #OPTION_SUBTREE} to filter
     * the returned data.
     * <p>
     * Implementations of this protocol should return an error if they do not
     * recognise any provided configuration key or value.
     */
    public static final ControlInfo SERIALIZE_INFO
            = Info.control(c -> c.function()
            .inputs(a -> a.type(PMap.class).property(ArgumentInfo.KEY_OPTIONAL, true))
            .outputs(a -> a.type(PMap.class)));

    /**
     * A component info for this protocol. Can be used with
     * {@link Info.ComponentInfoBuilder#merge(org.praxislive.core.ComponentInfo)}.
     */
    public static final ComponentInfo API_INFO = Info.component(cmp -> cmp
            .protocol(SerializableProtocol.class)
            .control(SERIALIZE, SERIALIZE_INFO)
    );

    @Override
    public Stream<String> controls() {
        return Stream.of(SERIALIZE);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        return switch (control) {
            case SERIALIZE ->
                SERIALIZE_INFO;
            default ->
                throw new IllegalArgumentException();
        };
    }

}
