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
package org.praxislive.core.protocols;

import java.util.stream.Stream;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Protocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;

/**
 * Protocol for a component that can be started and stopped.
 */
public class StartableProtocol implements Protocol {

    @Deprecated
    public final static StartableProtocol INSTANCE = new StartableProtocol();

    /**
     * Name of the start control.
     */
    public final static String START = "start";

    /**
     * Name of the stop control.
     */
    public final static String STOP = "stop";

    /**
     * Name of the is-running control.
     */
    public final static String IS_RUNNING = "is-running";

    /**
     * Info for the start control. It is an action control that should "start"
     * the component. It may respond with an error if for some reason the
     * component cannot be started.
     */
    public final static ControlInfo START_INFO = ControlInfo.createActionInfo(PMap.EMPTY);

    /**
     * Info for the stop control. It is an action control that should "stop" the
     * component. It may respond with an error if for some reason the component
     * cannot be stopped.
     */
    public final static ControlInfo STOP_INFO = ControlInfo.createActionInfo(PMap.EMPTY);

    /**
     * Info for the is-running control. It is a read-only boolean property that
     * responds whether the component is currently running / started.
     */
    public final static ControlInfo IS_RUNNING_INFO
            = ControlInfo.createReadOnlyPropertyInfo(
                    PBoolean.info(),
                    null);
    ;

    /**
     * A component info for this protocol. Can be used with
     * {@link Info.ComponentInfoBuilder#merge(org.praxislive.core.ComponentInfo)}.
     */
    public static final ComponentInfo API_INFO = Info.component(cmp -> cmp
            .protocol(StartableProtocol.class)
            .control(START, START_INFO)
            .control(STOP, STOP_INFO)
            .control(IS_RUNNING, IS_RUNNING_INFO)
    );

    @Override
    public Stream<String> controls() {
        return Stream.of(START, STOP, IS_RUNNING);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        switch (control) {
            case START:
                return START_INFO;
            case STOP:
                return STOP_INFO;
            case IS_RUNNING:
                return IS_RUNNING_INFO;
        }
        throw new IllegalArgumentException();
    }
}
