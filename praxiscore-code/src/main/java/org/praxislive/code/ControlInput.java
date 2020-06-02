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

import org.praxislive.core.PortListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.praxislive.core.Value;
import org.praxislive.core.ControlPort;
import org.praxislive.core.PortConnectionException;
import org.praxislive.core.PortInfo;

/**
 *
 */
public class ControlInput extends ControlPort.Input {
    
    public final static PortInfo INFO =
            PortInfo.create(ControlPort.class, PortInfo.Direction.IN, null);
    
    private final List<ControlPort.Output> connections;
    private final List<PortListener> listeners;
    
    private Link link;

    public ControlInput(Link link) {
        if (link == null) {
            throw new NullPointerException();
        }
        this.link = link;
        connections = new ArrayList<>();
        listeners = new CopyOnWriteArrayList<>();
    }

    public void setLink(Link link) {
        if (link == null) {
            throw new NullPointerException();
        }
        this.link = link;
    }

    public Link getLink() {
        return link;
    }

    @Override
    protected void addControlOutputPort(ControlPort.Output port) throws PortConnectionException {
        if (connections.contains(port)) {
            throw new PortConnectionException();
        }
        connections.add(port);
        listeners.forEach(l -> l.connectionsChanged(this));
    }

    @Override
    protected void removeControlOutputPort(ControlPort.Output port) {
        if (connections.remove(port)) {
            listeners.forEach(l -> l.connectionsChanged(this));
        }
    }

    @Override
    public void disconnectAll() {
        for (ControlPort.Output connection : connections()) {
            disconnect(connection);
        }
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
    public List<ControlPort.Output> connections() {
        return List.copyOf(connections);
    }

    @Override
    public void receive(long time, double value) {
        link.receive(time, value);
    }

    @Override
    public void receive(long time, Value value) {
        link.receive(time, value);
    }

    public static interface Link {

        public void receive(long time, double value);

        public void receive(long time, Value value);

    }

}
