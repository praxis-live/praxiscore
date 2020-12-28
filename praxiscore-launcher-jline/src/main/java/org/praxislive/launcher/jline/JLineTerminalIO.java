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
package org.praxislive.launcher.jline;

import java.util.stream.Collectors;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Services;
import org.praxislive.core.syntax.Tokenizer;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PString;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jline.reader.EOFError;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.praxislive.core.services.SystemManagerService;

/**
 *
 */
class JLineTerminalIO extends AbstractRoot {

    static {
        System.setProperty("org.jline.reader.support.parsedline", "true");
    }

    private static final String PROMPT = "> ";
    private static final String CONTINUATION_PROMPT = "- ";
    private static final String EXIT_PROMPT = "Exit [Y/N] ? ";
    private static final String EXIT_NOTICE = "Shutting down ...";

    private static final System.Logger LOG = System.getLogger(JLineTerminalIO.class.getName());

    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();

    private Thread inputThread;
    private ControlAddress scriptService;
    private ControlAddress fromAddress;
    private Terminal terminal;
    private LineReader reader;

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
        try {
            terminal = TerminalBuilder
                    .builder()
                    .jna(true)
                    .build();
            reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser((line, cursor, context) -> {
                        if (isCompleteScript(line)) {
                            return new ArgumentCompleter.ArgumentLine(line, cursor);
                        } else {
                            throw new EOFError(cursor, cursor, line);
                        }
                    })
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, CONTINUATION_PROMPT)
                    .build();
            inputThread = new Thread(this::inputLoop);
            inputThread.start();
        } catch (IOException ex) {
            System.err.println("Unable to start terminal IO");
            setIdle();
        }
    }

    @Override
    protected void terminating() {
        if (inputThread != null) {
            inputThread.interrupt();
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

        try {
            responses.put(new Response(output, call.isError()));
        } catch (InterruptedException ex) {
            // should never happen
            LOG.log(System.Logger.Level.ERROR, "Queue threw error", ex);
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
            responses.add(new Response("" + e, true));
        }
    }

    private void handleExit() {
        getLookup().find(Services.class)
                .flatMap(s -> s.locate(SystemManagerService.class))
                .map(cmp -> ControlAddress.of(cmp, SystemManagerService.SYSTEM_EXIT))
                .ifPresentOrElse(exit -> {
                    getRouter().route(Call.create(exit, fromAddress,
                            getExecutionContext().getTime()));
                }, () -> System.exit(0));
    }

    private boolean isCompleteScript(String script) {
        try {
            var tok = new Tokenizer(script);
            for (var t : tok) {
                // consume to end
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void inputLoop() {
        while (getState() == State.ACTIVE_RUNNING) {
            try {
                var script = reader.readLine(PROMPT);
                if (script != null && !script.isBlank()) {
                    invokeLater(() -> handleScript(script));
                    var response = responses.poll(10, TimeUnit.SECONDS);
                    if (response == null) {
                        writeResponse(new Response("Timed out", true));
                    } else {
                        writeResponse(response);
                    }
                }
                // make sure response queue empty
                Response response;
                while ((response = responses.poll()) != null) {
                    writeResponse(response);
                }
            } catch (UserInterruptException ex) {
                if (ex.getPartialLine().isBlank()) {
                    try {
                        var confirm = reader.readLine(EXIT_PROMPT);
                        if ("y".equals(confirm.trim().toLowerCase(Locale.ROOT))) {
                            terminal.writer().println(EXIT_NOTICE);
                            terminal.flush();
                            invokeLater(this::handleExit);
                            Thread.sleep(5000);
                            // should have exited by now
                        }
                    } catch (UserInterruptException ex2) {
                        // continue
                    } catch (Exception ex2) {
                        LOG.log(System.Logger.Level.DEBUG, "Exception in exit question", ex);
                    }
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.DEBUG, "Exception in input loop", ex);

            }
        }
    }

    private void writeResponse(Response response) {
        if (response.error) {
            terminal.writer()
                    .println(new AttributedString("ERR : " + response.message,
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                            .toAnsi(terminal)
                    );
            terminal.flush();
        } else {
            terminal.writer()
                    .println(new AttributedString("--- : " + response.message,
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                            .toAnsi(terminal)
                    );
            terminal.flush();
        }
    }

    private static class Response {

        final String message;
        final boolean error;

        private Response(String message, boolean error) {
            this.message = message;
            this.error = error;
        }

    }

}
