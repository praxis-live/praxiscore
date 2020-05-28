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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.MainThread;
import org.praxislive.hub.Hub;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.hub.net.NetworkCoreFactory;
import picocli.CommandLine;

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

    /**
     * Main entry point.
     *
     * @param context a context implementation providing for launching a child
     * of this process
     * @param args the command line arguments, possibly amended
     */
    public static void main(Launcher.Context context, String[] args) {
        int ret = new CommandLine(new Exec(context)).execute(args);
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

    /**
     * Context for launching child processes.
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

    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    private static class Exec implements Callable<Integer> {

        @CommandLine.Option(names = {"-f", "--file"},
                description = "a script file or project directory to run.")
        private File file;

        @CommandLine.Option(names = {"-p", "--port"},
                converter = PortConverter.class,
                description = "{auto | 0 .. 65535} : launch a server on the specified port. " +
                    "If 0 or auto, a port is automatically chosen. " +
                    "Unless --network is specified, connections are only supported over local loopback. " +
                    "The port is reported to standard out as \"Listening at : [port]\".")
        private Integer port;

        @CommandLine.Option(names = {"-n", "--network"},
                description = "{all | CIDR mask} : launch a server that supports remote" +
                    "connections, from all addresses or matching mask. " +
                    "Implies --port auto if not otherwise specified.")
        private String network;

        @CommandLine.Option(names = {"-i", "--interactive"},
                description = {"allow for controlling the hub via PCL commands over the command line."})
        private boolean interactive;

        @CommandLine.Option(names = "--child",
                description = "Configure the process to run as a child process. " +
                    "Implies --port auto unless specified.")
        private boolean child;

        @CommandLine.Parameters(description = "Extra command line arguments.")
        private List<String> extraArgs;

        private final Launcher.Context context;

        private Exec(Launcher.Context context) {
            this.context = context;
        }

        @Override
        public Integer call() throws Exception {

            if (child) {
                if (port == null) {
                    port = 0;
                    // script == null?
                }
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

            final String script;
            if (file != null) {
                file = file.getAbsoluteFile();
                if (file.isDirectory()) {
                    file = new File(file, "project.pxp");
                }
                script = "set _PWD " + file.getParentFile().toURI() + "\n"
                        + Files.readString(file.toPath());
            } else {
                script = null;
            }

            if (!requireServer && !interactive && file == null) {
                return 1;
            }

            final var main = new MainThreadImpl();

            do {
                var coreBuilder = NetworkCoreFactory.builder()
                        .childLauncher(new ChildLauncherImpl(context))
                        .exposeServices(List.of(CodeCompilerService.class));

                if (requireServer) {
                    coreBuilder.enableServer();
                    coreBuilder.serverPort(port);
                }

                if (allowRemote && cidr != null) {
                    coreBuilder.allowRemoteServerConnection(cidr);
                } else if (allowRemote) {
                    coreBuilder.allowRemoteServerConnection();
                }

                var coreFactory = coreBuilder.build();

                var hubBuilder = Hub.builder()
                        .setCoreRootFactory(coreFactory)
                        .extendLookup(main);
                if (script != null) {
                    hubBuilder.addExtension(new ScriptRunner(List.of(script)));
                }
                if (interactive) {
                    hubBuilder.addExtension(new FallbackTerminalIO());
                }

                var hub = hubBuilder.build();
                hub.start();

                if (requireServer) {
                    var serverInfo = coreFactory.awaitInfo(30, TimeUnit.SECONDS);
                    port = serverInfo.serverAddress()
                            .filter(a -> a instanceof InetSocketAddress)
                            .map(a -> (InetSocketAddress) a)
                            .orElseThrow().getPort();
                    System.out.println(LISTENING_STATUS + port);
                }
                main.run(hub);

            } while (requireServer);

            return 0;
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
                    var task = queue.poll(500, TimeUnit.MILLISECONDS);
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
                    var task = queue.poll(500, TimeUnit.MILLISECONDS);
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
