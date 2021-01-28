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
 *
 */
package org.praxislive.midi.components;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.AbstractRootContainer;
import org.praxislive.core.Lookup;
import org.praxislive.core.Packet;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Clock;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PString;
import org.praxislive.midi.MidiInputContext;

/**
 *
 */
public class MidiRoot extends AbstractRootContainer {
    
    private final static Logger LOG = Logger.getLogger(MidiRoot.class.getName());
    
    private final DeviceProperty device;
    private final ComponentInfo info;
    
    private MidiThreadRouter router;
    private MidiDevice midiDevice;
    private Transmitter transmitter;
    private Lookup lookup;
    private MidiContextReceiver receiver;
    private MidiMessage lastMessage;
    
    public MidiRoot() {
        device = new DeviceProperty();
        registerControl("device", device);
        registerControl("last-message", new LastMessageProperty());
        info = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .merge(StartableProtocol.API_INFO)
                .control("device", c -> c.property()
                        .input(a -> a.string()
                                .emptyIsDefault()
                                .allowed(getDeviceNames().toArray(String[]::new))))
                .control("last-message", c -> c.readOnlyProperty().output(PString.class))
        );
    }

    @Override
    public ComponentInfo getInfo() {
        return info;
    }
    
    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), router, receiver);
        }
        return lookup;
    }
    
    @Override
    protected void activating() {
        super.activating();
        router = new MidiThreadRouter(getRouter());
        receiver = new MidiContextReceiver(getRootHub().getClock());
    }
    
    @Override
    protected void starting() {
        super.starting();
        lastMessage = null;
        try {
            midiDevice = getDevice(device.get().toString());
            transmitter = midiDevice.getTransmitter();
            transmitter.setReceiver(receiver);
            midiDevice.open();
        } catch (MidiUnavailableException ex) {
            setIdle();
        }
    }
    
    @Override
    protected void stopping() {
        super.stopping();
        closeDevice();
    }
    
    @Override
    protected void terminating() {
        super.terminating();
        closeDevice();
        
    }
    
    private void closeDevice() {
        if (transmitter != null) {
            transmitter.setReceiver(null);
            transmitter.close();
            transmitter = null;
        }
        if (midiDevice != null) {
            midiDevice.close();
            midiDevice = null;
        }
    }
    
    private MidiDevice getDevice(String device) throws MidiUnavailableException {
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        if (infos.length == 0) {
            throw new MidiUnavailableException();
        }
        if (device == null || device.isEmpty()) {
            for (MidiDevice.Info info : infos) {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() != 0) {
                    return dev;
                }
            }
            throw new MidiUnavailableException();
        } else {
            Pattern pattern = Pattern.compile(Pattern.quote(device), Pattern.CASE_INSENSITIVE);
            for (MidiDevice.Info info : infos) {
                if (pattern.matcher(info.getName()).matches()) {
                    MidiDevice dev = MidiSystem.getMidiDevice(info);
                    if (dev.getMaxTransmitters() != 0) {
                        return dev;
                    }
                }
            }
            for (MidiDevice.Info info : infos) {
                if (pattern.matcher(info.getName()).find()) {
                    MidiDevice dev = MidiSystem.getMidiDevice(info);
                    if (dev.getMaxTransmitters() != 0) {
                        return dev;
                    }
                }
            }
        }
        throw new MidiUnavailableException();
        
    }
    
    private List<String> getDeviceNames() {
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        List<String> names = new ArrayList<>(infos.length);
        for (MidiDevice.Info info : infos) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() != 0) {
                    names.add(info.getName());
                }
            } catch (MidiUnavailableException ex) {
            }
        }
        return names;
    }
    
    private class DeviceProperty extends AbstractProperty {
        
        PString device = PString.EMPTY;
        
        @Override
        protected void set(long time, Value arg) throws Exception {
            device = PString.of(arg);
        }
        
        @Override
        protected Value get() {
            return device;
        }
        
    }
    
    private class LastMessageProperty extends AbstractProperty {
        
        @Override
        protected void set(long time, Value arg) throws Exception {
            throw new UnsupportedOperationException();
        }
        
        @Override
        protected Value get() {
            if (lastMessage != null) {
                if (lastMessage instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) lastMessage;
                    StringBuilder sb = new StringBuilder("Ch:")
                            .append(sm.getChannel() + 1)
                            .append(" Cmd:")
                            .append(sm.getCommand())
                            .append(" Data1:")
                            .append(sm.getData1())
                            .append(" Data2:")
                            .append(sm.getData2());
                    return PString.of(sb);
                } else {
                    return PString.of(lastMessage);
                }
            } else {
                return PString.EMPTY;
            }
        }
        
    }
    
    private class MidiContextReceiver
            extends MidiInputContext implements Receiver {
        
        private final Clock clock;
        
        private MidiContextReceiver(Clock clock) {
            this.clock = clock;
        }
        
        @Override
        public void send(final MidiMessage message, long timeStamp) {
            final long time = clock.getTime(); //@TODO use timestamp
            invokeLater(new Runnable() {
                @Override
                public void run() {
//                    if (getState() == RootState.ACTIVE_RUNNING) {
                    if (lastMessage instanceof ShortMessage) {
                        if (((ShortMessage) message).getCommand() < 0xF0) {
                            lastMessage = message;
                        }
                    } else {
                        lastMessage = message;
                    }
                    dispatch(message, time);
//                    } 
                }
            });
        }
        
        @Override
        public void close() {
            // no op
        }
    }
    
    private class MidiThreadRouter implements PacketRouter {
        
        private final PacketRouter delegate;
        
        private MidiThreadRouter(PacketRouter delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void route(Packet packet) {
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, "Sending call\n{0}", packet);
            }
            delegate.route(packet);
            
        }
    }
}
