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

import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import org.praxislive.base.AbstractComponent;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Container;
import org.praxislive.core.Info;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.gui.Keys;
import org.praxislive.gui.GuiContext;

/**
 * Abstract superclass for default GuiComponent model.
 *
 * @author Neil C Smith
 */
public abstract class AbstractGuiComponent extends AbstractComponent {

    private JComponent component;
    private LabelBinding label;
    private LayoutBinding layout;
    private GuiContext context;
    private ComponentInfo info;

    public final JComponent getSwingComponent() {
        if (EventQueue.isDispatchThread()) {
            if (component == null) {
                component = createSwingComponent();
                var cmpInfo = Info.component();
                cmpInfo.merge(ComponentProtocol.API_INFO);
                label = new LabelBinding(component);
                label.addPropertyChangeListener(new LabelListener());
                registerControl("label", label);
                cmpInfo.control("label", c -> c.property().input(i -> i.string()));
                initControls(cmpInfo);
                layout = new LayoutBinding(component);
                registerControl("layout", layout);
                cmpInfo.control("layout", c -> c.property().input(i -> i.string()));
                info = cmpInfo.build();
                updateLabel();
            }
            return component;
        } else {
            return null;
        }
    }

    @Override
    public ComponentInfo getInfo() {
        return info;
    }
    
    protected void initControls(Info.ComponentInfoBuilder cmpInfo) {
        // no op hook
    }

    @Override
    public void parentNotify(Container parent) throws VetoException {
        if (EventQueue.isDispatchThread()) {
            super.parentNotify(parent);
            // call getSwingComponent() early to ensure JComponent creation
            getSwingComponent();
        } else {
            throw new VetoException("Trying to install GUI component in GUI incompatible container.");
        }

    }

    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
//        getSwingComponent().putClientProperty(Keys.Address, getAddress());
        GuiContext ctxt = getLookup().find(GuiContext.class).orElse(null);
        if (context != ctxt) {
            if (context != null) {
                context.getContainer().remove(getSwingComponent());
            }
            if (ctxt != null) {
                ctxt.getContainer().add(getSwingComponent());
            }
            context = ctxt;
        }
        getSwingComponent().putClientProperty(Keys.Address, getAddress());
    }

    protected abstract JComponent createSwingComponent();

    protected void updateLabel() {
        // no op hook
    }

    protected String getLabel() {
        return label == null ? "" : label.get().toString();
    }

    protected boolean isLabelOnParent() {
        return label == null ? false : label.isLabelOnParent();
    }

    private class LabelListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent pce) {
            updateLabel();
        }

    }
}
