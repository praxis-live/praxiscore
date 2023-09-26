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
 *
 */
package org.praxislive.tinkerforge;

import org.praxislive.code.AbstractBasicProperty;
import org.praxislive.code.CodeContext;
import org.praxislive.code.ControlDescriptor;
import org.praxislive.core.Value;
import org.praxislive.core.Control;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 *
 */
class DeviceProperty extends AbstractBasicProperty {

    private final static ControlInfo INFO = ControlInfo.createPropertyInfo(
            ArgumentInfo.of(PString.class,
                        PMap.of(ArgumentInfo.KEY_SUGGESTED_VALUES,
                                TFCodeContext.AUTO)),
            PString.EMPTY,
            PMap.EMPTY
    );

    private TFCodeContext context;
    
    @Override
    protected void set(long time, Value arg) throws Exception {
        context.setUID(arg.toString());
    }

    @Override
    protected Value get() {
        return PString.of(context.getUID());
    }

//    @Override
    public ControlInfo getInfo() {
        return INFO;
    }
    

    static class Descriptor extends ControlDescriptor<Descriptor> {

        private final DeviceProperty control;

        Descriptor(String id, int index) {
            super(Descriptor.class, id, Category.Property, index);
            control = new DeviceProperty();
        }

        @Override
        public ControlInfo controlInfo() {
            return control.getInfo();
        }

        @Override
        public void attach(CodeContext<?> context, Descriptor previous) {
            control.context = (TFCodeContext) context;
        }

        @Override
        public Control control() {
            return control;
        }

    }

}
