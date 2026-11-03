/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Craig Ellenwood.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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

/** Line-framed script I/O for a process owned by an automation client. */
final class MachineTerminalIO extends AbstractRoot {

    private final InputStream source;
    private final PrintStream output;
    private Thread input;
    private ControlAddress scriptService;
    private Call pendingCall;
    private CompletableFuture<Void> pendingCompletion;

    MachineTerminalIO() {
        this(System.in, System.out);
    }

    MachineTerminalIO(InputStream source, PrintStream output) {
        this.source = source;
        this.output = output;
    }

    @Override
    protected void activating() {
        setRunning();
    }

    @Override
    protected void starting() {
        scriptService = getLookup().find(Services.class)
                .flatMap(services -> services.locate(ScriptService.class))
                .map(address -> ControlAddress.of(address, ScriptService.EVAL))
                .orElseThrow();
        output.println("PRAXIS_READY");
        output.flush();
        input = new Thread(this::readInput, "praxis-machine-input");
        input.setDaemon(true);
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
            router.route(call.error(PError.of("Unsupported operation")));
        } else if (pendingCall != null && call.matchID() == pendingCall.id()) {
            respond(call.isReply(), call.args().stream()
                    .map(Value::toString).collect(Collectors.joining(" ")));
            pendingCall = null;
            pendingCompletion.complete(null);
            pendingCompletion = null;
        }
    }

    private void respond(boolean ok, String text) {
        output.println((ok ? "PRAXIS_OK " : "PRAXIS_ERROR ")
                + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        output.flush();
    }

    private void submit(String script, CompletableFuture<Void> completion) {
        try {
            pendingCompletion = completion;
            pendingCall = Call.create(scriptService,
                    ControlAddress.of(getAddress(), "io"),
                    getExecutionContext().getTime(), PString.of(script));
            getRouter().route(pendingCall);
        } catch (RuntimeException ex) {
            pendingCall = null;
            pendingCompletion = null;
            respond(false, ex.toString());
            completion.complete(null);
        }
    }

    private void readInput() {
        var reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8));
        try {
            String line;
            while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                String script;
                try {
                    byte[] bytes = Base64.getDecoder().decode(line);
                    script = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
                } catch (IllegalArgumentException | CharacterCodingException ex) {
                    respond(false, "Invalid base64 UTF-8 script");
                    continue;
                }
                // Keep requests serial even if the caller writes several lines at once.
                var completion = new CompletableFuture<Void>();
                invokeLater(() -> submit(script, completion));
                completion.get();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException | ExecutionException ex) {
            System.getLogger(MachineTerminalIO.class.getName())
                    .log(System.Logger.Level.WARNING, "Machine input closed", ex);
        }
    }
}
