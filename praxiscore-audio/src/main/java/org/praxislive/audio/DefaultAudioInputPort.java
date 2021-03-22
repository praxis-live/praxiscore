/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2021 Neil C Smith.
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
import org.praxislive.core.PortConnectionException;
import org.praxislive.audio.AudioPort.Output;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jaudiolibs.pipes.Pipe;
import org.jaudiolibs.pipes.Add;

/**
 *
 */
public class DefaultAudioInputPort extends AudioPort.Input {

    private final static System.Logger LOG = System.getLogger(DefaultAudioInputPort.class.getName());
    
    private final List<AudioPort.Output> connections;
    private final List<PortListener> listeners;
    
    private Pipe sink;
    private Pipe portSink;
    private Add mixer;
    private boolean multiChannelCapable;
    
    public DefaultAudioInputPort(Pipe sink) {
        this(sink, false);
    }
    
    public DefaultAudioInputPort(Pipe sink, boolean multiChannelCapable) {
        if (sink == null) {
            throw new NullPointerException();
        }
        this.sink = sink;
        this.portSink = sink;
        this.multiChannelCapable = multiChannelCapable;
        connections = new ArrayList<>();
        listeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public void disconnectAll() {
        for (AudioPort.Output connection : connections()) {
            disconnect(connection);
        }
    }

    @Override
    public List<AudioPort.Output> connections() {
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
    protected void addAudioOutputPort(Output port, Pipe source) throws PortConnectionException {
        if (connections.contains(port)) {
            throw new PortConnectionException();
        }
        if (connections.size() == 1) {
            switchToMultichannel();
        }
        try {
            portSink.addSource(source);
            connections.add(port);
            listeners.forEach(l -> l.connectionsChanged(this));
        } catch (Exception ex) {
            if (connections.size() == 1) {
                switchToSingleChannel();
            }
            throw new PortConnectionException();
        }
    }

    @Override
    protected void removeAudioOutputPort(Output port, Pipe source) {
        if (connections.remove(port)) {
            portSink.removeSource(source);
            if (connections.size() == 1) {
                switchToSingleChannel();
            }
            listeners.forEach(l -> l.connectionsChanged(this));
        }
    }

    private void switchToMultichannel() {
        if (multiChannelCapable || portSink == mixer) {
            return;
        }
        Pipe[] sources = removeSources(sink);
        try {
            if (mixer == null) {
                mixer = new Add(16); // @TODO make channels configurable
            }
            sink.addSource(mixer);
            for (Pipe source : sources) {
                mixer.addSource(source);
            }
            portSink = mixer;
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "Error converting port to multi channel", ex);
            removeSources(mixer);
            removeSources(sink);
            connections.clear();
            listeners.forEach(l -> l.connectionsChanged(this));
        }
    }

    private void switchToSingleChannel() {
        if (portSink == sink) {
            return;
        }
        Pipe[] sources = removeSources(mixer);
        try {
            sink.removeSource(mixer);
            for (Pipe source : sources) {
                sink.addSource(source);
            }
            portSink = sink;
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "Error converting port to single channel", ex);
            removeSources(sink);
            removeSources(mixer);
            connections.clear();
            listeners.forEach(l -> l.connectionsChanged(this));
        }

    }

    private Pipe[] removeSources(Pipe sink) {
        Pipe[] sources = new Pipe[sink.getSourceCount()];
        for (int i=0; i<sources.length; i++) {
            sources[i] = sink.getSource(i);
        }
        for (Pipe source : sources) {
            sink.removeSource(source);
        }
        return sources;
    }
}
