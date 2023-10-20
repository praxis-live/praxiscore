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

import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PString;

/**
 * A type representing a connection between two ports.
 */
public final class Connection {

    private final PArray dataArray;

    /**
     * Create a connection reference. The child IDs must be valid according to
     * {@link ComponentAddress#isValidID(java.lang.String)}. The port IDs must
     * be valid according to {@link PortAddress#isValidID(java.lang.String)}.
     *
     * @param child1 ID of first child
     * @param port1 ID of port on first child
     * @param child2 ID of second child
     * @param port2 ID of port on second child
     * @throws IllegalArgumentException if the IDs are not valid
     */
    public Connection(String child1, String port1, String child2, String port2) {
        verifyChildID(child1);
        verifyChildID(child2);
        verifyPortID(port1);
        verifyPortID(port2);
        dataArray = PArray.of(PString.of(child1), PString.of(port1),
                PString.of(child2), PString.of(port2));
    }

    /**
     * Query the component ID of the first connected component.
     *
     * @return ID of first child
     */
    public String child1() {
        return dataArray.get(0).toString();
    }

    /**
     * Query the port ID of the connected port on the first component.
     *
     * @return ID of port on first child
     */
    public String port1() {
        return dataArray.get(1).toString();
    }

    /**
     * Query the component ID of the second connected component.
     *
     * @return ID of the second child
     */
    public String child2() {
        return dataArray.get(2).toString();
    }

    /**
     * Query the port ID of the connected port on the second component.
     *
     * @return ID of port on second child
     */
    public String port2() {
        return dataArray.get(3).toString();
    }

    /**
     * Access the Connection as the backing PArray data. The data consists of
     * four values, {@code child1 port1 child2 port2}.
     * <p>
     * This is the same format included in the list returned from
     * {@link ContainerProtocol#CONNECTIONS}.
     *
     * @return backing data array
     */
    public PArray dataArray() {
        return dataArray;
    }

    @Override
    public String toString() {
        return dataArray.toString();
    }

    @Override
    public int hashCode() {
        return dataArray.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof Connection c && dataArray.equals(c.dataArray));
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
