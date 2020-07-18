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
@GenerateTemplate(TFLCD20x4.TEMPLATE_PATH)
public class TFLCD20x4 extends TFCodeDelegate {

    final static String TEMPLATE_PATH = "resources/lcd20x4.pxj";

    // PXJ-BEGIN:body

    @TinkerForge BrickletLCD20x4 lcd;
    
    @P(1) @OnChange("linesChanged")
    String line1;
    @P(2) @OnChange("linesChanged")
    String line2;
    @P(3) @OnChange("linesChanged")
    String line3;
    @P(4) @OnChange("linesChanged")
    String line4;
    
    @P(5) @OnChange("lightChanged")
    boolean backlight;
    
    @P(6) @Type.String(allowed = {"Value", "Signal"}, def = "Value") @Config.Port(false)
    String button_mode;
    
    @AuxOut(1) Output button1;
    @AuxOut(2) Output button2;
    @AuxOut(3) Output button3;
    @AuxOut(4) Output button4;

    Listener listener =  new Listener();
    boolean updateLines, updateLight;
    
    @Override
    public void setup() {
        lcd.addButtonPressedListener(listener);
        lcd.addButtonReleasedListener(listener);
        updateLines = true;
        updateLight = true;
    }

    @Override
    public void update() {
        if (updateLines) {
            try {
                lcd.writeLine((short) 0, (short) 0, lcdString(line1, 20));
                lcd.writeLine((short) 1, (short) 0, lcdString(line2, 20));
                lcd.writeLine((short) 2, (short) 0, lcdString(line3, 20));
                lcd.writeLine((short) 3, (short) 0, lcdString(line4, 20));
            } catch (Exception ex) {
            }
            updateLines = false;
        }
        if (updateLight) {
            try {
                if (backlight) {
                    lcd.backlightOn();
                } else {
                    lcd.backlightOff();
                }      
            } catch (Exception ex) {
            }
        }
    }

    @Override
    public void dispose() {
        try {
            lcd.clearDisplay();
            lcd.backlightOff();
            lcd.removeButtonPressedListener(listener);
            lcd.removeButtonReleasedListener(listener);
        } catch (Exception ex) {
        }
    }
    
    void linesChanged() {
        updateLines = true;
    }
    
    void lightChanged() {
        updateLight = true;
    }
    
    private class Listener implements BrickletLCD20x4.ButtonPressedListener,
            BrickletLCD20x4.ButtonReleasedListener {

        @Override
        public void buttonPressed(short button) {
            if ("Value".equals(button_mode)) {
                sendValue(button, true);
            } else {
                sendSignal(button);
            }
        }

        @Override
        public void buttonReleased(short button) {
            if ("Value".equals(button_mode)) {
                sendValue(button, false);
            }
        }
        
        void sendValue(short button, boolean value) {
            switch (button) {
                    case 0 :
                        button1.send(value);
                        break;
                    case 1 :
                        button2.send(value);
                        break;
                    case 2 :
                        button3.send(value);
                        break;
                    case 3 :
                        button4.send(value);
                        break;
                }
        }
        
        void sendSignal(short button) {
            switch (button) {
                    case 0 :
                        button1.send();
                        break;
                    case 1 :
                        button2.send();
                        break;
                    case 2 :
                        button3.send();
                        break;
                    case 3 :
                        button4.send();
                        break;
                }
        }
        
    }

    // PXJ-END:body
}
