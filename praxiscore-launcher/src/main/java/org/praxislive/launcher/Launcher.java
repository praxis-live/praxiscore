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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.core.MainThread;
import org.praxislive.core.Root;
import org.praxislive.hub.Hub;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.hub.net.NetworkCoreFactory;

/**
 *
 */
public class Launcher {
    
    static final String LISTENING_STATUS = "Listening at : ";
    
    private final Launcher.Context context;
    private final String[] args;
    
    private Launcher(Context context, String[] args) {
        this.context = context;
        this.args = args;
    }
    
    private void launch() {
        System.out.println("PraxisCORE");
        System.out.println(Arrays.toString(args));
        
        if (args.length >= 1 && args[0].equals("--child")) {
            processChild(args);
        } else {
            processFile(args);
        }
    }
    
    public static void main(Launcher.Context context, String[] args) {
        new Launcher(context, args).launch();
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
    
    private void processFile(String[] args) {
        try {
            NetworkCoreFactory sf = NetworkCoreFactory.builder()
                    .childLauncher(new ChildLauncherImpl(context))
                    .exposeServices(List.of(CodeCompilerService.class))
                    .build();
            List<Root> exts = new ArrayList<>();
            if (args.length == 1 && !args[0].startsWith("-")) {
                File file = new File(args[0]);
                if (!file.isAbsolute()) {
                    file = file.getAbsoluteFile();
                }
                if (file.isDirectory()) {
                    file = new File(file, "project.pxp");
                }
                String script = Files.readString(file.toPath());
                script = "set _PWD " + file.getParentFile().toURI() + "\n" + script;
                exts.add(new NonGuiPlayer(List.of(script)));
            }
            exts.add(new TerminalIO());
            var builder = Hub.builder();
            builder.setCoreRootFactory(sf);
            exts.forEach(builder::addExtension);
            var main = new MainThreadImpl();
            builder.extendLookup(main);
            var hub = builder.build();
            hub.start();
            main.run(hub);
        } catch (Exception ex) {
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }
    
    private void processChild(String[] args) {
        int port = 0;
        try {
            var main = new MainThreadImpl();
            while (true) {
                NetworkCoreFactory sf = NetworkCoreFactory.builder()
                        .enableServer()
                        .serverPort(port)
                        .childLauncher(new ChildLauncherImpl(context))
                        .exposeServices(List.of(CodeCompilerService.class))
                        .build();
                List<Root> exts = new ArrayList<>();
                exts.add(new TerminalIO());
                var builder = Hub.builder();
                builder.setCoreRootFactory(sf);
                exts.forEach(builder::addExtension);
                builder.extendLookup(main);
                var hub = builder.build();
                hub.start();
                var serverInfo = sf.awaitInfo(30, TimeUnit.SECONDS);
                port = serverInfo.serverAddress()
                        .filter(a -> a instanceof InetSocketAddress)
                        .map(a -> (InetSocketAddress) a)
                        .orElseThrow().getPort();
                System.out.println(LISTENING_STATUS + port);
                main.run(hub);
            }
            
        } catch (Exception ex) {
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }
    
    public static interface Context {
        
        public ProcessBuilder createChildProcessBuilder(List<String> javaOptions,
                List<String> arguments);
        
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

//
//    private void processScript(Env env, String[] args) throws CommandException {
//        if (args.length < 1) {
//            throw new CommandException(1, "Too many script files specified on command line.");
//        }
//        String script;
//        try {
//            script = loadScript(env, args[0]);
//        } catch (Exception ex) {
//            LOG.log(Level.SEVERE, "Error loading script file", ex);
//            throw new CommandException(1, "Error loading script file.");
//        }
//        try {
//            Hub hub = Hub.builder()
//                    .addExtension(new NonGuiPlayer(Collections.singletonList(script)))
//                    .build();
//            hub.start();
//            hub.await();
//        } catch (Exception ex) {
//            throw new CommandException(1, "Error starting hub");
//        }
//    }
//
//    private String loadScript(Env env, String filename) throws IOException {
//
//        LOG.log(Level.FINE, "File : {0}", filename);
//        File f = new File(filename);
//        if (!f.isAbsolute()) {
//            f = new File(env.getCurrentDirectory(), filename);
//        }
//        LOG.log(Level.FINE, "java.io.File : {0}", f);
//        FileObject target = FileUtil.toFileObject(f);
//        if (target == null) {
//            LOG.log(Level.FINE, "Can't find script file");
//            throw new IOException("Can't find script file");
//        }
//        if (target.isFolder()) {
//            target = target.getFileObject(PROJECT_PXP);
//            if (target == null) {
//                throw new IOException("No project file found in target folder");
//            }
//        }
//        LOG.log(Level.FINE, "Loading script : {0}", target);
//        String script = target.asText();
//        script = "set _PWD " + FileUtil.toFile(target.getParent()).toURI() + "\n" + script;
//        return script;
//    }

