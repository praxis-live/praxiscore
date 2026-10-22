/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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

import java.util.List;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PString;
import org.praxislive.core.services.SystemManagerService;
import org.praxislive.core.services.UserInputService;

import static org.praxislive.core.services.UserInputService.*;

/**
 *
 */
class JLineTerminalIO extends AbstractRoot implements RootHub.ServiceProvider {

    private ControlAddress scriptService;
    private ControlAddress fromAddress;

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(UserInputService.class, LogService.class);
    }

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
            switch (call.to().controlID()) {
                case LogService.LOG -> {
                    TerminalImpl.getInstance().postLog(call);
                    if (call.isReplyRequired()) {
                        router.route(call.reply());
                    }
                }
                case USER_INPUT, USER_INPUT_CONFIRM, USER_INPUT_MAP, USER_INPUT_SELECT ->
                    TerminalImpl.getInstance().postRequest(call);
                default ->
                    throw new UnsupportedOperationException();
            }
        } else if ("io".equals(call.to().controlID())) {
            TerminalImpl.getInstance().postResponse(call);
        }
    }

    void postExit() {
        invokeLater(this::handleExit);
    }

    void postInputResponse(Call call) {
        invokeLater(() -> handleInputResponse(call));
    }

    void postScript(String script) {
        invokeLater(() -> handleScript(script));
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

    private void handleInputResponse(Call call) {
        try {
            getRouter().route(call);
        } catch (Exception e) {
            TerminalImpl.getInstance().showError(e.toString());
        }
    }

    private void handleScript(String script) {
        try {
            getRouter().route(
                    Call.create(scriptService,
                            fromAddress,
                            getExecutionContext().getTime(),
                            PString.of(script)));
        } catch (Exception e) {
            TerminalImpl.getInstance().showError(e.toString());
        }
    }

}
