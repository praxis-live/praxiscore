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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.praxislive.hub.net.ChildLauncher;

/**
 *
 */
class ChildLauncherImpl implements ChildLauncher {

    private final Launcher.Context context;

    public ChildLauncherImpl(Launcher.Context context) {
        this.context = context;
    }

    @Override
    public Info launch(List<String> javaOptions, List<String> arguments) throws Exception {
        var pb = context.createChildProcessBuilder(javaOptions, List.of("--child"));
        pb.redirectErrorStream(true);
        var process = pb.start();
        var in = process.getInputStream();
        var infoFuture = new CompletableFuture<Info>();
        process.onExit().thenRun(() -> infoFuture.cancel(true));
        startDaemon(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(Launcher.LISTENING_STATUS)) {
                        var address = Launcher.parseListeningLine(line);
                        infoFuture.complete(new Info(process, address));
                    }
                    System.out.println(line);
                }
            } catch (Exception ex) {
                infoFuture.completeExceptionally(ex);
                process.destroy();
            }
        });
        return infoFuture.get(30, TimeUnit.SECONDS);
    }

    private void startDaemon(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

}
