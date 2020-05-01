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
 */
package org.praxislive.gui.components;

import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import net.miginfocom.swing.MigLayout;
import org.praxislive.base.Binding;
import org.praxislive.base.Binding.Adaptor;
import org.praxislive.core.Value;
import org.praxislive.core.types.PString;
import org.praxislive.gui.impl.SingleBindingGuiComponent;

/**
 *
 */
public class TextArea extends SingleBindingGuiComponent {

    private JPanel panel;
    private JTextArea text;
    private TextAdaptor adaptor;
    private boolean activeEdit;
    private Action sendAction;
    private Action resetAction;
    private boolean syncing;

    @Override
    protected Adaptor getBindingAdaptor() {
        if (adaptor == null) {
            createComponentAndAdaptor();
        }
        return adaptor;
    }

    @Override
    protected JComponent createSwingComponent() {
        if (panel == null) {
            createComponentAndAdaptor();
        }
        return panel;
    }

    private void createComponentAndAdaptor() {
        panel = new JPanel(new MigLayout("fill", "[grow]", "[grow][nogrid]"));
        text = new JTextArea();
        text.getDocument().addDocumentListener(new TextListener());
        JScrollPane sp = new JScrollPane(text,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        panel.add(sp, "grow, wrap, width 450, height 300");
        adaptor = new TextAdaptor();
        text.addAncestorListener(adaptor);
        sendAction = new SendAction();
        sendAction.setEnabled(false);
        panel.add(new JButton(sendAction), "tag ok");
        resetAction = new ResetAction();
        resetAction.setEnabled(false);
        panel.add(new JButton(resetAction), "tag cancel");

        Keymap map = JTextComponent.addKeymap("PraxisTextArea", text.getKeymap());

        map.addActionForKeyStroke(KeyStroke.getKeyStroke("control ENTER"), sendAction);
        map.addActionForKeyStroke(KeyStroke.getKeyStroke("ESCAPE"), resetAction);

        text.setKeymap(map);
    }

    private void setActiveEdit(boolean active) {
        if (active == activeEdit) {
            return;
        }
        sendAction.setEnabled(active);
        resetAction.setEnabled(active);
        activeEdit = active;
    }

    private class TextAdaptor extends Binding.Adaptor implements AncestorListener {

        private TextAdaptor() {
            setSyncRate(Binding.SyncRate.Low);
        }

        private void send(String text) {
            PString val = PString.of(text);
            send(List.of(val));
        }

        @Override
        public void update() {
            if (activeEdit || text.isFocusOwner()) {
                return;
            }
            sync();
        }

        private void sync() {
            syncing = true;
            Binding binding = getBinding();
            if (binding == null) {
                text.setText("");
            } else {
                List<Value> args = binding.getValues();
                if (args.size() < 1) {
                    text.setText("");
                } else {
                    text.setText(args.get(0).toString());
                }
            }
            syncing = false;
        }

        @Override
        protected void onError(List<Value> args) {
            JOptionPane.showMessageDialog(panel, args, "Error", JOptionPane.ERROR_MESSAGE);
        }

        @Override
        public void updateBindingConfiguration() {
            // no op
        }

        @Override
        public void ancestorAdded(AncestorEvent event) {
            setActive(true);
        }

        @Override
        public void ancestorRemoved(AncestorEvent event) {
            setActive(false);
        }

        @Override
        public void ancestorMoved(AncestorEvent event) {
            // no op
        }
    }

    private class TextListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            changed();
        }
        
        private void changed() {
            if (!syncing) {
                setActiveEdit(true);
            }
        }

    }

    private class SendAction extends AbstractAction {

        private SendAction() {
            super("Send");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            adaptor.send(text.getText());
            setActiveEdit(false);
        }
    }

    private class ResetAction extends AbstractAction {

        private ResetAction() {
            super("Reset");
        }

        @Override
        public void actionPerformed(ActionEvent e) {           
            setActiveEdit(false);
            adaptor.sync();
        }
    }
}
