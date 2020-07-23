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
package org.praxislive.bin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.praxislive.launcher.Launcher;

public class Main {

    public static void main(String[] args) {
        Launcher.main(new LauncherCtxt(), args);
    }

    private static class LauncherCtxt implements Launcher.Context {

        private final String command;
        private final String modulePath;
        private final String classPath;

        private LauncherCtxt() {
            command = ProcessHandle.current().info().command().orElse("java");
            modulePath = System.getProperty("jdk.module.path");
            classPath = System.getProperty("java.class.path");
        }

        @Override
        public ProcessBuilder createChildProcessBuilder(List<String> javaOptions,
                List<String> arguments) {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.addAll(javaOptions);
            if (modulePath == null || modulePath.isEmpty()) {
                cmd.add("-classpath");
                cmd.add(classPath);
                cmd.add("org.praxislive.bin.Main");
            } else {
                cmd.add("-p");// -p %classpath -m org.praxislive.bin/org.praxislive.bin.Main
                cmd.add(modulePath);
                cmd.add("-m");
                cmd.add("org.praxislive.bin/org.praxislive.bin.Main");
            }
            cmd.addAll(arguments);
            return new ProcessBuilder(cmd);
        }

        @Override
        public Optional<File> autoRunFile() {
            var location = System.getProperty("app.home");
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
