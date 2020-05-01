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
package org.praxislive.gui.components;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.BindingContext;
import org.praxislive.core.Value;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ValueFormatException;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.gui.impl.AbstractGuiComponent;
import org.praxislive.gui.impl.BoundedValueAdaptor;

/**
 *
 */
public class XYController extends AbstractGuiComponent {

    private static Logger logger = Logger.getLogger(XYController.class.getName());
    
    private final Preferences xPrefs;
    private final Preferences yPrefs;
    
    private BindingContext bindingContext;
    private Box container;
    private JXYController controller;
    private BoundedValueAdaptor xAdaptor;
    private BoundedValueAdaptor yAdaptor;
    private ControlAddress xBinding;
    private ControlAddress yBinding;

    public XYController() {
        xPrefs = new Preferences();
        yPrefs = new Preferences();
    }

    @Override
    protected void initControls(Info.ComponentInfoBuilder cmpInfo) {
        super.initControls(cmpInfo);
        registerControl("binding-x", new XAddressBinding());
        registerControl("binding-y", new YAddressBinding());
        
        var bindingInfo = Info.control(c -> c.property().input(ControlAddress.class)
                .property(ArgumentInfo.KEY_ALLOW_EMPTY, PBoolean.TRUE));
        cmpInfo.control("binding-x", bindingInfo);
        cmpInfo.control("binding-y", bindingInfo);
        
        registerControl("minimum-x", new MinBinding(xPrefs));
        registerControl("minimum-y", new MinBinding(yPrefs));
        registerControl("maximum-x", new MaxBinding(xPrefs));
        registerControl("maximum-y", new MaxBinding(yPrefs));
        
        var rangeInfo = Info.control(c -> c.property().input(Value.class)
                .property(ArgumentInfo.KEY_ALLOW_EMPTY, PBoolean.TRUE)
                .property(ArgumentInfo.KEY_EMPTY_IS_DEFAULT, PBoolean.TRUE)
        );
        cmpInfo.control("minimum-x", rangeInfo);
        cmpInfo.control("minimum-y", rangeInfo);
        cmpInfo.control("maximum-x", rangeInfo);
        cmpInfo.control("maximum-y", rangeInfo);
    }
    
    

    @Override
    protected JComponent createSwingComponent() {
        if (container == null) {
            createComponentAndAdaptors();
        }
        return container;
    }

    @Override
    protected void updateLabel() {
        super.updateLabel();
        updateBorders();
    }

    private void createComponentAndAdaptors() {
        container = Box.createHorizontalBox();
        controller = new JXYController();
        controller.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        controller.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        xAdaptor = new BoundedValueAdaptor(controller.getXRangeModel());
        yAdaptor = new BoundedValueAdaptor(controller.getYRangeModel());
        controller.addAncestorListener(new AncestorAdaptor());
        updateAdaptors();
        updateBorders();
        container.add(controller);
    }

    private void updateAdaptors() {
        if (xAdaptor != null && yAdaptor != null) {
            xAdaptor.setPreferredMinimum(xPrefs.minimum);
            xAdaptor.setPreferredMaximum(xPrefs.maximum);
//            xAdaptor.setPreferredScale(xPrefs.scale);
            yAdaptor.setPreferredMinimum(yPrefs.minimum);
            yAdaptor.setPreferredMaximum(yPrefs.maximum);
//            yAdaptor.setPreferredScale(yPrefs.scale);
        }
    }

    private void updateBorders() {
        var lbl = isLabelOnParent() ? "" : getLabel();
        if (container != null) {
            if (lbl.isEmpty()) {
                container.setBorder(Utils.getBorder());
            } else {
                container.setBorder(BorderFactory.createTitledBorder(
                        Utils.getBorder(), lbl));
            }
            container.revalidate();
        }
    }

    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        BindingContext ctxt = getLookup().find(BindingContext.class).orElse(null);
        if (bindingContext != ctxt) {
            if (bindingContext != null) {
                if (xBinding != null) {
                    bindingContext.unbind(xBinding, xAdaptor);
                }
                if (yBinding != null) {
                    bindingContext.unbind(yBinding, yAdaptor);
                }
            }
            if (ctxt != null) {
                if (xBinding != null) {
                    ctxt.bind(xBinding, xAdaptor);
                }
                if (yBinding != null) {
                    ctxt.bind(yBinding, yAdaptor);
                }
            }
            bindingContext = ctxt;
        }
    }

    private class AncestorAdaptor implements AncestorListener {

        @Override
        public void ancestorAdded(AncestorEvent event) {
            xAdaptor.setActive(true);
            yAdaptor.setActive(true);
        }

        @Override
        public void ancestorRemoved(AncestorEvent event) {
            xAdaptor.setActive(false);
            yAdaptor.setActive(false);
        }

        @Override
        public void ancestorMoved(AncestorEvent event) {
            // no op
        }
    }

    private class Preferences {

        PNumber minimum;
        PNumber maximum;
        PString scale;
    }


    private class XAddressBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) {
            if (xAdaptor == null) {
                createComponentAndAdaptors();
            }
            if (bindingContext != null) {
                if (xBinding != null) {
                    bindingContext.unbind(xBinding, xAdaptor);
                }
                if (value.isEmpty()) {
                    xBinding = null;
                } else {
                    try {
                        xBinding = ControlAddress.from(value).get();
                        bindingContext.bind(xBinding, xAdaptor);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Could not create binding-x", ex);
                        xBinding = null;
                    }
                }
            }

        }

        @Override
        public Value get() {
            return xBinding == null ? PString.EMPTY : xBinding;
        }
    }

    private class YAddressBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) {
            if (yAdaptor == null) {
                createComponentAndAdaptors();
            }
            if (bindingContext != null) {
                if (yBinding != null) {
                    bindingContext.unbind(yBinding, yAdaptor);
                }
                if (value.isEmpty()) {
                    yBinding = null;
                } else {
                    try {
                        yBinding = ControlAddress.coerce(value);
                        bindingContext.bind(yBinding, yAdaptor);
                    } catch (ValueFormatException ex) {
                        logger.log(Level.WARNING, "Could not create binding-y", ex);
                        yBinding = null;
                    }
                }
            }
        }

        @Override
        public Value get() {
            return yBinding == null ? PString.EMPTY : yBinding;
        }
    }

    private class MinBinding extends AbstractProperty {

        private final Preferences prefs;

        private MinBinding(Preferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public void set(long time, Value value) {
            if (value.isEmpty()) {
                prefs.minimum = null;
            } else {
                try {
                    prefs.minimum = PNumber.from(value).get();
                } catch (Exception ex) {
                    prefs.minimum = null;
                }
            }
            updateAdaptors();
        }

        @Override
        public Value get() {
            Value arg = prefs.minimum;
            if (arg == null) {
                return PString.EMPTY;
            } else {
                return arg;
            }
        }
    }

    private class MaxBinding extends AbstractProperty {

        private final Preferences prefs;

        private MaxBinding(Preferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public void set(long time, Value value) {
            if (value.isEmpty()) {
                prefs.maximum = null;
            } else {
                try {
                    prefs.maximum = PNumber.from(value).get();
                } catch (Exception ex) {
                    prefs.maximum = null;
                }
            }
            updateAdaptors();
        }

        @Override
        public Value get() {
            Value arg = prefs.maximum;
            if (arg == null) {
                return PString.EMPTY;
            } else {
                return arg;
            }
        }
    }

}
