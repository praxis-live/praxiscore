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
package org.praxislive.tinkerforge.components;

import org.praxislive.code.AbstractComponentFactory;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.ComponentFactoryProvider;
import org.praxislive.tinkerforge.TFCode;
import org.praxislive.tinkerforge.TFCodeDelegate;

/**
 *
 */
public class TFComponents implements ComponentFactoryProvider {
    
    private final static Factory instance = new Factory();

    @Override
    public ComponentFactory getFactory() {
        return instance;
    }

    private static class Factory extends AbstractComponentFactory {

        private Factory() {
            build();
        }

        private void build() {

            add("tinkerforge:custom", TFCustom.class, TFCustom.TEMPLATE_PATH);
            
            add("tinkerforge:ambient-light", TFAmbientLight.class, TFAmbientLight.TEMPLATE_PATH);
            add("tinkerforge:analog-in", TFAnalogIn.class, TFAnalogIn.TEMPLATE_PATH);
            add("tinkerforge:analog-out", TFAnalogOut.class, TFAnalogOut.TEMPLATE_PATH);
            add("tinkerforge:barometer", TFBarometer.class, TFBarometer.TEMPLATE_PATH);
            add("tinkerforge:distance-ir", TFDistanceIR.class, TFDistanceIR.TEMPLATE_PATH);
            add("tinkerforge:dual-relay", TFDualRelay.class, TFDualRelay.TEMPLATE_PATH);
            add("tinkerforge:io16", TFIO16.class, TFIO16.TEMPLATE_PATH);
            add("tinkerforge:joystick", TFJoystick.class, TFJoystick.TEMPLATE_PATH);
            add("tinkerforge:lcd20x4", TFLCD20x4.class, TFLCD20x4.TEMPLATE_PATH);
            add("tinkerforge:linear-poti", TFLinearPoti.class, TFLinearPoti.TEMPLATE_PATH);
            add("tinkerforge:rotary-poti", TFRotaryPoti.class, TFRotaryPoti.TEMPLATE_PATH);
            add("tinkerforge:servo", TFServo.class, TFServo.TEMPLATE_PATH);
            add("tinkerforge:temperature", TFTemperature.class, TFTemperature.TEMPLATE_PATH);
            add("tinkerforge:temperature-ir", TFTemperatureIR.class, TFTemperatureIR.TEMPLATE_PATH);

        }

        private void add(String type, Class<? extends TFCodeDelegate> cls, String path) {
            add(TFCode.base().create(type, cls, source(path)));
        }
        
    }
}
