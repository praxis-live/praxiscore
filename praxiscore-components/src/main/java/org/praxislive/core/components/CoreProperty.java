/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
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
package org.praxislive.core.components;

import org.praxislive.code.GenerateTemplate;

import org.praxislive.core.code.CoreCodeDelegate;

// default imports
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import org.praxislive.core.*;
import org.praxislive.core.types.*;
import org.praxislive.code.userapi.*;
import static org.praxislive.code.userapi.Constants.*;

/**
 *
 *
 */
@GenerateTemplate(CoreProperty.TEMPLATE_PATH)
public class CoreProperty extends CoreCodeDelegate {

    final static String TEMPLATE_PATH = "resources/property.pxj";
    // PXJ-BEGIN:body

    @P(1) @Config.Port(false) @OnChange("valueChanged")
    Property value;

    @Out(1) Output out;

    @Override
    @Config.Expose("value")
    public void init() {
    }

    @Override
    public void starting() {
        out.send(value.get());
    }

    void valueChanged() {
        out.send(value.get());
    }

    // PXJ-END:body
}
