/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
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
package org.praxislive.launcher.jline;

import java.util.stream.Collectors;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PString;
import org.praxislive.core.services.SystemManagerService;

/**
 *
 */
class JLineTerminalIO extends AbstractRoot {

    private ControlAddress scriptService;
    private ControlAddress fromAddress;

    @Override
    protected void activating() {
        setRunning();
    }

    @Override
    protected void starting() {
        scriptService = getLookup().find(Services.class)
                .flatMap(srvs -> srvs.locate(ScriptService.class))
                .map(cmp -> ControlAddress.of(cmp, ScriptService.EVAL))
                .orElseThrow();
        fromAddress = ControlAddress.of(getAddress(), "io");
        TerminalImpl.getInstance().attach(this);
    }

    @Override
    protected void terminating() {
        TerminalImpl.getInstance().detach(this);
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        if (call.isRequest()) {
            router.route(call.error(PError.of("Unsupported Operation")));
            return;
        }
        String output = call.args().stream()
                .map(Value::toString)
                .collect(Collectors.joining(" "));

        TerminalImpl.getInstance().postResponse(new Response(output, call.isError()));
    }

    void postScript(String script) {
        invokeLater(() -> handleScript(script));
    }

    private void handleScript(String script) {
        try {
            getRouter().route(
                    Call.create(scriptService,
                            fromAddress,
                            getExecutionContext().getTime(),
                            PString.of(script)));
        } catch (Exception e) {
            TerminalImpl.getInstance().postResponse(new Response("" + e, true));
        }
    }

    void postExit() {
        invokeLater(this::handleExit);
    }

    private void handleExit() {
        getLookup().find(Services.class)
                .flatMap(s -> s.locate(SystemManagerService.class))
                .map(cmp -> ControlAddress.of(cmp, SystemManagerService.SYSTEM_EXIT))
                .ifPresentOrElse(exit -> {
                    getRouter().route(Call.createQuiet(exit, fromAddress,
                            getExecutionContext().getTime()));
                }, () -> System.exit(0));
    }

}
