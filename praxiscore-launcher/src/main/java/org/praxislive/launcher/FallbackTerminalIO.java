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
package org.praxislive.launcher;

import java.util.stream.Collectors;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Services;
import org.praxislive.core.syntax.Token;
import org.praxislive.core.syntax.Tokenizer;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PString;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 */
class FallbackTerminalIO extends AbstractRoot {

    private Thread input;
    private String script;
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
        input = new Thread(this::inputLoop);
        script = "";
        input.start();
    }

    @Override
    protected void terminating() {
        if (input != null) {
            input.interrupt();
        }
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

        if (call.isReply()) {
            System.out.println("--- : " + output);
        } else {
            System.err.println("ERR : " + output);
        }

    }

    private void processInput(String in) {
        if ("clear".equals(in)) {
            script = "";
            return;
        }
        if (!script.isEmpty()) {
            script = script + "\n" + in;
        } else {
            script = in;
        }
        Tokenizer tok = new Tokenizer(script);
        try {
            for (Token t : tok) {
            }
            Call exec = Call.create(scriptService,
                    fromAddress,
                    getExecutionContext().getTime(),
                    PString.of(script)
            );
            getRouter().route(exec);
            script = "";
        } catch (Exception ex) {
            // let script build up
        }

    }

    private void inputLoop() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (!Thread.interrupted()) {
            try {
                String in;
                in = reader.readLine();
                invokeLater(() -> processInput(in));
            } catch (IOException ex) {

            }

        }
    }
}
