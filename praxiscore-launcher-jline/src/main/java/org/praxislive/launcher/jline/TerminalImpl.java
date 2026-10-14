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
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.jline.prompt.ConfirmResult;
import org.jline.prompt.InputResult;
import org.jline.prompt.ListBuilder;
import org.jline.prompt.ListResult;
import org.jline.prompt.PromptBuilder;
import org.jline.prompt.PromptResult;
import org.jline.prompt.Prompter;
import org.jline.prompt.PrompterFactory;
import org.jline.reader.EOFError;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Value;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.UserInputService;
import org.praxislive.core.syntax.Tokenizer;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;
import org.praxislive.launcher.AbstractLogProcessor;

class TerminalImpl {

    static {
        System.setProperty("org.jline.reader.support.parsedline", "true");
        System.setProperty("org.jline.terminal.softwareSignals", "false");
    }

    private static final String PROMPT = "> ";
    private static final String CONTINUATION_PROMPT = "- ";
    private static final String EXIT_PROMPT = "Exit [Y/N] ? ";
    private static final String EXIT_NOTICE = "Shutting down ...";

    private static final System.Logger LOG = System.getLogger(JLineTerminalIO.class.getName());

    private static final TerminalImpl INSTANCE = new TerminalImpl();

    private final Queue<Call> requestQueue;
    private final AtomicReference<JLineTerminalIO> service;
    private final LogProcessor logProcessor;

    private Thread inputThread;
    private Terminal terminal;
    private LineReader reader;
    private Prompter prompter;
    private boolean dumbColor;

    private TerminalImpl() {
        service = new AtomicReference<>();
        requestQueue = new ConcurrentLinkedQueue<>();
        logProcessor = new LogProcessor();
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
                dumbColor = Terminal.TYPE_DUMB_COLOR.equals(terminal.getType());
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
                prompter = PrompterFactory.create(terminal);
                inputThread = new Thread(this::inputLoop, "TerminalIO");
                inputThread.start();
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.ERROR, "Unable to start terminal IO", ex);
            }
        }
    }

    synchronized void detach(JLineTerminalIO service) {
        this.service.compareAndSet(service, null);
    }

    synchronized void postLog(Call call) {
        if (reader != null) {
            logProcessor.processLog(call);
        }
    }

    synchronized void postRequest(Call call) {
        requestQueue.add(call);
        if (requestQueue.size() == 1 && reader != null && reader.isReading()) {
            inputThread.interrupt();
        }
    }

    synchronized void postResponse(Call call) {
        if (reader != null) {
            writeResponse(call);
        }
    }

    synchronized void showError(String msg) {
        if (reader != null) {
            reader.printAbove(new AttributedString("ERR : " + msg,
                    AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)));
        }
    }

    private void inputLoop() {

        String partial = "";

        while (true) {
            Call request = requestQueue.poll();
            if (request != null) {
                handleUserInputRequest(request);
                continue;
            }

            try {
                String script = reader.readLine(PROMPT, null, (Character) null, partial);
                if (script != null && !script.isBlank()) {
                    JLineTerminalIO root = service.get();
                    if (root != null) {
                        root.postScript(script);
                    } else {
                        showError("Not running");
                    }
                }
            } catch (UserInterruptException ex) {
                partial = ex.getPartialLine();
                if (requestQueue.isEmpty()) {
                    if (partial.isEmpty()) {
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
                    } else {
                        partial = "";
                    }
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.DEBUG, "Exception in input loop", ex);
            }
        }
    }

    private void handleUserInputRequest(Call request) {
        Call response;
        try {
            Value userInput = switch (request.to().controlID()) {
                case UserInputService.USER_INPUT ->
                    promptUserInput(request.args());
                case UserInputService.USER_INPUT_CONFIRM ->
                    promptUserInputConfirm(request.args());
                case UserInputService.USER_INPUT_MAP ->
                    promptUserInputMap(request.args());
                case UserInputService.USER_INPUT_SELECT ->
                    promptUserInputSelect(request.args());
                default ->
                    throw new UnsupportedOperationException();
            };
            response = request.reply(userInput);
        } catch (Exception ex) {
            response = request.error(PError.of(ex));
        }
        JLineTerminalIO root = service.get();
        if (root != null) {
            root.postInputResponse(response);
        }
    }

    private Value promptUserInput(List<Value> args) throws Exception {
        String msg = args.get(0).toString();
        String content = args.size() > 1 ? args.get(1).toString() : "";
        ArgumentInfo argInfo = args.size() > 2
                ? ArgumentInfo.from(args.get(2))
                        .orElseThrow(IllegalArgumentException::new)
                : Value.info();
        String mime = argInfo.attributes().getString(ArgumentInfo.KEY_MIME_TYPE, "");
        PromptBuilder builder = prompter.newBuilder();
        if (mime.isBlank()) {
            builder.createInputPrompt()
                    .name("input")
                    .message(msg)
                    .defaultValue(content.isEmpty() ? null : content)
                    .addPrompt();
        } else {
            String ext = switch (mime) {
                case "text/x-java", "text/x-praxis-java" ->
                    "java";
                default ->
                    "";
            };
            builder.createEditorPrompt()
                    .name("input")
                    .message(msg)
                    .title(msg)
                    .initialText(content)
                    .showLineNumbers(true)
                    .fileExtension(ext)
                    .addPrompt();
        }
        PromptResult<?> result = prompter
                .prompt(List.of(), builder.build())
                .get("input");
        return PString.of(result.getResult());
    }

    private PBoolean promptUserInputConfirm(List<Value> args) throws Exception {
        String msg = args.get(0).toString();
        PromptBuilder builder = prompter.newBuilder();
        builder.createConfirmPrompt()
                .name("input")
                .message(msg)
                .addPrompt();
        ConfirmResult result = (ConfirmResult) prompter
                .prompt(List.of(), builder.build())
                .get("input");
        return PBoolean.of(result.isConfirmed());
    }

    private PMap promptUserInputMap(List<Value> args) throws Exception {
        String msg = args.get(0).toString();
        PMap input = PMap.from(args.get(1)).orElseThrow(IllegalArgumentException::new);
        PromptBuilder builder = prompter.newBuilder();
        input.asMap().forEach((key, value) -> {
            builder.createInputPrompt()
                    .name(key)
                    .message(key)
                    .defaultValue(value.toString())
                    .addPrompt();
        });
        var results = prompter.prompt(List.of(new AttributedString(msg)), builder.build());
        PMap.Entry[] entries = input.keys().stream()
                .map(key -> {
                    String value = Optional.ofNullable(results.get(key))
                            .map(r -> ((InputResult) r).getInput())
                            .orElseGet(() -> input.getString(key, ""));
                    return PMap.entry(key, value);
                })
                .toArray(PMap.Entry[]::new);
        return PMap.ofEntries(entries);
    }

    private Value promptUserInputSelect(List<Value> args) throws Exception {
        String msg = args.get(0).toString();
        PArray values = PArray.from(args.get(1)).orElseThrow(IllegalArgumentException::new);
        PromptBuilder builder = prompter.newBuilder();
        ListBuilder list = builder.createListPrompt().name("input").message(msg);
        values.forEach(v -> list.add(v.toString(), v.toString()));
        list.addPrompt();
        ListResult result = (ListResult) prompter
                .prompt(List.of(), builder.build())
                .get("input");
        return PString.of(result.getSelectedId());
    }

    private synchronized void writeResponse(Call call) {
        boolean error = call.isError();
        String output = error
                ? errorResponseToOutput(call.args())
                : responseToOutput(call.args());
        boolean multiline = output.contains(System.lineSeparator());
        if (error) {
            output = "!!! : " + output;
        } else {
            if (multiline) {
                output = "---" + System.lineSeparator() + output;
            } else {
                output = "--- : " + output;
            }
        }
        AttributedStyle style = error
                ? AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)
                : AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
        if (dumbColor) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            output.lines().forEach(line -> sb.append(line, style)
                    .append(System.lineSeparator()));
            reader.printAbove(sb.toAttributedString());
        } else {
            reader.printAbove(new AttributedString(output, style));
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

    private String responseToOutput(List<Value> args) {
        return args.stream()
                .map(v -> v.print())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String errorResponseToOutput(List<Value> args) {
        return args.stream()
                .map(v -> PError.from(v)
                        .map(e -> e.message().isBlank()
                                ? e.errorType()
                                : e.errorType() + System.lineSeparator() + e.message())
                        .orElseGet(() -> v.print()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private class LogProcessor extends AbstractLogProcessor {

        AttributedStringBuilder sb;

        @Override
        protected void write(ComponentAddress src, LogLevel level, String text) {
            if (sb == null) {
                sb = new AttributedStringBuilder();
            }
            if (dumbColor) {
                // IDE output window treats bold as bright, and needs the
                // escapes on every output line
                AttributedStyle style = switch (level) {
                    case ERROR ->
                        AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
                    case WARNING ->
                        AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
                    default ->
                        AttributedStyle.DEFAULT.bold();
                };
                text.lines().forEach(line -> sb.append(line, style)
                        .append(System.lineSeparator()));
            } else {
                AttributedStyle style = switch (level) {
                    case ERROR ->
                        AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
                    case WARNING ->
                        AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
                    default ->
                        AttributedStyle.DEFAULT.faint();
                };
                sb.append(text, style);
            }

        }

        @Override
        protected void flush() {
            if (sb != null) {
                reader.printAbove(sb.toAttributedString());
                sb = null;
            }
        }

    }

    static TerminalImpl getInstance() {
        return INSTANCE;
    }

}
