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
@GenerateTemplate(TFTemperatureIR.TEMPLATE_PATH)
public class TFTemperatureIR extends TFCodeDelegate {

    final static String TEMPLATE_PATH = "resources/temperature_ir.pxj";

    // PXJ-BEGIN:body

    @TinkerForge BrickletTemperatureIR device;
    
    @P(1) @ReadOnly
    double object;
    @P(2) @ReadOnly
    double ambient;
    @Out(1) @ID("object")
    Output objOut;
    @Out(2) @ID("ambient")
    Output ambOut;
    
    Listener listener = new Listener();
    
    @Override
    public void setup() {
        device.addAmbientTemperatureListener(listener);
        device.addObjectTemperatureListener(listener);
        try {
            device.setAmbientTemperatureCallbackPeriod(CALLBACK_PERIOD);
            device.setObjectTemperatureCallbackPeriod(CALLBACK_PERIOD);
        } catch (TinkerforgeException ex) {
            log(WARNING, ex);
        }
    }

    @Override
    public void dispose() {
        device.removeAmbientTemperatureListener(listener);
        device.removeObjectTemperatureListener(listener);
        try {
            device.setAmbientTemperatureCallbackPeriod(0);
            device.setObjectTemperatureCallbackPeriod(0);
        } catch (TinkerforgeException ex) {
            log(WARNING, ex);
        }
    }
    
    private class Listener implements
            BrickletTemperatureIR.AmbientTemperatureListener,
            BrickletTemperatureIR.ObjectTemperatureListener {

        @Override
        public void ambientTemperature(short amb) {
            double t = amb / 10.0;
            ambient = t;
            ambOut.send(t);
        }

        @Override
        public void objectTemperature(short obj) {
            double t = obj / 10.0;
            object = t;
            objOut.send(t);
        }
        
    }
    
    // PXJ-END:body
}
