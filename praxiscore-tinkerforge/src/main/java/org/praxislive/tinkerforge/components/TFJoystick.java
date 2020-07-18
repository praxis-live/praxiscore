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
package org.praxislive.tinkerforge.components;

import org.praxislive.code.GenerateTemplate;
import org.praxislive.tinkerforge.TFCodeDelegate;

// default imports
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import org.praxislive.core.*;
import org.praxislive.core.types.*;
import org.praxislive.code.userapi.*;
import com.tinkerforge.*;
import org.praxislive.tinkerforge.userapi.*;
import static org.praxislive.code.userapi.Constants.*;
import static org.praxislive.tinkerforge.userapi.Constants.*;

/**
 *
 */
@GenerateTemplate(TFJoystick.TEMPLATE_PATH)
public class TFJoystick extends TFCodeDelegate {

    final static String TEMPLATE_PATH = "resources/joystick.pxj";

    // PXJ-BEGIN:body

    final String VALUE = "Value";
    final String SIGNAL = "Signal";
    
    @TinkerForge BrickletJoystick joystick;
    
    @P(1) @Config.Port(false) @OnChange("updatePosition")
            boolean normalize;
    @P(2) @Config.Port(false) @Type.String(allowed = {VALUE, SIGNAL})
            String buttonMode;
    
    @P(3) @ReadOnly double x;
    @P(4) @ReadOnly double y;
    @P(5) @ReadOnly boolean button;
    
    
    @Out(1) @ID("x") Output outX;
    @Out(2) @ID("y") Output outY;
    @Out(3) @ID("button") Output outButton;
    
    Listener listener = new Listener();
    int rawX, rawY;
    
    @Override
    public void setup() {
        joystick.addPositionListener(listener);
        joystick.addPressedListener(listener);
        joystick.addReleasedListener(listener);
        try {
            joystick.setPositionCallbackPeriod(CALLBACK_PERIOD);
        } catch (TinkerforgeException ex) {
        }
    }

    @Override
    public void dispose() {
        joystick.removePositionListener(listener);
        joystick.removePressedListener(listener);
        joystick.removeReleasedListener(listener);
        try {
            joystick.setPositionCallbackPeriod(0);
        } catch (TinkerforgeException ex) {
        }
    }
    
    private void updatePosition() {
        if (normalize) {
            x = normalize(rawX);
            y = normalize(rawY);
        } else {
            x = rawX;
            y = rawY;
        }
        outX.send(x);
        outY.send(y);
    }
    
    private double normalize(int val) {
        return (val + 100) / 200.0;
    }
    
    class Listener implements BrickletJoystick.PositionListener,
            BrickletJoystick.PressedListener, BrickletJoystick.ReleasedListener {

        @Override
        public void position(short sx, short sy) {
            rawX = sx;
            rawY = sy;
            updatePosition();
        }

        @Override
        public void pressed() {
            button = true;
            if (VALUE.equals(buttonMode)) {
                outButton.send(true);
            } else {
                outButton.send();
            }
        }

        @Override
        public void released() {
            button = false;
            if (VALUE.equals(buttonMode)) {
                outButton.send(false);
            }
        }
        
    }

    // PXJ-END:body
}
