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
package org.praxislive.code;

import java.util.Optional;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Container;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlPort;
import org.praxislive.core.Lookup;
import org.praxislive.core.Port;
import org.praxislive.core.Value;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 * CodeDelegate is the base class for all user rewritable code. Most uses will
 * extend from {@link DefaultCodeDelegate}.
 */
public abstract class CodeDelegate {

    private CodeContext<? extends CodeDelegate> context;
    
        /**
     * Send a log message.
     * 
     * @param level
     * @param msg
     */
    public final void log(LogLevel level, String msg) {
        getContext().getLog().log(level, msg);
    }

    /**
     * Send a log message with associated Exception type.
     * 
     * @param level
     * @param ex
     */
    public final void log(LogLevel level, Exception ex) {
        getContext().getLog().log(level, ex);
    }

    /**
     * Send a log message with associated Exception.
     * 
     * @param level
     * @param ex
     * @param msg
     */
    public final void log(LogLevel level, Exception ex, String msg) {
        getContext().getLog().log(level, ex, msg);
    }

    /**
     * Send a log message with associated Exception type.
     * 
     * @param level
     * @param type
     * @param msg
     */
    public final void log(LogLevel level, Class<? extends Exception> type, String msg) {
        getContext().getLog().log(level, type, msg);
    }

    /**
     * Check whether the messages at the given log level are being sent.
     * 
     * @param level
     * @return
     */
    public final boolean isLoggable(LogLevel level) {
        return getContext().getLogLevel().isLoggable(level);
    }

    /**
     * Send a value to a port on another component. The other component must have
     * the same parent.
     * 
     * @param componentID ID of the other component
     * @param portID ID of the port on the other component
     * @param value
     */
    public final void transmit(String componentID, String portID, String value) {
        this.transmit(componentID, portID, PString.of(value));
    }

    /**
     * Send a value to a port on another component. The other component must have
     * the same parent.
     * 
     * @param componentID ID of the other component
     * @param portID ID of the port on the other component
     * @param value
     */
    public final void transmit(String componentID, String portID, Value value) {
        ControlPort.Input port = findPort(componentID, portID);
        if (port == null) {
            log(LogLevel.ERROR, "Can't find an input port at " + componentID + "!" + portID);
        } else {
            try {
                port.receive(time(), value);
            } catch (Exception ex) {
                log(LogLevel.ERROR, ex);
            }
        }
    }

    /**
     * Send a value to a port on another component. The other component must have
     * the same parent.
     * 
     * @param componentID ID of the other component
     * @param portID ID of the port on the other component
     * @param value
     */
    public final void transmit(String componentID, String portID, double value) {
        ControlPort.Input port = findPort(componentID, portID);
        if (port == null) {
            log(LogLevel.ERROR, "Can't find an input port at " + componentID + "!" + portID);
        } else {
            try {
                port.receive(time(), value);
            } catch (Exception ex) {
                log(LogLevel.ERROR, ex);
            }
        }
    }

    private ControlPort.Input findPort(String cmp, String port) {
        Component thisCmp = getContext().getComponent();
        Container parent = thisCmp.getParent();
        if (parent == null) {
            return null;
        }
        Component thatCmp = parent.getChild(cmp);
        if (thatCmp == null) {
            return null;
        }
        Port thatPort = thatCmp.getPort(port);
        if (thatPort instanceof ControlPort.Input) {
            return (ControlPort.Input) thatPort;
        } else {
            return null;
        }
    }
    
    /**
     * Send a message to a Control.
     * 
     * @param destination address of control
     * @param value message value
     */
    public final void tell(ControlAddress destination, String value) {
        tell(destination, PString.of(value));
    }

    /**
     * Send a message to a Control.
     * 
     * @param destination address of control
     * @param value message value
     */
    public final void tell(ControlAddress destination, double value) {
        tell(destination, PNumber.of(value));
    }

    /**
     * Send a message to a Control.
     * 
     * @param destination address of control
     * @param value message value
     */
    public final void tell(ControlAddress destination, Value value) {
        Call call = Call.createQuiet(destination, self("_log"), getContext().getTime(), value);
        getContext().getComponent().getPacketRouter().route(call);
    }
    
    /**
     * Send a message to a Control in the given number of seconds or fractions
     * of second from now.
     * 
     * @param seconds from now
     * @param destination address of control
     * @param value message value
     */
    public final void tellIn(double seconds, ControlAddress destination, String value) {
        tellIn(seconds, destination, PString.of(value));
    }

    /**
     * Send a message to a Control in the given number of seconds or fractions
     * of second from now.
     * 
     * @param seconds from now
     * @param destination address of control
     * @param value message value
     */
    public final void tellIn(double seconds, ControlAddress destination, double value) {
        tellIn(seconds, destination, PNumber.of(value));
    }

    /**
     * Send a message to a Control in the given number of seconds or fractions
     * of second from now.
     * 
     * @param seconds from now
     * @param destination address of control
     * @param value message value
     */
    public final void tellIn(double seconds, ControlAddress destination, Value value) {
        long time = getContext().getTime() + ((long) (seconds * 1_000_000_000));
        Call call = Call.createQuiet(destination, self("_log"), time, value);
        getContext().getComponent().getPacketRouter().route(call);
    }

    /**
     * Get this component's address.
     * 
     * @return address of self
     */
    public final ComponentAddress self() {
        return getContext().getComponent().getAddress();
    }

    /**
     * Get the address of a control on this component.
     * 
     * @param control id of control
     * @return address of control
     */
    public final ControlAddress self(String control) {
        return ControlAddress.of(self(), control);
    }
    
    
    
    /**
     * Return a Lookup for finding instances of features.
     * 
     * @return Lookup context
     */
    public Lookup getLookup() {
        return getContext().getLookup();
    }
    
    /**
     * Search for an instance of the given type.
     * @param <T>
     * @param type class to search for
     * @return Optional wrapping the result if found, or empty if not
     */
    public <T> Optional<T> find(Class<T> type) {
        return getLookup().find(type);
    }

    /**
     * The current clocktime in nanoseconds. May only be used relatively to itself, 
     * and may be negative.
     * 
     * @return
     */
    public final long time() {
        return getContext().getTime();
    }

    /**
     * The current time in milliseconds since the root was started.
     * 
     * @return
     */
    public final long millis() {
        return (time() - getContext().getExecutionContext().getStartTime())
                / 1_000_000;
    }

    CodeContext<? extends CodeDelegate> getContext() {
        return context;
    }

    void setContext(CodeContext<? extends CodeDelegate> context) {
        this.context = context;
    }

}
