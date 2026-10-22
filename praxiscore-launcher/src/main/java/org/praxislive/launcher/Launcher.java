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
package org.praxislive.launcher;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.MainThread;
import org.praxislive.hub.Hub;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.core.Lookup;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.SystemManagerService;
import org.praxislive.core.services.UserInputService;
import org.praxislive.hub.net.NetworkCoreFactory;
import picocli.CommandLine;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * Main entry point for parsing command line arguments and launching a
 * {@link Hub}. This launcher uses {@link NetworkCoreFactory} to build a
 * distributed hub capable of proxying to other local or remote hubs. It can
 * also support a server to allow the built hub to be controlled externally.
 * <p>
 * This class is mainly intended to be called from the main method entry point.
 * The additional required context should provide support to launch another
 * instance of this process.
 * <p>
 * This launcher understands the following command line switches -
 * <ul>
 * <li>-f / --file {file} : a script file or project directory to run.</li>
 * <li>-p / --port {auto | 0 .. 65535} : launch a server on the specified port.
 * If 0 or auto, a port is automatically chosen. Unless --network is specified,
 * connections are only supported over local loopback. The port is reported to
 * standard out as "Listening at : [port]".</li>
 * <li>-n / --network {all | CIDR mask} : launch a server that supports remote
 * connections, from all addresses or matching mask. Implies --port auto if not
 * otherwise specified.</li>
 * <li>-i / --interactive : allow for controlling the hub via PCL commands over
 * the command line.</li>
 * <li>--child : configure the process to run as a child process. Implies --port
 * auto unless specified.</li>
 * </ul>
 * <p>
 * For other purposes, use the {@link Hub#builder()} directly to create and
 * launch a Hub.
 */
public class Launcher {

    static final String LISTENING_STATUS = "Listening at : ";

    private static final ResourceBundle MESSAGES
            = ResourceBundle.getBundle(Launcher.class.getPackageName() + ".Messages");

    /**
     * Main entry point.
     *
     * @param context a context implementation providing for launching a child
     * of this process
     * @param args the command line arguments, possibly amended
     */
    public static void main(Launcher.Context context, String[] args) {
        int ret = 0;
        Exec exec = new Exec(context);
        CommandLine cmd = new CommandLine(exec);
        cmd.setResourceBundle(context.resourceBundle());
        try {
            CommandLine.ParseResult parse = cmd.parseArgs(args);
            if (cmd.isUsageHelpRequested()) {
                cmd.usage(cmd.getOut());
                ret = cmd.getCommandSpec().exitCodeOnUsageHelp();
            } else if (cmd.isVersionHelpRequested()) {
                String versionOutput = exec.message("message.version", context.version());
                cmd.getOut().println(versionOutput);
                ret = cmd.getCommandSpec().exitCodeOnVersionHelp();
            } else {
                ret = exec.call();
            }
        } catch (CommandLine.ParameterException ex) {
            cmd.getErr().println(ex.getMessage());
            if (!UnmatchedArgumentException.printSuggestions(ex, cmd.getErr())) {
                ex.getCommandLine().usage(cmd.getErr());
            }
            ret = cmd.getCommandSpec().exitCodeOnInvalidInput();
        } catch (Exception ex) {
            ex.printStackTrace(cmd.getErr());
            ret = cmd.getCommandSpec().exitCodeOnExecutionException();
        }

        System.exit(ret);
    }

    static SocketAddress parseListeningLine(String line) {
        if (line.startsWith(LISTENING_STATUS)) {
            try {
                int port = Integer.parseInt(line.substring(LISTENING_STATUS.length()).trim());
                return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        throw new IllegalArgumentException();
    }

    private static String version() {
        String version = "DEV";

        try (InputStream pomProps = Launcher.class.getResourceAsStream(
                "/META-INF/maven/org.praxislive/praxiscore-launcher/pom.properties")) {
            if (pomProps != null) {
                Properties props = new Properties();
                props.load(pomProps);
                version = props.getProperty("version", version);
            }
        } catch (IOException ex) {
            // fall through
        }

        return version;
    }

    /**
     * Context for creating child processes and customizing launcher.
     */
    public static interface Context {

        /**
         * Create a {@link ProcessBuilder} to launch another instance of this
         * process.
         *
         * @param javaOptions additional JVM options to pass to process
         * @param arguments additional command line arguments for process
         * @return process builder
         */
        public ProcessBuilder createChildProcessBuilder(List<String> javaOptions,
                List<String> arguments);

        /**
         * Provide an optional file to be run on launch, eg. for embedding the
         * launcher in a project. If the context provides an auto-run file and
         * the file option is specified, an exception will be thrown on launch.
         * An implementation that doesn't want this behaviour should return an
         * empty optional if a file is specified.
         *
         * @return optional file to run on launch
         */
        @Deprecated(forRemoval = true)
        public default Optional<File> autoRunFile() {
            return Optional.empty();
        }

        /**
         * Provide an optional file to be run on launch, eg. for embedding the
         * launcher in a project. If the context provides an auto-run file and
         * the file option is specified, an exception will be thrown on launch.
         * An implementation that doesn't want this behaviour should return an
         * empty optional if a file is specified.
         *
         * @return optional file to run on launch
         */
        public default Optional<Path> autoRun() {
            return autoRunFile().map(File::toPath);
        }

        /**
         * A String representation of the version. The default value is
         * calculated from the version of the Launcher module.
         *
         * @return version string
         */
        public default String version() {
            return Launcher.version();
        }

        /**
         * A resource bundle to use for launcher messages, help, etc. The
         * default value provides all required messages. To override only a
         * subset, call the default method and use its value as the parent
         * resource bundle.
         *
         * @return resource bundle
         */
        public default ResourceBundle resourceBundle() {
            return MESSAGES;
        }

    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    private static class Exec implements Callable<Integer> {

        @CommandLine.Option(names = {"-d", "--cd"},
                descriptionKey = "option.cd.help")
        private Path cd;

        @CommandLine.Option(names = {"-x", "--exec"},
                descriptionKey = "option.exec.help")
        private String exec;

        @CommandLine.Option(names = {"-f", "--file"},
                descriptionKey = "option.file.help")
        private Path file;

        @CommandLine.Option(names = {"-i", "--interactive"},
                descriptionKey = "option.interactive.help")
        private Boolean interactive;

        @CommandLine.Option(names = {"-p", "--port"},
                converter = PortConverter.class,
                descriptionKey = "option.port.help")
        private Integer port;

        @CommandLine.Option(names = {"-n", "--network"},
                descriptionKey = "option.network.help")
        private String network;

        @CommandLine.Option(names = "--child",
                descriptionKey = "option.child.help")
        private boolean child;

        @CommandLine.Option(names = "--show-environment",
                descriptionKey = "option.environment.help")
        private boolean showEnv;

        @CommandLine.Option(names = "--no-signal-handlers",
                description = "Don't install signal handlers.",
                hidden = true)
        private boolean noSignals;

        @CommandLine.Option(names = "--no-autorun",
                description = "Don't run autorun script.",
                hidden = true)
        private boolean noAutorun;

        @CommandLine.Parameters(description = "Extra command line arguments.")
        private List<String> extraArgs;

        private final Launcher.Context context;

        private Exec(Launcher.Context context) {
            this.context = context;
        }

        @Override
        public Integer call() throws Exception {

            if (showEnv) {
                outputEnvironmentInfo();
            }

            if (child) {
                if (port == null) {
                    port = 0;
                }
                if (!noSignals) {
                    installChildSignalOverrides();
                }
                // assert script == null?
            }

            final boolean requireServer = network != null || port != null;
            if (requireServer && port == null) {
                port = 0;
            }
            final boolean allowRemote = requireServer && network != null;

            final String cidr;
            if (allowRemote && !network.equalsIgnoreCase("all")) {
                cidr = network;
            } else {
                cidr = null;
            }

            Path autorun = null;
            if (!child && !noAutorun) {
                autorun = context.autoRun().orElse(null);
            }

            if (file != null) {
                if (child) {
                    error(message("message.fileAndChild"));
                    return 1;
                }
                if (autorun != null) {
                    error(message("message.fileAndAutorun"));
                    return 1;
                }
            }

            final String postScript;
            if (cd != null || exec != null) {
                String ps = "\n";
                if (cd != null) {
                    ps += """
                          global {
                            cd %s
                          }
                          """.formatted(cd.toUri());
                }
                if (exec != null) {
                    ps += exec;
                }
                postScript = ps;
            } else {
                postScript = null;
            }

            final String script;
            Path scriptPath = file == null ? autorun : file;
            if (scriptPath != null) {
                scriptPath = scriptPath.toAbsolutePath().normalize();
                if (Files.isDirectory(scriptPath)) {
                    scriptPath = scriptPath.resolve("project.pxp");
                }
                if (!Files.exists(scriptPath)) {
                    error(message("message.noFile", scriptPath));
                    return 1;
                }
                try {
                    String body = Files.readString(scriptPath);
                    String preScript = """
                                global {
                                    constant _FILE %s
                                }
                                cd %s
                                """.formatted(
                            scriptPath.toUri(),
                            scriptPath.getParent().toUri()
                    );
                    if (postScript == null) {
                        script = preScript + body;
                    } else {
                        script = preScript + body + postScript;
                    }
                } catch (Exception ex) {
                    error(message("message.readFileError", scriptPath));
                    return 1;
                }
            } else {
                script = postScript;
            }

            final boolean terminal;
            if (interactive == null) {
                terminal = !(requireServer
                        || scriptPath != null
                        || (script == null && showEnv));
            } else {
                terminal = interactive;
            }

            if (!requireServer && !terminal && script == null) {
                if (showEnv) {
                    return 0;
                } else {
                    error(message("message.noTask"));
                    return 1;
                }
            }

            final MainThreadImpl main = new MainThreadImpl();

            int exitValue = 0;

            do {
                NetworkCoreFactory.Builder coreBuilder = NetworkCoreFactory.builder()
                        .childLauncher(new ChildLauncherImpl(context))
                        .exposeServices(List.of(
                                CodeCompilerService.class,
                                LogService.class,
                                SystemManagerService.class,
                                UserInputService.class
                        ));

                if (requireServer) {
                    coreBuilder.enableServer();
                    coreBuilder.serverPort(port);
                }

                if (allowRemote && cidr != null) {
                    coreBuilder.allowRemoteServerConnection(cidr);
                } else if (allowRemote) {
                    coreBuilder.allowRemoteServerConnection();
                }

                NetworkCoreFactory coreFactory = coreBuilder.build();

                Hub.Builder hubBuilder = Hub.builder()
                        .setCoreRootFactory(coreFactory)
                        .extendLookup(main);

                LogLevel logLevel = LogLevel.INFO;
                hubBuilder.extendLookup(logLevel);

                if (terminal) {
                    Root terminalIO = createTerminalIO();
                    hubBuilder.addExtension(terminalIO);
                    if (!(terminalIO instanceof RootHub.ServiceProvider rhsp)
                            || !rhsp.services().contains(LogService.class)) {
                        hubBuilder.addExtension(new LogServiceImpl(logLevel));
                    }
                } else {
                    hubBuilder.addExtension(new LogServiceImpl(logLevel));
                }

                Hub hub = hubBuilder.build();
                if (script != null) {
                    hub.eval(script);
                }
                hub.start();

                if (requireServer) {
                    NetworkCoreFactory.Info serverInfo
                            = coreFactory.awaitInfo(30, TimeUnit.SECONDS);
                    port = serverInfo.serverAddress()
                            .filter(a -> a instanceof InetSocketAddress)
                            .map(a -> (InetSocketAddress) a)
                            .orElseThrow().getPort();
                    out(LISTENING_STATUS + port);
                }
                main.run(hub);

                exitValue = hub.exitValue();

            } while (requireServer);

            return exitValue;
        }

        private void installChildSignalOverrides() {
            // override same as JLine terminal
            String[] signals = new String[]{"INT", "QUIT", "TSTP", "CONT", "INFO", "WINCH"};
            try {
                ServiceLoader.load(Signals.class)
                        .findFirst()
                        .ifPresent(s -> {
                            for (String signal : signals) {
                                s.register(signal, () -> {
                                    System.getLogger(Launcher.class.getName())
                                            .log(System.Logger.Level.DEBUG,
                                                    () -> "Received signal : " + signal);
                                });
                            }
                        });
            } catch (Exception ex) {
                System.getLogger(Launcher.class.getName())
                        .log(System.Logger.Level.ERROR,
                                "Error registering signal handler",
                                ex);
            }
        }

        private Root createTerminalIO() {
            try {
                return ServiceLoader.load(TerminalIOProvider.class)
                        .findFirst()
                        .map(p -> p.createTerminalIO(Lookup.EMPTY))
                        .orElseGet(FallbackTerminalIO::new);
            } catch (Exception ex) {
                System.getLogger(Launcher.class.getName())
                        .log(System.Logger.Level.ERROR,
                                "Error creating terminal IO, defaulting to fallback",
                                ex);
                return new FallbackTerminalIO();
            }
        }

        private String message(String key, Object... params) {
            String base = context.resourceBundle().getString(key);
            if (params.length == 0) {
                return base;
            } else {
                return MessageFormat.format(base, params);
            }
        }

        private void outputEnvironmentInfo() {
            try {
                ProcessHandle handle = ProcessHandle.current();
                handle.info().command().ifPresent(s
                        -> out("Command :\n" + s + "\n"));
                handle.info().arguments().ifPresent(args
                        -> out("Arguments :\n" + (Arrays.toString(args)) + "\n"));
                handle.info().commandLine().ifPresent(s
                        -> out("Full command line :\n" + s + "\n"));
                String modulePath = System.getProperty("jdk.module.path");
                out("Java module path :");
                out((modulePath == null || modulePath.isBlank()
                        ? "[EMPTY]" : modulePath));
                out("");
                String classPath = System.getProperty("java.class.path");
                out("Java class path :");
                out((classPath == null || classPath.isBlank()
                        ? "[EMPTY]" : classPath));
                out("");
                out("Environment variables :");
                out("" + System.getenv());
                out("");
            } catch (Exception e) {
                System.getLogger(Launcher.class.getName())
                        .log(System.Logger.Level.DEBUG,
                                "Exception thrown outputting environment info.", e);
            }
        }

        private void out(String msg) {
            System.out.println(msg);
        }

        private void error(String msg) {
            String ansiMsg = CommandLine.Help.Ansi.AUTO.string(
                    "@|bold,red " + msg + "|@"
            );
            System.out.println(ansiMsg);
        }

    }

    private static class PortConverter implements CommandLine.ITypeConverter<Integer> {

        @Override
        public Integer convert(String arg0) throws Exception {
            if (arg0.equalsIgnoreCase("auto")) {
                return 0;
            }
            int port = Integer.parseInt(arg0);
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port value out of range");
            }
            return port;
        }

    }

    private static class MainThreadImpl implements MainThread {

        private final Thread main;
        private final BlockingQueue<Runnable> queue;

        private MainThreadImpl() {
            this.main = Thread.currentThread();
            this.queue = new LinkedBlockingQueue<>();
        }

        @Override
        public void runLater(Runnable task) {
            queue.add(task);
        }

        @Override
        public boolean isMainThread() {
            return Thread.currentThread() == main;
        }

        private void run(Hub hub) {
            while (hub.isAlive()) {
                try {
                    Runnable task = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    }
                } catch (Throwable t) {
                    System.getLogger(Launcher.class.getName())
                            .log(System.Logger.Level.ERROR,
                                    "", t);
                }
            }

            drain:
            for (;;) {
                try {
                    Runnable task = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    } else {
                        break drain;
                    }
                } catch (Throwable t) {
                    System.getLogger(Launcher.class.getName())
                            .log(System.Logger.Level.ERROR,
                                    "", t);
                }
            }
        }

    }

}
