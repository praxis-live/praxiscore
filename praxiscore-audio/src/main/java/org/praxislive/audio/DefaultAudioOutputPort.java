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
package org.praxislive.audio;

import org.praxislive.core.PortListener;
import org.praxislive.core.Port;
import org.praxislive.core.PortConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jaudiolibs.pipes.Pipe;
import org.jaudiolibs.pipes.Tee;

/**
 *
 */
public class DefaultAudioOutputPort extends AudioPort.Output {

    private final static Logger logger = Logger.getLogger(DefaultAudioOutputPort.class.getName());
    
    private final List<AudioPort.Input> connections;
    private final List<PortListener> listeners;
    
    private Pipe source;
    private Pipe portSource;
    private Tee splitter;
    private boolean multiChannelCapable;

    public DefaultAudioOutputPort(Pipe source) {
        this(source, false);
    }
    
    public DefaultAudioOutputPort(Pipe source, boolean multiChannelCapable) {
        if (source == null) {
            throw new NullPointerException();
        }
        this.source = source;
        this.portSource = source;
        this.multiChannelCapable = multiChannelCapable;
        connections = new ArrayList<>();
        listeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public void connect(Port port) throws PortConnectionException {
        if (port instanceof AudioPort.Input) {
            AudioPort.Input aport = (AudioPort.Input) port;
            if (connections.contains(aport)) {
                throw new PortConnectionException();
            }
            if (connections.size() == 1) {
                switchToMultichannel();
            }
            AudioPort.Input ip = (AudioPort.Input) port;
            try {
                makeConnection(ip, portSource);
                connections.add(ip);
            } catch (PortConnectionException ex) {
                if (connections.size() == 1) {
                    switchToSingleChannel();
                }
                throw ex;
            }
            listeners.forEach(l -> l.connectionsChanged(this));
        } else {
            throw new PortConnectionException();
        }
    }

    @Override
    public void disconnect(Port port) {
        if (port instanceof AudioPort.Input) {
            AudioPort.Input aport = (AudioPort.Input) port;
            if (connections.contains(aport)) {
                breakConnection(aport, portSource);
                connections.remove(aport);
                if (connections.size() == 1) {
                    switchToSingleChannel();
                }
                listeners.forEach(l -> l.connectionsChanged(this));
            }
        }
    }

    @Override
    public void disconnectAll() {
        for (AudioPort.Input port : connections()) {
            disconnect(port);
        }
    }

    @Override
    public List<AudioPort.Input> connections() {
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

    private void switchToMultichannel() {
        if (multiChannelCapable || portSource == splitter) {
            return;
        }
        Pipe[] sinks = removeSinks(source);
        try {
            if (splitter == null) {
                splitter = new Tee(16); // @TODO make channels configurable
            }
            splitter.addSource(source);
            for (Pipe sink : sinks) {
                sink.addSource(splitter);
            }
            portSource = splitter;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error converting port to multi channel", ex);
            removeSinks(splitter);
            removeSinks(source);
            portSource = source;
            connections.clear();
        }
    }

    private void switchToSingleChannel() {
        if (portSource == source) {
            return;
        }
        Pipe[] sinks = removeSinks(splitter);
        try {
            splitter.removeSource(source);
            for (Pipe sink : sinks) {
                sink.addSource(source);
            }
            portSource = source;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error converting port to single channel", ex);
            removeSinks(source);
            removeSinks(splitter);
            portSource = source;
            connections.clear();
        }

    }

    private Pipe[] removeSinks(Pipe source) {
//        Sink[] sinks = source.getSinks();
//        for (Sink sink : sinks) {
//            sink.removeSource(source);
//        }
//        return sinks;
        Pipe[] sinks = new Pipe[source.getSinkCount()];
        for (int i = 0; i < sinks.length; i++) {
            sinks[i] = source.getSink(i);
        }
        for (Pipe sink : sinks) {
            sink.removeSource(source);
        }
        return sinks;
    }
}
