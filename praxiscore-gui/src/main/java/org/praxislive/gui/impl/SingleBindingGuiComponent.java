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

import org.praxislive.base.AbstractProperty;
import org.praxislive.base.Binding;
import org.praxislive.base.BindingContext;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Value;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PString;

/**
 *
 */
public abstract class SingleBindingGuiComponent extends AbstractGuiComponent {
    
    private ControlAddress binding;
    private Binding.Adaptor adaptor;
    private BindingContext bindingContext;
    
    protected SingleBindingGuiComponent() {
    }
    
    @Override
    protected void initControls(Info.ComponentInfoBuilder cmpInfo) {
        super.initControls(cmpInfo);
        registerControl("binding", new AddressBinding());
        cmpInfo.control("binding", c -> c.property().input(ControlAddress.class)
                .property(ArgumentInfo.KEY_ALLOW_EMPTY, PBoolean.TRUE));
    }
    
    private class AddressBinding extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (adaptor == null) {
                adaptor = getBindingAdaptor();
            }
            if (bindingContext != null) {
                if (binding != null) {
                    bindingContext.unbind(binding, adaptor);
                }
                if (arg.isEmpty()) {
                    binding = null;
                } else {
                    try {
                        binding = ControlAddress.from(arg).orElseThrow();
                        bindingContext.bind(binding, adaptor);
                    } catch (Exception ex) {
                        binding = null;
                    }
                }
            }
        }

        @Override
        protected Value get() {
            return binding == null ? PString.EMPTY : binding;
        }
    }
    
    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        BindingContext ctxt = getLookup().find(BindingContext.class).orElse(null);
        if (bindingContext != ctxt) {
            if (bindingContext != null && binding != null) {
                bindingContext.unbind(binding, adaptor);
            }
            if (ctxt != null && binding != null) {
                ctxt.bind(binding, adaptor);
            }
            bindingContext = ctxt;
        }
    }
    
    protected abstract Binding.Adaptor getBindingAdaptor();
    
}
