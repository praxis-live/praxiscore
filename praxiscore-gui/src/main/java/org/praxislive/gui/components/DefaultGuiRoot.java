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
 */
package org.praxislive.gui.components;

import java.awt.Dimension;
import java.awt.EventQueue;
import org.praxislive.core.Lookup;
import java.awt.LayoutManager;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import net.miginfocom.swing.MigLayout;
import org.praxislive.base.AbstractRootContainer;
import org.praxislive.base.BindingContextControl;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.gui.Keys;
import org.praxislive.gui.GuiContext;
import com.formdev.flatlaf.FlatDarkLaf;
import org.praxislive.core.ComponentType;

/**
 *
 */
// IMPORTANT: Fixes and changes to behaviour of this class should be propagated
//            to DockableGuiRoot in praxis.live.pxr.gui
public class DefaultGuiRoot extends AbstractRootContainer {

    private final static ComponentInfo INFO;

    static {
        INFO = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .merge(StartableProtocol.API_INFO)
                .property(ComponentInfo.KEY_COMPONENT_TYPE, ComponentType.of("root:gui"))
        );
    }

    private static boolean lafConfigured = false;

    private JFrame frame;
    private JPanel container;
    private MigLayout layout;
    private LayoutChangeListener layoutListener;
    private BindingContextControl bindings;
    private Context context;
    private Lookup lookup;

    @Override
    public Lookup getLookup() {
        return lookup == null ? super.getLookup() : lookup;
    }

    @Override
    protected void activating() {
        var delegate = new SwingDelegate();
        attachDelegate(delegate);
        delegate.start();
    }

    @Override
    protected void starting() {
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    protected void stopping() {
        frame.setVisible(false);
    }

    private void setup() {
        if (!lafConfigured) {
            setupLookAndFeel();
            lafConfigured = true;
        }
        frame = new JFrame();
        frame.setTitle("PRAXIS : " + getAddress());
        frame.setMinimumSize(new Dimension(150, 50));
        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                setIdle();
            }
        });
        frame.getContentPane().setLayout(new MigLayout("fill", "[fill, grow]"));
        layout = new MigLayout("fill", "[fill]");
        container = new JPanel(layout);
        container.addContainerListener(new ChildrenListener());
        layoutListener = new LayoutChangeListener();
        frame.getContentPane().add(new JScrollPane(container), "grow, push");

        bindings = new BindingContextControl(ControlAddress.of(getAddress(), "_bindings"),
                getExecutionContext(),
                getRouter());
        registerControl("_bindings", bindings);

        context = new Context();

        lookup = Lookup.of(super.getLookup(), bindings, context);

    }

    private void setupLookAndFeel() {
        FlatDarkLaf.install();
    }

    private void dispose() {
        frame.setVisible(false);
        frame.dispose();
    }

    @Override
    public ComponentInfo getInfo() {
        return INFO;
    }

    private class SwingDelegate extends Delegate {

        private Timer timer;

        private void start() {
            EventQueue.invokeLater(() -> {
                setup();
                timer = new Timer(50, e -> update());
                timer.start();
            });
        }

        private void update() {
            boolean ok = doUpdate(getRootHub().getClock().getTime());
            if (!ok) {
                timer.stop();
                dispose();
                detachDelegate(this);
            }
        }

    }

    private class Context extends GuiContext {

        @Override
        public JComponent getContainer() {
            return container;
        }
    }

    private void setLayoutConstraint(JComponent child) {
        layout.setComponentConstraints(child, child.getClientProperty(Keys.LayoutConstraint));
        container.revalidate();
        container.repaint();
    }

    private class ChildrenListener implements ContainerListener {

        @Override
        public void componentAdded(ContainerEvent e) {
            if (e.getChild() instanceof JComponent) {
                JComponent child = (JComponent) e.getChild();
                child.addPropertyChangeListener(
                        Keys.LayoutConstraint, layoutListener);
                setLayoutConstraint(child);
            }
        }

        @Override
        public void componentRemoved(ContainerEvent e) {
            if (e.getChild() instanceof JComponent) {
                ((JComponent) e.getChild()).removePropertyChangeListener(
                        Keys.LayoutConstraint, layoutListener);
            }
        }

    }

    private class LayoutChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getSource() instanceof JComponent) {
                JComponent comp = (JComponent) evt.getSource();
                LayoutManager lm = container.getLayout();
                if (lm instanceof MigLayout) {
                    ((MigLayout) lm).setComponentConstraints(comp, evt.getNewValue());
                    container.revalidate();
                }
            }
        }
    }

}
