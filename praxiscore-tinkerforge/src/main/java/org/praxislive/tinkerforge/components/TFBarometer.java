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
@GenerateTemplate(TFBarometer.TEMPLATE_PATH)
public class TFBarometer extends TFCodeDelegate {

    final static String TEMPLATE_PATH = "resources/barometer.pxj";

    // PXJ-BEGIN:body

    final static double DEFAULT_REFERENCE_PRESSURE = 1013.25;
    
    @TinkerForge BrickletBarometer device;
    
    @P(1) @OnChange("referenceChanged") @ID("reference-pressure")
    @Config.Port(false) @Type.Number(def = DEFAULT_REFERENCE_PRESSURE)
    double reference;
    @P(2) @ReadOnly
    double pressure;
    @P(3) @ReadOnly
    double altitude;
    @Out(1) @ID("pressure")
    Output pressureOut;
    @Out(2) @ID("altitude")
    Output altitudeOut;
    
    Listener listener = new Listener();
    boolean updateReference;
    
    @Override
    public void setup() {
        device.addAirPressureListener(listener);
        device.addAltitudeListener(listener);
        try {
            device.setAirPressureCallbackPeriod(CALLBACK_PERIOD);
            device.setAltitudeCallbackPeriod(CALLBACK_PERIOD);
        } catch (TinkerforgeException ex) {
            log(WARNING, ex);
        }
        updateReference = true;
    }

    @Override
    public void update() {
        if (updateReference) {
            try {
                device.setReferenceAirPressure((int) (reference * 1000));
            } catch (TinkerforgeException ex) {
                log(WARNING, ex);
            }
            updateReference = false;
        }
    }

    @Override
    public void dispose() {
        device.removeAirPressureListener(listener);
        device.removeAltitudeListener(listener);
        try {
            device.setAirPressureCallbackPeriod(0);
            device.setAltitudeCallbackPeriod(0);
        } catch (TinkerforgeException ex) {
            log(WARNING, ex);
        }
    }
    
    void referenceChanged() {
        updateReference = true;
    }
    
    private class Listener implements BrickletBarometer.AirPressureListener,
            BrickletBarometer.AltitudeListener {

        @Override
        public void airPressure(int air) {
            double p = air / 1000.0;
            pressure = p;
            pressureOut.send(p);
        }

        @Override
        public void altitude(int alt) {
            double a = alt / 100.0;
            altitude = a;
            altitudeOut.send(a);
        }
        
    }

    // PXJ-END:body
}
