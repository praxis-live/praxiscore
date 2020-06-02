/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2020 Neil C Smith.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.praxislive.core.Value;
import org.praxislive.core.ControlPort;
import org.praxislive.core.Port;
import org.praxislive.core.PortConnectionException;
import org.praxislive.core.PortListener;
import org.praxislive.core.PortInfo;

/**
 *
 */
public class ControlOutput extends ControlPort.Output {
    
    public final static PortInfo INFO = 
            PortInfo.create(ControlPort.class, PortInfo.Direction.OUT, null);

    private final List<ControlPort.Input> connections;
    private final List<PortListener> listeners;
    
    private boolean sending;

    public ControlOutput() {
        connections = new ArrayList<>();
        listeners = new CopyOnWriteArrayList<>();
    }
       

    @Override
    public void connect(Port port) throws PortConnectionException {
        if (port instanceof ControlPort.Input) {
            ControlPort.Input cport = (ControlPort.Input) port;
            if (connections.contains(cport)) {
                throw new PortConnectionException();
            }
            makeConnection(cport);
            connections.add(cport);
            listeners.forEach(l -> l.connectionsChanged(this));
        } else {
            throw new PortConnectionException();
        }
    }

    @Override
    public void disconnect(Port port) {
        if (port instanceof ControlPort.Input) {
            ControlPort.Input cport = (ControlPort.Input) port;
            if (connections.remove(cport)) {
                breakConnection(cport);
                listeners.forEach(l -> l.connectionsChanged(this));
            }
        }
    }

    @Override
    public void disconnectAll() {
        for (ControlPort.Input connection : connections()) {
            disconnect(connection);
        }
    }

    @Override
    public List<ControlPort.Input> connections() {
        return List.copyOf(connections);
    }

    @Override
    public void addListener(PortListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeListener(PortListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void send(long time, double value) {
        if (sending) {
            return; // @TODO recursion strategy - allow up to maximum count?
        }
        sending = true;
        for (ControlPort.Input port : connections) {
            try {
                port.receive(time, value);
            } catch (Exception ex) {
                // @TODO log errors
            }

        }
        sending = false;
    }

    @Override
    public void send(long time, Value value) {
        if (sending) {
            return;
        }
        sending = true;
        for (ControlPort.Input port : connections) {
            try {
                port.receive(time, value);
            } catch (Exception ex) {
                // @TODO log errors
            }
        }
        sending = false;
    }
}
