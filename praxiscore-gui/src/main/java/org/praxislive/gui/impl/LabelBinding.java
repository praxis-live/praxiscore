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
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.praxislive.base.AbstractProperty;
import org.praxislive.core.Value;
import org.praxislive.core.types.PString;
import org.praxislive.gui.Keys;

/**
 *
 */
class LabelBinding extends AbstractProperty {

    private final JComponent component;
    private final ComponentListener cl;
    private final PropertyChangeSupport pcs;
    private PString label;
    private boolean ignoreChange;

    LabelBinding(JComponent component) {
        this.component = component;
        cl = new ComponentListener();
        component.addPropertyChangeListener(Keys.Label, cl);
        component.addPropertyChangeListener(Keys.LabelOnParent, cl);
        pcs = new PropertyChangeSupport(this);
    }

    @Override
    protected void set(long time, Value arg) throws Exception {
        label = PString.of(arg);
        ignoreChange = true;
        component.putClientProperty(Keys.Label, label.toString());
        ignoreChange = false;
    }
    
    @Override
    protected Value get() {
        if (label == null) {
            Object o = component.getClientProperty(Keys.Label);
            if (o instanceof String) {
                label = PString.of(o);
            } else {
                label = PString.EMPTY;
            }
        }
        return label;
    }

    boolean isLabelOnParent() {
        Object o = component.getClientProperty(Keys.LabelOnParent);
        if (o instanceof Boolean) {
            return ((Boolean)o).booleanValue();
        }
        return false;
    }

    void addPropertyChangeListener(PropertyChangeListener pl) {
        pcs.addPropertyChangeListener(pl);
    }
    
    void removePropertyChangeListener(PropertyChangeListener pl) {
        pcs.removePropertyChangeListener(pl);
    }

    private class ComponentListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent pce) {
            if (!ignoreChange) {
                label = null;
            }
            pcs.firePropertyChange(pce.getPropertyName(), pce.getOldValue(), pce.getNewValue());
        }
    }
}
