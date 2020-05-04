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
package org.praxislive.osc.components;

import java.net.SocketAddress;
import java.util.List;
import org.praxislive.base.AbstractComponent;
import org.praxislive.base.AbstractProperty;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Info;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.internal.osc.OSCListener;
import org.praxislive.internal.osc.OSCMessage;

/**
 *
 */
public class OSCInput extends AbstractComponent {

    private final OSCListener listener;
    private final ComponentInfo info;
    
    private OSCContext context;
    private ControlAddress sendAddress;
    private String oscAddress;
    private ControlAddress returnAddress;

    public OSCInput() {
        listener = new OSCListenerImpl();
        sendAddress = null;
        oscAddress = "";
        
        registerControl("address", new SendAddressBinding());
        registerControl("osc-address", new OSCAddressBinding());
        registerControl("_log", new OSCLogControl());
        
        info = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .control("address", c -> c
                        .property()
                        .input(a -> a
                                .type(ControlAddress.class)
                                .property(ArgumentInfo.KEY_ALLOW_EMPTY, PBoolean.TRUE)
                        )
                )
                .control("osc-address", c -> c
                        .property()
                        .input(a -> a
                                .string()
                                .emptyIsDefault()
                        )
                )
        );
        
    }

    @Override
    public ComponentInfo getInfo() {
        return info;
    }
    
    
    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        OSCContext ctxt = getLookup().find(OSCContext.class).orElse(null);
        if (ctxt != context) {
            if (context != null) {
                unregisterListener();
                context = null;
            }
            if (ctxt == null) {
                return;
            }
            context = ctxt;
            registerListener();
        }
        ComponentAddress c = getAddress();
        if (c == null) {
            returnAddress = null;
        } else {
            returnAddress = ControlAddress.of(c, "_log");
        }
    }
    
    private void registerListener() {
        if (sendAddress != null) {
            String osc = oscAddress.isEmpty() ?
                    sendAddress.toString() : oscAddress;
            context.addListener(osc, listener);
        }
    }
    
    private void unregisterListener() {
        if (sendAddress != null) {
            String osc = oscAddress.isEmpty() ?
                    sendAddress.toString() : oscAddress;
            context.removeListener(osc, listener);
        }
    }
    
    private void dispatch(OSCMessage msg, long time) {
        if (sendAddress == null) {
            return;
        }
        PacketRouter router = getLookup().find(PacketRouter.class).orElse(c -> {});
        int count = msg.getArgCount();
        List<Value> arguments;
        switch (count) {
            case 0:
                arguments = List.of();
                break;
            case 1:
                arguments = List.of(objectToArg(msg.getArg(0)));
                break;
            default:
                Value[] args = new Value[count];
                for (int i=0; i<count; i++) {
                    args[i] = objectToArg(msg.getArg(i));
                }   arguments = List.of(args);
                break;
        }
        router.route(Call.createQuiet(sendAddress, returnAddress, time, arguments));
    }
    
    private Value objectToArg(Object obj) {
       if (obj instanceof Boolean) {
            return ((Boolean) obj).booleanValue() ? PBoolean.TRUE : PBoolean.FALSE;
        }
        if (obj instanceof Integer) {
            return PNumber.of(((Integer) obj).intValue());
        }
        if (obj instanceof Number) {
            return PNumber.of(((Number) obj).doubleValue());
        }
        if (obj == null) {
            return PString.EMPTY;
        }
        return PString.of(obj);
    }
    
    private class SendAddressBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) throws Exception {
            ControlAddress send = value.isEmpty() ? null : ControlAddress.from(value).get();
            unregisterListener();
            sendAddress = send;
            registerListener();
        }

        @Override
        public Value get() {
            if (sendAddress == null) {
                return PString.EMPTY;
            } else {
                return sendAddress;
            }
        }
        
    }
    
    private class OSCAddressBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) {
            unregisterListener();
            oscAddress = value.toString();
            registerListener();
        }

        @Override
        public Value get() {
            return PString.of(oscAddress);
        }
        
    }

    private class OSCListenerImpl implements OSCListener {

        @Override
        public void messageReceived(OSCMessage oscm, SocketAddress sa, long time) {
            dispatch(oscm, time);
        }
    }
    
    private class OSCLogControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            
        }

        
    }
}
