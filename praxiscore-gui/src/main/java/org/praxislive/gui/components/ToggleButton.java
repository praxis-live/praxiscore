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
package org.praxislive.gui.components;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.Binding.Adaptor;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Info;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.types.PBoolean;
import org.praxislive.gui.impl.SingleBindingGuiComponent;
import org.praxislive.gui.impl.ToggleButtonModelAdaptor;

/**
 *
 *
 */
public class ToggleButton extends SingleBindingGuiComponent {

    private JToggleButton button;
    private Value onArg;
    private Value offArg;
    private ToggleButtonModelAdaptor adaptor;

    public ToggleButton() {
        onArg = PBoolean.TRUE;
        offArg = PBoolean.FALSE;
    }

    @Override
    protected void initControls(Info.ComponentInfoBuilder cmpInfo) {
        super.initControls(cmpInfo);
        registerControl("on-value", new OnBinding());
        cmpInfo.control("on-value", c -> c.property().input(Value.class).defaultValue(PBoolean.TRUE));
        registerControl("off-value", new OffBinding());
        cmpInfo.control("off-value", c -> c.property().input(Value.class).defaultValue(PBoolean.FALSE));
        cmpInfo.property(ComponentInfo.KEY_COMPONENT_TYPE, ComponentType.of("gui:togglebutton"));
    }

    @Override
    public void write(TreeWriter writer) {
        super.write(writer);
        if (!PBoolean.TRUE.equivalent(onArg)) {
            writer.writeProperty("on-value", onArg);
        }
        if (!PBoolean.FALSE.equivalent(offArg)) {
            writer.writeProperty("off-value", offArg);
        }
    }

    @Override
    protected void updateLabel() {
        super.updateLabel();
        button.setText(getLabel());
    }

    @Override
    protected Adaptor getBindingAdaptor() {
        if (adaptor == null) {
            createComponentAndAdaptor();
        }
        return adaptor;
    }

    @Override
    protected JComponent createSwingComponent() {
        if (button == null) {
            createComponentAndAdaptor();
        }
        return button;
    }

    private void createComponentAndAdaptor() {
        button = new JToggleButton(getLabel());
        button.setUI(new UI());
        adaptor = new ToggleButtonModelAdaptor(button.getModel());
        setAdaptorArguments();
        button.addAncestorListener(new AncestorListener() {

            @Override
            public void ancestorAdded(AncestorEvent event) {
                adaptor.setActive(true);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                adaptor.setActive(false);
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
                // no op
            }
        });
    }

    private void setAdaptorArguments() {
        if (adaptor != null) {
            adaptor.setOnArgument(onArg);
            adaptor.setOffArgument(offArg);
        }
    }

    private class OnBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) {
            onArg = value;
            setAdaptorArguments();
        }

        @Override
        public Value get() {
            return onArg;
        }

    }

    private class OffBinding extends AbstractProperty {

        @Override
        public void set(long time, Value value) {
            offArg = value;
            setAdaptorArguments();
        }

        @Override
        public Value get() {
            return offArg;
        }

    }

    private static class UI extends BasicToggleButtonUI {

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setRolloverEnabled(true);
            b.setBorder(new EmptyBorder(8, 8, 8, 8));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            g.setColor(b.hasFocus() || b.getModel().isRollover()
                    ? Utils.mix(c.getBackground(), c.getForeground(), 0.8)
                    : Utils.mix(c.getBackground(), c.getForeground(), 0.6));

            g.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
            super.paint(g, c);
        }

        @Override
        protected void paintButtonPressed(Graphics g, AbstractButton b) {
            g.setColor(b.getForeground());
            g.fillRect(4, 4, b.getWidth() - 8, b.getHeight() - 8);
        }

        @Override
        protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
            ButtonModel model = b.getModel();
            FontMetrics fm = g.getFontMetrics();
            int mnemonicIndex = b.getDisplayedMnemonicIndex();
            if (model.isPressed() || model.isSelected()) {
                g.setColor(b.getBackground());
            } else if (!model.isRollover()) {
                g.setColor(Utils.mix(b.getBackground(), b.getForeground(), 0.8));
            } else {
                g.setColor(b.getForeground());
            }
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex,
                    textRect.x + getTextShiftOffset(),
                    textRect.y + fm.getAscent() + getTextShiftOffset());

        }
    }

}
