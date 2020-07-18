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
@GenerateTemplate(TFRotaryPoti.TEMPLATE_PATH)
public class TFRotaryPoti extends TFCodeDelegate {

    final static String TEMPLATE_PATH = "resources/rotary_poti.pxj";

    // PXJ-BEGIN:body

    @TinkerForge BrickletRotaryPoti poti;
    
    @P(1) @Config.Port(false) @OnChange("updateValue")
    boolean normalize;
    @P(2) @ReadOnly
    double value;
    @Out(1) @ID("value")
    Output out;
    
    Listener listener = new Listener();
    int raw;
    
    @Override
    public void setup() {
        poti.addPositionListener(listener);
        try {
            poti.setPositionCallbackPeriod(CALLBACK_PERIOD);
        } catch (TinkerforgeException ex) {
        }
    }

    @Override
    public void dispose() {
        poti.removePositionListener(listener);
        try {
            poti.setPositionCallbackPeriod(0);
        } catch (TinkerforgeException ex) {
        }
    }
    
    private void updateValue() {
        if (normalize) {
            value = normalize(raw);
        } else {
            value = raw;
        }
        out.send(value);
    }
    
    private double normalize(int val) {
        return (val + 150) / 300.0;
    }
    
    private class Listener implements BrickletRotaryPoti.PositionListener {

        @Override
        public void position(short position) {
            raw = position;
            updateValue();
        }
        
    }

    // PXJ-END:body
}
