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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.logging.Logger;
import org.praxislive.base.Binding;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Value;

/**
 *
 */
public class ActionAdaptor extends Binding.Adaptor implements ActionListener {

    private static Logger logger = Logger.getLogger(ActionAdaptor.class.getName());

    private List<Value> args;
    private boolean isProperty;

    public ActionAdaptor() {
        setSyncRate(Binding.SyncRate.None);
        this.args = List.of();
    }

    public void setCallArguments(List<Value> args) {
        this.args = List.copyOf(args);
    }

    public List<Value> getCallArguments() {
        return args;
    }

    @Override
    public void update() {
        // no op - nothing to sync
    }

    @Override
    public void updateBindingConfiguration() {
        isProperty = false;
        Binding binding = getBinding();
        if (binding != null) {
             isProperty = binding.getControlInfo()
                    .map(ControlInfo::isProperty)
                    .orElse(Boolean.FALSE);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isProperty && args.isEmpty()) {
            logger.warning("Can't send zero length arguments to property control");
        } else {
            send(args);
        }
    }

}
