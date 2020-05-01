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

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.Binding.Adaptor;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.ExecutionContext;
import org.praxislive.core.Info;
import org.praxislive.core.Value;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PString;
import org.praxislive.gui.impl.ActionAdaptor;
import org.praxislive.gui.impl.SingleBindingGuiComponent;

/**
 *
 */
public class Button extends SingleBindingGuiComponent {

    private final static Logger LOG = Logger.getLogger(Button.class.getName());

    private JButton button;
    private ActionAdaptor adaptor;
    private List<Value> values;
    private OnClickProperty onClick;
    
    public Button() {
        values = List.of();
    }

    @Override
    protected void initControls(Info.ComponentInfoBuilder cmpInfo) {
        super.initControls(cmpInfo);
        registerControl("values", new ValuesBinding());
        cmpInfo.control("values", c -> c.property().input(PArray.class));
        onClick = new OnClickProperty();
        registerControl("on-click", new OnClickProperty());
        cmpInfo.control("on-click", c -> c.property()
                .input(i -> i.string().mime("text/x-praxis-script")));
        registerControl("_on-click-log", new OnClickLog());
    }

    @Override
    protected void updateLabel() {
        super.updateLabel();
        button.setText(getLabel());
    }

    @Override
    protected JComponent createSwingComponent() {
        if (button == null) {
            createComponentAndAdaptor();
        }
        return button;
    }

    @Override
    protected Adaptor getBindingAdaptor() {
        if (adaptor == null) {
            createComponentAndAdaptor();
        }
        return adaptor;
    }

    private void createComponentAndAdaptor() {
        button = new JButton();
        button.setUI(new UI());
        adaptor = new ActionAdaptor();
        button.addActionListener(adaptor);
        adaptor.setCallArguments(values);
        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                processOnClick();
            }
        });
        button.addAncestorListener(new AncestorListener() {

            public void ancestorAdded(AncestorEvent event) {
                adaptor.setActive(true);
            }

            public void ancestorRemoved(AncestorEvent event) {
                adaptor.setActive(false);
            }

            public void ancestorMoved(AncestorEvent event) {
                // no op
            }
        });
    }

    private void processOnClick() {
        try {
            String script = onClick.get().toString().trim();
            if (script.isEmpty()) {
                return;
            }
            ControlAddress to = ControlAddress.of(
                    findService(ScriptService.class),
                    ScriptService.EVAL);
            ControlAddress from = ControlAddress.of(
                    getAddress(), "_on-click-log");
            Call call = Call.createQuiet(to, from,
                    getLookup().find(ExecutionContext.class).get().getTime(),
                    PString.of(script));
            getLookup().find(PacketRouter.class).get().route(call);
        } catch (Exception ex) {
            Logger.getLogger(Button.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    private class ValuesBinding extends AbstractProperty {

        private PArray value = PArray.EMPTY;

        @Override
        public void set(long time, Value value) {
            var arr = PArray.from(value).orElseThrow(IllegalArgumentException::new);
            if (arr.isEmpty()) {
                values = List.of();
            } else {
                values = arr.stream().collect(Collectors.toList());
            }
            this.value = arr;
            adaptor.setCallArguments(values);
        }

        @Override
        public Value get() {
            return this.value;
        }
    }
    
    private class OnClickProperty extends AbstractProperty {
        
        private PString value = PString.EMPTY;

        @Override
        protected void set(long time, Value arg) throws Exception {
            this.value = PString.of(arg);
        }

        @Override
        protected Value get() {
            return value;
        }
        
    }

    private class OnClickLog implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isError()) {
                LOG.warning(call.toString());
            }

        }
    
    }

    private static class UI extends BasicButtonUI {

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
            g.fillRect(0, 0, b.getWidth(), b.getHeight());
        }

        @Override
        protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
            Color fg = b.getForeground();
            ButtonModel model = b.getModel();
            FontMetrics fm = g.getFontMetrics();
            int mnemonicIndex = b.getDisplayedMnemonicIndex();
            if (model.isPressed() || model.isSelected()) {
                g.setColor(b.getBackground());
            } else if (!model.isRollover()) {
                g.setColor(Utils.mix(b.getBackground(), fg, 0.8));
            } else {
                g.setColor(b.getForeground());
            }
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex,
                    textRect.x + getTextShiftOffset(),
                    textRect.y + fm.getAscent() + getTextShiftOffset());
        }

    }

}
