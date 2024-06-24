/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2024 Neil C Smith.
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
import org.praxislive.base.AbstractContainer;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Container;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.VetoException;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.gui.GuiContext;
import org.praxislive.gui.Keys;

/**
 *
 */
public abstract class AbstractGuiContainer extends AbstractContainer {

    private JComponent component;
    private Lookup lookup;
    private LabelBinding label;
    private GuiContext context;
    private LayoutBinding layout;
    private ComponentInfo info;

    public final JComponent getSwingContainer() {
        if (EventQueue.isDispatchThread()) {
            if (component == null) {
                component = createSwingContainer();
                var cmpInfo = Info.component();
                cmpInfo.merge(ComponentProtocol.API_INFO);
                cmpInfo.merge(ContainerProtocol.API_INFO);
                cmpInfo.control(ContainerProtocol.SUPPORTED_TYPES,
                        ContainerProtocol.SUPPORTED_TYPES_INFO);
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

    @Override
    public void write(TreeWriter writer) {
        super.write(writer);
        Value labelValue = label.get();
        if (!labelValue.isEmpty()) {
            writer.writeProperty("label", labelValue);
        }
        Value layoutValue = layout.get();
        if (!layoutValue.isEmpty()) {
            writer.writeProperty("layout", layoutValue);
        }
    }

    protected void initControls(Info.ComponentInfoBuilder cmpInfo) {
        // no op hook
    }

    @Override
    public void parentNotify(Container parent) throws VetoException {
        if (EventQueue.isDispatchThread()) {
            super.parentNotify(parent);
            getSwingContainer();
        } else {
            throw new VetoException("Trying to install GUI component in GUI incompatible container.");
        }
    }

    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        // use super.getLookup() - don't want our own!
        GuiContext ctxt = super.getLookup().find(GuiContext.class).orElse(null);
        if (context != ctxt) {
            if (context != null) {
                context.getContainer().remove(getSwingContainer());
            }
            if (ctxt != null) {
                ctxt.getContainer().add(getSwingContainer());
            }
            context = ctxt;
        }
        getSwingContainer().putClientProperty(Keys.Address, getAddress());
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), new GuiContext() {

                @Override
                public JComponent getContainer() {
                    return getSwingContainer();
                }
            });
        }
        return lookup;
    }

    protected abstract JComponent createSwingContainer();

    protected void updateLabel() {
        // no op hook
    }

    protected String getLabel() {
        return label.get().toString();
    }

    protected boolean isLabelOnParent() {
        return label.isLabelOnParent();
    }

    private class LabelListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent pce) {
            updateLabel();
        }

    }

}
