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

import java.util.Objects;
import java.util.Optional;
import org.praxislive.core.types.PMap;

/**
 * Information on the type, direction and properties for a {@link Port}.
 */
public final class PortInfo extends PMap.MapBasedValue {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "PortInfo";

    /**
     * Map key for port type name. Value should be the String from
     * {@link Port.Type#name()}.
     */
    public static final String KEY_TYPE = "type";

    /**
     * Map key for the port direction.
     */
    public static final String KEY_DIRECTION = "direction";

    /**
     * Port direction.
     */
    public static enum Direction {

        /**
         * Port is for input. Only output controls can be connected to inputs.
         */
        IN,
        /**
         * Port is for output. Only input controls can be connected to outputs.
         */
        OUT,
        /**
         * Port is bi-directional.
         */
        BIDI
    };

    private final String type;
    private final Direction direction;

    PortInfo(String type,
            Direction direction,
            PMap data) {
        super(data);
        this.type = type;
        this.direction = direction;
    }

    /**
     * The type of the port.
     *
     * @return port type
     */
    public String portType() {
        return type;
    }

    /**
     * The direction of the port.
     *
     * @return port direction
     */
    public Direction direction() {
        return direction;
    }

    /**
     * Access the map of properties. The map includes the type and direction, as
     * well as any custom or optional properties.
     * <p>
     * This method is equivalent to calling
     * {@link PMap.MapBasedValue#dataMap()}.
     *
     * @return property map
     */
    public PMap properties() {
        return dataMap();
    }

    /**
     * Create a PortInfo.
     *
     * @param typeClass port base class
     * @param direction port direction
     * @param properties additional properties (may be null)
     * @return port info
     */
    public static PortInfo create(Class<? extends Port> typeClass,
            Direction direction, PMap properties) {
        return create(Port.Type.of(typeClass).name(), direction, properties);
    }

    static PortInfo create(String type, Direction direction, PMap properties) {
        PMap map = PMap.of(KEY_TYPE, Objects.requireNonNull(type),
                KEY_DIRECTION, Objects.requireNonNull(direction));
        if (properties != null) {
            map = PMap.merge(map, properties, PMap.IF_ABSENT);
        }
        return new PortInfo(type, direction, map);
    }

    /**
     * Coerce the provided Value into a PortInfo if possible.
     *
     * @param arg value of unknown type
     * @return port info or empty optional
     */
    public static Optional<PortInfo> from(Value arg) {
        if (arg instanceof PortInfo info) {
            return Optional.of(info);
        } else {
            try {
                return PMap.from(arg).map(PortInfo::fromMap);
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * Parse the provided String into a PortInfo if possible.
     *
     * @param string text to parse
     * @return port info
     * @throws ValueFormatException if parsing fails
     */
    public static PortInfo parse(String string) throws ValueFormatException {
        var data = PMap.parse(string);
        try {
            return fromMap(data);
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }
    }

    private static PortInfo fromMap(PMap data) {
        var portType = data.get(KEY_TYPE).toString();
        var direction = Direction.valueOf(data.getString(KEY_DIRECTION, ""));
        return new PortInfo(portType, direction, data);
    }

}
