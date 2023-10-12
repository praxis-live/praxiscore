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
package org.praxislive.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 * Information about the controls, ports, protocols and properties of a
 * Component.
 */
public class ComponentInfo extends PMap.MapBasedValue {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "ComponentInfo";

    /**
     * Map key for the controls map.
     */
    public static final String KEY_CONTROLS = "controls";

    /**
     * Map key for the ports map.
     */
    public static final String KEY_PORTS = "ports";

    /**
     * Map key for the protocols list.
     */
    public static final String KEY_PROTOCOLS = "protocols";

    /**
     * Optional key for storing the {@link ComponentType} of the Component in
     * the properties map. value must be a valid component type.
     */
    public static final String KEY_COMPONENT_TYPE = "component-type";

    /**
     * Optional key marking the fact that the response from
     * {@link Component#getInfo()} or {@link ComponentProtocol} may change
     * during the lifetime of the component. Value must currently be a boolean.
     */
    public static final String KEY_DYNAMIC = "dynamic";

    private final OrderedMap<String, ControlInfo> controls;
    private final OrderedMap<String, PortInfo> ports;
    private final List<String> protocols;

    private ComponentInfo(
            OrderedMap<String, ControlInfo> controls,
            OrderedMap<String, PortInfo> ports,
            List<String> protocols,
            PMap data) {
        super(data);
        this.protocols = protocols;
        this.controls = controls;
        this.ports = ports;
    }

    /**
     * The list of {@link Protocol}s supported by the related component.
     *
     * @return list of protocols
     */
    public List<String> protocols() {
        return protocols;
    }

    /**
     * Query whether the related component has the provided protocol.
     *
     * @param protocol protocol class
     * @return true if component has protocol
     */
    public boolean hasProtocol(Class<? extends Protocol> protocol) {
        String name = Protocol.Type.of(protocol).name();
        return protocols.contains(name);
    }

    /**
     * The list of controls on the related component. To access the
     * {@link ControlInfo} for a control, use
     * {@link #controlInfo(java.lang.String)}.
     *
     * @return list of controls
     */
    public List<String> controls() {
        return controls.keys();
    }

    /**
     * Access the {@link ControlInfo} for the given control.
     *
     * @param control name of control
     * @return control info (or null if not in the list of controls)
     */
    public ControlInfo controlInfo(String control) {
        return controls.get(control);
    }

    /**
     * The list of ports on the related component. To access the
     * {@link PortInfo} for a port, use {@link #portInfo(java.lang.String)}.
     *
     * @return list of ports
     */
    public List<String> ports() {
        return ports.keys();
    }

    /**
     * Access the {@link PortInfo} for the given port.
     *
     * @param port name of port
     * @return port info (or null if not in the list of ports)
     */
    public PortInfo portInfo(String port) {
        return ports.get(port);
    }

    /**
     * Access the map of properties. The map includes all the controls, ports
     * and protocols, as well as any custom or optional properties.
     * <p>
     * This method is equivalent to calling
     * {@link PMap.MapBasedValue#dataMap()}.
     *
     * @return property map
     */
    public PMap properties() {
        return dataMap();
    }

    static ComponentInfo create(
            Map<String, ControlInfo> controls,
            Map<String, PortInfo> ports,
            Set<String> protocols,
            PMap properties) {

        var ctrls = OrderedMap.copyOf(controls);
        var prts = OrderedMap.copyOf(ports);
        var protos = List.copyOf(protocols);

        PMap data = PMap.of(KEY_CONTROLS, PMap.ofMap(controls),
                KEY_PORTS, PMap.ofMap(ports),
                KEY_PROTOCOLS, protos.stream().map(PString::of).collect(PArray.collector())
        );

        if (properties != null && !properties.isEmpty()) {
            data = PMap.merge(data, properties, PMap.IF_ABSENT);
        }

        return new ComponentInfo(ctrls, prts, protos, data);

    }

    /**
     * Coerce the provided Value into a ComponentInfo if possible.
     *
     * @param arg value of unknown type
     * @return component info or empty optional
     */
    public static Optional<ComponentInfo> from(Value arg) {
        if (arg instanceof ComponentInfo info) {
            return Optional.of(info);
        } else {
            try {
                return PMap.from(arg).map(ComponentInfo::fromMap);
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * Parse the provided String into a ComponentInfo if possible.
     *
     * @param string text to parse
     * @return component info
     * @throws ValueFormatException if parsing fails
     */
    public static ComponentInfo parse(String string) throws ValueFormatException {
        var data = PMap.parse(string);
        try {
            return fromMap(data);
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }
    }

    /**
     * Convenience method to create an {@link ArgumentInfo} for a ComponentInfo
     * argument.
     *
     * @return argument info for ComponentInfo
     */
    public static ArgumentInfo info() {
        return ArgumentInfo.of(ComponentInfo.class);
    }

    private static ComponentInfo fromMap(PMap data) {
        var controls = Optional.ofNullable(data.get(KEY_CONTROLS))
                .flatMap(PMap::from)
                .map(m -> m.asMapOf(ControlInfo.class))
                .orElseGet(() -> OrderedMap.of());
        var ports = Optional.ofNullable(data.get(KEY_PORTS))
                .flatMap(PMap::from)
                .map(m -> m.asMapOf(PortInfo.class))
                .orElseGet(() -> OrderedMap.of());
        var protocols = Optional.ofNullable(data.get(KEY_PROTOCOLS))
                .flatMap(PArray::from)
                .map(l -> l.asListOf(String.class))
                .orElseGet(() -> List.of());
        return new ComponentInfo(controls, ports, protocols, data);
    }

}
