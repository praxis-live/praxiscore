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
package org.praxislive.audio.impl.components;

import org.praxislive.audio.AudioContext;
import org.praxislive.audio.AudioPort;
import org.praxislive.audio.ClientRegistrationException;
import org.jaudiolibs.pipes.Pipe;
import org.jaudiolibs.pipes.Placeholder;
import org.praxislive.audio.DefaultAudioOutputPort;
import org.praxislive.base.AbstractComponent;
import org.praxislive.base.AbstractProperty;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Port;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;

/**
 *
 */
public class AudioInput extends AbstractComponent {

    private final static int MAX_CHANNELS = 16;

    private final Placeholder[] placeholders;
    private final AudioPort.Output[] ports;
    private final AudioContext.InputClient client;

    private int channelCount;
    private AudioContext context;
    private ComponentInfo info;

    public AudioInput() {
        placeholders = new Placeholder[MAX_CHANNELS];
        for (int i = 0; i < MAX_CHANNELS; i++) {
            placeholders[i] = new Placeholder();
        }
        ports = new AudioPort.Output[MAX_CHANNELS];

        client = new Client();

        channelCount = 2;

        registerControl("channels", new ChannelProperty());

        syncPorts();
    }

    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        AudioContext ctxt = getLookup().find(AudioContext.class).orElse(null);
        if (ctxt != context) {
            if (context != null) {
                context.unregisterAudioInputClient(client);
                context = null;
            }
            if (ctxt == null) {
                return;
            }
            try {
                ctxt.registerAudioInputClient(client);
                context = ctxt;
            } catch (ClientRegistrationException ex) {
                System.getLogger(AudioInput.class.getName())
                        .log(System.Logger.Level.ERROR, "", ex);
            }
        }
    }

    private void syncPorts() {
        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (i < channelCount) {
                if (ports[i] == null) {
                    var port = new DefaultAudioOutputPort(placeholders[i]);
                    registerPort(Port.OUT + "-" + (i + 1), port);
                    ports[i] = port;
                }
            } else {
                if (ports[i] != null) {
                    // unregister will disconnect all
                    unregisterPort(Port.OUT + "-" + (i + 1));
                    ports[i] = null;
                }
            }
        }
        info = null;
    }

    @Override
    public ComponentInfo getInfo() {
        if (info == null) {
            info = Info.component(cmp -> {
                cmp.merge(ComponentProtocol.API_INFO);
                cmp.property(ComponentInfo.KEY_DYNAMIC, PBoolean.TRUE);
                cmp.control("channels", c -> c.property().input(a -> a
                        .number().min(1).max(MAX_CHANNELS)
                        .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                ));
                for (int i = 0; i < channelCount; i++) {
                    cmp.port(Port.OUT + "-" + (i + 1), p -> p.output(AudioPort.class));
                }
                return cmp;
            });
        }
        return info;
    }

    private class Client extends AudioContext.InputClient {

        @Override
        public int getInputCount() {
            return channelCount;
        }

        @Override
        public Pipe getInputSink(int index) {
            return placeholders[index];
        }

    }

    private class ChannelProperty extends AbstractProperty {

        PNumber value = PNumber.of(2);

        @Override
        protected void set(long time, Value arg) throws Exception {
            value = PNumber.from(arg)
                    .filter(n -> n.toIntValue() >= 1 && n.toIntValue() <= MAX_CHANNELS)
                    .orElseThrow(IllegalArgumentException::new);
            channelCount = value.toIntValue();
            syncPorts();
        }

        @Override
        protected Value get() {
            return value;
        }

    }

}
