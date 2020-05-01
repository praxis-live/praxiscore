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
 *
 */
package org.praxislive.gui.impl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.ConstraintParser;
import net.miginfocom.layout.IDEUtil;
import org.praxislive.base.AbstractProperty;
import org.praxislive.core.Value;
import org.praxislive.core.types.PString;
import org.praxislive.gui.Keys;

/**
 *
 */
class LayoutBinding extends AbstractProperty implements PropertyChangeListener {

    private final JComponent component;
    private PString layoutString = PString.EMPTY;

    LayoutBinding(JComponent component) {
        this.component = component;
        component.addPropertyChangeListener(Keys.LayoutConstraint, this);
    }

    @Override
    protected void set(long time, Value arg) throws Exception {
        CC constraint;
        PString value = PString.of(arg);
        if (arg.isEmpty()) {
            constraint = null;
        } else {
            constraint = ConstraintParser.parseComponentConstraint(value.toString());
        }
        component.putClientProperty(Keys.LayoutConstraint, constraint);
        layoutString = value;
    }

    @Override
    protected Value get() {
        if (layoutString == null) {
            try {
                CC constraint = (CC) component.getClientProperty(Keys.LayoutConstraint);
                layoutString = PString.of(IDEUtil.getConstraintString(constraint, false));
            } catch (Exception ex) {
                layoutString = PString.EMPTY;
            }
        }
        return layoutString;
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        layoutString = null;
    }

}
