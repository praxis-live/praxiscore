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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.jline.reader.EOFError;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.praxislive.core.syntax.Tokenizer;

class TerminalImpl {

    static {
        System.setProperty("org.jline.reader.support.parsedline", "true");
    }

    private static final String PROMPT = "> ";
    private static final String CONTINUATION_PROMPT = "- ";
    private static final String EXIT_PROMPT = "Exit [Y/N] ? ";
    private static final String EXIT_NOTICE = "Shutting down ...";

    private static final System.Logger LOG = System.getLogger(JLineTerminalIO.class.getName());

    private static final TerminalImpl INSTANCE = new TerminalImpl();

    private final AtomicReference<JLineTerminalIO> service;

    private Thread inputThread;
    private Terminal terminal;
    private LineReader reader;

    private TerminalImpl() {
        service = new AtomicReference<>();
    }

    synchronized void attach(JLineTerminalIO service) {
        this.service.set(service);
        if (terminal == null) {
            try {
                terminal = TerminalBuilder
                        .builder()
                        .jni(true)
                        .dumb(true)
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
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.ERROR, "Unable to start terminal IO", ex);
            }
        }
    }

    synchronized void detach(JLineTerminalIO service) {
        this.service.compareAndSet(service, null);
    }

    synchronized void postResponse(Response response) {
        if (reader != null) {
            writeResponse(response);
        }
    }

    private void inputLoop() {

        while (true) {
            try {
                String script = reader.readLine(PROMPT);
                if (script != null && !script.isBlank()) {
                    JLineTerminalIO root = service.get();
                    if (root != null) {
                        root.postScript(script);
                    } else {
                        writeResponse(new Response("Not running", true));
                    }
                }
            } catch (UserInterruptException ex) {
                if (ex.getPartialLine().isBlank()) {
                    try {
                        String confirm = reader.readLine(EXIT_PROMPT);
                        if ("y".equals(confirm.trim().toLowerCase(Locale.ROOT))) {
                            terminal.writer().println(EXIT_NOTICE);
                            terminal.flush();
                            JLineTerminalIO root = service.get();
                            if (root != null) {
                                root.postExit();
                                Thread.sleep(5000);
                                // should have exited by now - fall through
                            }
                            System.exit(0);
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

    private synchronized void writeResponse(Response response) {
        if (response.error()) {
            reader.printAbove(new AttributedString("ERR : " + response.message(),
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                            .toAnsi(terminal)
                    );
        } else {
            reader.printAbove(new AttributedString("--- : " + response.message(),
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                            .toAnsi(terminal)
                    );
        }
    }

    private boolean isCompleteScript(String script) {
        try {
            Tokenizer.parse(script);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static TerminalImpl getInstance() {
        return INSTANCE;
    }

}
