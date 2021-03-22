/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2021 Neil C Smith.
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
package org.praxislive.data;

import org.praxislive.base.AbstractRootContainer;
import org.praxislive.base.BindingContextControl;
import org.praxislive.code.SharedCodeProperty;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.Services;

/**
 *
 *
 */
public class DataRoot extends AbstractRootContainer {

    private final static ComponentInfo INFO;

    static {
        INFO = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .merge(StartableProtocol.API_INFO)
                .control("shared-code", SharedCodeProperty.INFO)
        );
    }

    private final SharedCodeProperty sharedCode;

    private BindingContextControl bindings;
    private Lookup lookup;

    public DataRoot() {
        sharedCode = new SharedCodeProperty(this, this::handleLog);
        registerControl("shared-code", sharedCode);
    }

    @Override
    protected void activating() {
        lookup = null;
        bindings = new BindingContextControl(ControlAddress.of(getAddress(), "_bindings"),
                getExecutionContext(),
                getRouter());
        registerControl("_bindings", bindings);
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            if (bindings != null) {
                lookup = Lookup.of(super.getLookup(), bindings, sharedCode.getSharedCodeContext());
            } else {
                lookup = Lookup.of(super.getLookup(), sharedCode.getSharedCodeContext());
            }
        }
        return lookup;
    }

    @Override
    public ComponentInfo getInfo() {
        return INFO;
    }

    private void handleLog(LogBuilder log) {
        if (log.isEmpty()) {
            return;
        }
        getLookup().find(Services.class)
                .flatMap(srv -> srv.locate(LogService.class))
                .ifPresent(logger -> {
                    var to = ControlAddress.of(logger, LogService.LOG);
                    var from = ControlAddress.of(getAddress(), "_log");
                    var call = Call.createQuiet(to,
                            from,
                            getExecutionContext().getTime(),
                            log.toList());
                    getRouter().route(call);
                });
    }

}
