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
@GenerateTemplate(TFAnalogOut.TEMPLATE_PATH)
public class TFAnalogOut extends TFCodeDelegate {

    final static String TEMPLATE_PATH = "resources/analog_out.pxj";

    // PXJ-BEGIN:body
    
    @TinkerForge BrickletAnalogOut device;

    @P(1) @Type.Number(min = 0, max = 5) @OnChange("voltageChanged")
    double voltage;

    boolean updateVoltage;

    @Override
    public void setup() {
        try {
            device.setMode(BrickletAnalogOut.MODE_ANALOG_VALUE);
        } catch (TinkerforgeException ex) {
            log(WARNING, ex);
        }
        updateVoltage = true;
    }

    @Override
    public void update() {
        if (updateVoltage) {
            int v = (int) ((voltage * 1000) + 0.5);
            if (v < 0) {
                v = 0;
            } else if (v > 5000) {
                v = 5000;
            }
            try {
                device.setVoltage(v);
            } catch (TinkerforgeException ex) {
                log(WARNING, ex);
            }
            updateVoltage = false;
        }
    }

    @Override
    public void dispose() {
        try {
            device.setVoltage(0);
        } catch (TinkerforgeException ex) {
            log(WARNING, ex);
        }
    }

    void voltageChanged() {
        updateVoltage = true;
    }
    
    // PXJ-END:body
}
