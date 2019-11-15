/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2019 Neil C Smith.
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

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.hub.Hub;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 *
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("PraxisCORE");
        System.out.println(Arrays.toString(args));
        
        if (args.length == 1) {
            try {
                File file = new File(args[0]);
                if (!file.isAbsolute()) {
                    file = file.getAbsoluteFile();
                }
                if (file.isDirectory()) {
                    file = new File(file, "project.pxp");
                }
                String script = Files.readString(file.toPath());
                script = "set _PWD " + file.getParentFile().toURI() + "\n" + script;
                Hub hub = Hub.builder()
                        .addExtension(new NonGuiPlayer(List.of(script)))
                        .build();
                hub.start();
                hub.await();
            } catch (Exception ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(1);
            }
        } else {
            System.exit(1);
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
}
