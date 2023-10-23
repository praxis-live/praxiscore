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
package org.praxislive.code;

import org.praxislive.base.MetaControl;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.protocols.ComponentProtocol;

final class MetaDescriptor extends ControlDescriptor<MetaDescriptor> {

    private MetaControl control;

    MetaDescriptor(int index) {
        super(MetaDescriptor.class, ComponentProtocol.META, Category.Internal, index);
    }

    @Override
    public void attach(CodeContext<?> context, MetaDescriptor previous) {
        if (previous != null) {
            control = previous.control;
            previous.control = null;
        } else {
            control = new MetaControl();
        }
    }

    @Override
    public Control control() {
        return control;
    }

    @Override
    public ControlInfo controlInfo() {
        return ComponentProtocol.META_INFO;
    }

}
