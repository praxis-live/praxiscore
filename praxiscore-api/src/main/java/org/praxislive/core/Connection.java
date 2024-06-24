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
package org.praxislive.core;

import java.util.Optional;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PString;

/**
 * A type representing a connection between two ports.
 */
public final class Connection extends PArray.ArrayBasedValue {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "Connection";

    private Connection(PArray data) {
        super(data);
        if (data.size() != 4) {
            throw new IllegalArgumentException("Invalid connection data");
        }
        verifyChildID(data.get(0).toString());
        verifyPortID(data.get(1).toString());
        verifyChildID(data.get(2).toString());
        verifyPortID(data.get(3).toString());
    }

    /**
     * Query the component ID of the source component.
     *
     * @return ID of first child
     */
    public String sourceComponent() {
        return dataArray().get(0).toString();
    }

    /**
     * Query the port ID of the source port.
     *
     * @return ID of port on first child
     */
    public String sourcePort() {
        return dataArray().get(1).toString();
    }

    /**
     * Query the component ID of the target component.
     *
     * @return ID of the second child
     */
    public String targetComponent() {
        return dataArray().get(2).toString();
    }

    /**
     * Query the port ID of the target port.
     *
     * @return ID of port on second child
     */
    public String targetPort() {
        return dataArray().get(3).toString();
    }

    /**
     * Create a connection reference. The child IDs must be valid according to
     * {@link ComponentAddress#isValidID(java.lang.String)}. The port IDs must
     * be valid according to {@link PortAddress#isValidID(java.lang.String)}.
     *
     * @param child1 ID of first child
     * @param port1 ID of port on first child
     * @param child2 ID of second child
     * @param port2 ID of port on second child
     * @return new connection
     * @throws IllegalArgumentException if the IDs are not valid
     */
    public static Connection of(String child1, String port1, String child2, String port2) {
        return new Connection(PArray.of(
                PString.of(child1), PString.of(port1),
                PString.of(child2), PString.of(port2)
        ));
    }

    /**
     * Coerce the provided value into a Connection if possible.
     *
     * @param value value of unknown type
     * @return connection or empty optional
     */
    public static Optional<Connection> from(Value value) {
        if (value instanceof Connection connection) {
            return Optional.of(connection);
        } else {
            try {
                return PArray.from(value).map(Connection::new);
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
    }

    private static void verifyChildID(String childID) {
        if (!ComponentAddress.isValidID(childID)) {
            throw new IllegalArgumentException("Invalid child ID : " + childID);
        }
    }

    private static void verifyPortID(String portID) {
        if (!PortAddress.isValidID(portID)) {
            throw new IllegalArgumentException("Invalid port ID : " + portID);
        }
    }

}
