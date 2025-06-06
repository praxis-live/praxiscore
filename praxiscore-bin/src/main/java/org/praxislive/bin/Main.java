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
package org.praxislive.bin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PString;
import org.praxislive.launcher.Launcher;

public class Main {

    public static void main(String[] args) {
        // @TODO replace with flag to disable hash checks when available
        if (System.getProperty("os.name", "").contains("Mac")) {
            System.setProperty("org.lwjgl.util.NoChecks", "true");
        }
        Launcher.main(new LauncherCtxt(), args);
    }

    private static class LauncherCtxt implements Launcher.Context {

        private LauncherCtxt() {
        }

        @Override
        public ProcessBuilder createChildProcessBuilder(List<String> javaOptions,
                List<String> arguments) {
            boolean isWindows = System.getProperty("os.name", "")
                    .toLowerCase(Locale.ROOT).contains("windows");
            String basedir = System.getProperty("app.home");
            if (basedir == null || basedir.isBlank()) {
                throw new IllegalStateException("Cannot find app.home");
            }
            Path bin = Path.of(basedir, "bin");
            Path launcher;
            try (Stream<Path> files = Files.list(bin)) {
                launcher = files.filter(f -> {
                    boolean isCmd = f.toString().endsWith(".cmd");
                    return isWindows ? isCmd : !isCmd && Files.isExecutable(f);
                }).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Cannot find launcher"));

            } catch (IOException ex) {
                throw new IllegalStateException("Cannot find launcher", ex);
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(launcher.toString());
            cmd.addAll(arguments);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Map<String, String> env = pb.environment();
            env.put("JAVA_HOME", System.getProperty("java.home"));
            env.put("JAVA_OPTS", javaOptions.stream()
                    .map(PString::of)
                    .collect(PArray.collector())
                    .toString());
            return pb;
        }

        @Override
        public Optional<File> autoRunFile() {
            String location = System.getProperty("app.home");
            if (location == null || location.isBlank()) {
                return Optional.empty();
            }
            try {
                var file = new File(location, "project.pxp");
                if (file.exists()) {
                    return Optional.of(file);
                }
            } catch (Exception ex) {
                System.getLogger(Main.class.getName()).log(System.Logger.Level.DEBUG,
                        "Error looking up file at " + location, ex);
            }
            return Optional.empty();
        }

    }

}
