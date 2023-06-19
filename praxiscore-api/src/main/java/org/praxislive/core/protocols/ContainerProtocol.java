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
package org.praxislive.core.protocols;

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Container;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.Protocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 * A container protocol that allows for calls to add / remove child components,
 * and connect / disconnect their ports.
 */
public class ContainerProtocol implements Protocol {

    @Deprecated
    public final static ContainerProtocol INSTANCE = new ContainerProtocol();

    /**
     * Name of the add-child control.
     */
    public final static String ADD_CHILD = "add-child";

    /**
     * Name of the remove-child control.
     */
    public final static String REMOVE_CHILD = "remove-child";

    /**
     * Name of the children control.
     */
    public final static String CHILDREN = "children";

    /**
     * Name of the connect control.
     */
    public final static String CONNECT = "connect";

    /**
     * Name of the disconnect control.
     */
    public final static String DISCONNECT = "disconnect";

    /**
     * Name of the connections control.
     */
    public final static String CONNECTIONS = "connections";

    /**
     * Name of the supported-types control.
     */
    public final static String SUPPORTED_TYPES = "supported-types";

    private final static ArgumentInfo STRING = PString.info();

    /**
     * Info for the add-child control. It is a function control that accepts two
     * arguments, the child name and the component type. It returns no
     * arguments. It will respond with an error if the child cannot be added.
     */
    public final static ControlInfo ADD_CHILD_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(STRING, ComponentType.info()),
                    List.of(),
                    PMap.EMPTY);

    /**
     * Info for the remove-child control. It is a function control that accepts
     * one argument, the child name. It returns no arguments.
     */
    public final static ControlInfo REMOVE_CHILD_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(STRING),
                    List.of(),
                    PMap.EMPTY);

    /**
     * Info for the children control. It is a read-only property control that
     * returns a PArray of child names. The response is equivalent to
     * {@link Container#children()}.
     */
    public final static ControlInfo CHILDREN_INFO
            = ControlInfo.createReadOnlyPropertyInfo(
                    PArray.info(),
                    PMap.EMPTY);

    /**
     * Info for the connect control. It is a function control that accepts four
     * arguments, the first component name, the first port name, the second
     * component name, and the second port name. It returns no arguments. It
     * will response with an error if the connection cannot be made.
     */
    public final static ControlInfo CONNECT_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(STRING, STRING, STRING, STRING),
                    List.of(),
                    PMap.EMPTY);

    /**
     * Info for the disconnect control. It is a function control that accepts
     * four arguments, the first component name, the first port name, the second
     * component name, and the second port name. It returns no arguments.
     */
    public final static ControlInfo DISCONNECT_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(STRING, STRING, STRING, STRING),
                    List.of(),
                    PMap.EMPTY);

    /**
     * Info for the connections control. It is a read-only property that returns
     * a PArray of PArray. Each internal PArray consists of four values,
     * corresponding to the arguments passed to each call to connect.
     */
    public final static ControlInfo CONNECTIONS_INFO
            = ControlInfo.createReadOnlyPropertyInfo(
                    PArray.info(),
                    PMap.EMPTY);

    /**
     * Info for the (optional) supported-types control. It is a read-only
     * property that returns a PArray consisting of all supported
     * {@link ComponentType} that can be passed to add-child.
     * <p>
     * A {@link SupportedTypes} implementation may be registered in the
     * container's {@link Lookup} to facilitate implementation of this control
     * by child containers.
     */
    public final static ControlInfo SUPPORTED_TYPES_INFO
            = Info.control(c -> c.readOnlyProperty().output(PArray.class));

    /**
     * A component info for this protocol. Can be used with
     * {@link Info.ComponentInfoBuilder#merge(org.praxislive.core.ComponentInfo)}.
     * <p>
     * This does not contain info for optional controls (ie. supported-types)
     * which must be added additionally if required.
     */
    public static final ComponentInfo API_INFO = Info.component(cmp -> cmp
            .protocol(ContainerProtocol.class)
            .control(ADD_CHILD, ADD_CHILD_INFO)
            .control(REMOVE_CHILD, REMOVE_CHILD_INFO)
            .control(CHILDREN, CHILDREN_INFO)
            .control(CONNECT, CONNECT_INFO)
            .control(DISCONNECT, DISCONNECT_INFO)
            .control(CONNECTIONS, CONNECTIONS_INFO)
    );

    @Override
    public Stream<String> controls() {
        return Stream.of(ADD_CHILD, REMOVE_CHILD, CHILDREN,
                CONNECT, DISCONNECT, CONNECTIONS);
    }

    @Override
    public Stream<String> optionalControls() {
        return Stream.of(SUPPORTED_TYPES);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        switch (control) {
            case ADD_CHILD:
                return ADD_CHILD_INFO;
            case REMOVE_CHILD:
                return REMOVE_CHILD_INFO;
            case CHILDREN:
                return CHILDREN_INFO;
            case CONNECT:
                return CONNECT_INFO;
            case DISCONNECT:
                return DISCONNECT_INFO;
            case CONNECTIONS:
                return CONNECTIONS_INFO;
            case SUPPORTED_TYPES:
                return SUPPORTED_TYPES_INFO;
        }
        throw new IllegalArgumentException();
    }

}
