/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Neil C Smith.
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
package org.praxislive.gui.components;

import org.praxislive.base.AbstractComponentFactory;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.ComponentFactoryProvider;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class GuiFactoryProvider implements ComponentFactoryProvider {

    private final static ComponentFactory factory = new Factory();

    public ComponentFactory getFactory() {
        return factory;
    }

    private static class Factory extends AbstractComponentFactory {

        private Factory() {
            build();
        }

        private void build() {
            // ROOT
            addRoot("root:gui", DefaultGuiRoot.class);

            // COMPONENTS
            
            add("gui:h-slider", HSlider.class);
            add("gui:v-slider", VSlider.class);
            add("gui:h-rangeslider", HRangeSlider.class);
            /* addComponent("gui:v-rangeslider", VRangeSlider.class); temp removal */
            add("gui:button", Button.class);
            add("gui:togglebutton", ToggleButton.class);
            add("gui:xy-pad", XYController.class);
            add("gui:filefield", FileField.class);
            add("gui:combobox", ComboBox.class);
            add("gui:textfield", TextField.class);
            // GUI containers
            add("gui:panel", Panel.class);
            add("gui:tabs", Tabs.class);
            /*addComponent("gui:textarea", TextArea.class); temp removal */

        }
    }
}
