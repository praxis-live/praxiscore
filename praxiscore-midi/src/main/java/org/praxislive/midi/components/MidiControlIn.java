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

import java.util.logging.Logger;
import javax.sound.midi.ShortMessage;
import org.praxislive.base.AbstractProperty;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.midi.impl.AbstractMidiInComponent;

/**
 *
 */
public class MidiControlIn extends AbstractMidiInComponent {

    private final static Logger LOG = Logger.getLogger(MidiControlIn.class.getName());
    
    private final ComponentInfo info;
    
    private int channel;
    private int controller;
    private double minimum;
    private double maximum;
    private ControlAddress binding;
    private ControlAddress returnAddress;

    public MidiControlIn() {
        channel = 0;
        controller = 0;
        minimum = 0;
        maximum = 1;

        registerControl("channel", new ChannelBinding());
        registerControl("controller", new ControllerBinding());
        registerControl("minimum", new MinBinding());
        registerControl("maximum", new MaxBinding());
        registerControl("binding", new AddressBinding());
        registerControl("_log", new LogControl());
        
        info = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .control("channel", c -> c
                        .property()
                        .defaultValue(PNumber.ONE)
                        .input(a -> a
                                .number()
                                .min(1)
                                .max(16)
                                .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                        ))
                .control("controller", c -> c
                        .property()
                        .defaultValue(PNumber.ZERO)
                        .input(a -> a
                                .number()
                                .min(0)
                                .max(127)
                                .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                        ))
                .control("minimum", c -> c
                        .property()
                        .defaultValue(PNumber.ZERO)
                        .input(PNumber.class)
                )
                .control("maximum", c -> c
                        .property()
                        .defaultValue(PNumber.ONE)
                        .input(PNumber.class)
                )
                .control("binding", c -> c
                        .property()
                        .input(a -> a
                                .type(ControlAddress.class)
                                .property(ArgumentInfo.KEY_ALLOW_EMPTY, PBoolean.TRUE)
                        ))
        );
    }

    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        ComponentAddress c = getAddress();
        if (c == null) {
            returnAddress = null;
        } else {
            returnAddress = ControlAddress.of(c, "_log");
        }
    }
    
    @Override
    public ComponentInfo getInfo() {
        return info;
    }

    @Override
    public void midiReceived(ShortMessage msg, long time) {
        if (msg.getChannel() == channel && msg.getCommand() == ShortMessage.CONTROL_CHANGE
                && msg.getData1() == controller && binding != null) {
            Call call = Call.createQuiet(binding, returnAddress, time, parseArgument(msg.getData2()));
            getLookup().find(PacketRouter.class).ifPresent(r -> r.route(call));
        }
    }

    private PNumber parseArgument(int value) {
        double min = minimum;
        double max = maximum;
        if (min == 0) {
            if (max == 127) {
                return PNumber.of(value);
            } else {
                double val = (value / 127.0) * max;
                return PNumber.of(val);
            }
        } else {
            double val = ((value / 127.0) * (max - min)) + min;
            return PNumber.of(val);
        }
    }


    private class ChannelBinding extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            PNumber pn = PNumber.from(arg).orElseThrow(IllegalArgumentException::new);
            int ch = pn.toIntValue() - 1;
            if (ch < 0 || ch > 15) {
                throw new IllegalArgumentException();
            }
            channel = ch;
        }

        @Override
        protected Value get() {
            return PNumber.of(channel + 1);
        }
        
    }
    
    private class ControllerBinding extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            PNumber pn = PNumber.from(arg).orElseThrow(IllegalArgumentException::new);
            int ctl = pn.toIntValue();
            if (ctl < 0 || ctl > 127) {
                throw new IllegalArgumentException();
            }
            controller = ctl;
        }

        @Override
        protected Value get() {
            return PNumber.of(controller);
        }
        
    }
    
    private class MinBinding extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            minimum = PNumber.from(arg)
                    .map(PNumber::value)
                    .orElseThrow(IllegalArgumentException::new);
        }

        @Override
        protected Value get() {
            return PNumber.of(minimum);
        }
        
    }
    
    private class MaxBinding extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            maximum = PNumber.from(arg)
                    .map(PNumber::value)
                    .orElseThrow(IllegalArgumentException::new);
        }

        @Override
        protected Value get() {
            return PNumber.of(maximum);
        }
        
    }
    

    private class AddressBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) throws Exception {
            if (value.isEmpty()) {
                binding = null;
            } else {
                binding = ControlAddress.coerce(value);
            }
        }

        @Override
        public Value get() {
            if (binding == null) {
                return PString.EMPTY;
            } else {
                return binding;
            }
        }

    }

    private class LogControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isError()) {
                LOG.warning(call.toString());
            }
        }

    }

}
