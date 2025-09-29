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
package org.praxislive.script.commands;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;

/**
 *
 */
class FileCmds {

    private static final Command FILE = new FileCmd();
    private static final Command FILE_LIST = new FileListCmd();
    private static final Command FILE_NAMES = new FileNamesCmd();
    private static final Command CD = new CdCmd();
    private static final Command PWD = new PwdCmd();
    private static final Command LOAD = new LoadCmd();

    private FileCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.put("file", FILE);
        commands.put("file-list", FILE_LIST);
        commands.put("file-names", FILE_NAMES);
        commands.put("ls", FILE_NAMES);
        commands.put("cd", CD);
        commands.put("pwd", PWD);
        commands.put("load", LOAD);
    }

    static URI getPWD(Namespace namespace) {
        return Optional.ofNullable(namespace.getVariable(Env.PWD))
                .flatMap(v -> PResource.from(v.getValue()))
                .map(PResource::value)
                .orElse(new File("").toURI());
    }

    private static URI resolve(Namespace namespace, String path) throws URISyntaxException {
        if (path.contains(":")) {
            try {
                URI uri = new URI(path);
                if (uri.isAbsolute()) {
                    return uri;
                }
            } catch (URISyntaxException ex) {
                // fall through?
            }
        }
        URI base = getPWD(namespace);
        URI uri = base.resolve(new URI(null, null, path, null));
        if ("file".equals(base.getScheme())) {
            uri = new File(uri).toURI();
        }
        return uri;
    }

    private static List<Path> listFiles(Namespace namespace) throws Exception {
        return listFiles(namespace, "", "*");
    }

    private static List<Path> listFiles(Namespace namespace, String globPath) throws Exception {
        int lastSlash = globPath.lastIndexOf("/");
        String path = "";
        if (lastSlash > 0) {
            path = globPath.substring(0, lastSlash);
        }
        String glob = "*";
        if (lastSlash < (globPath.length() - 1)) {
            glob = globPath.substring(lastSlash + 1);
        }
        return listFiles(namespace, path, glob);
    }

    private static List<Path> listFiles(Namespace namespace, String path, String glob) throws Exception {
        URI base = path.isEmpty() ? getPWD(namespace) : resolve(namespace, path);
        try {
            List<Path> list = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(base), glob)) {
                for (Path entry : stream) {
                    list.add(entry);
                }
            }
            list.sort(Comparator.naturalOrder());
            return list;
        } catch (IOException ex) {
            return Collections.EMPTY_LIST;
        }
    }

    private static class FileCmd implements InlineCommand {

        @Override
        public List<Value> process(Env env, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new Exception();
            }
            try {
                return List.of(PResource.of(
                        resolve(namespace, args.get(0).toString())
                ));
            } catch (Exception ex) {
                throw new Exception(ex);
            }
        }
    }

    private static class FileListCmd implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() > 1) {
                throw new Exception();
            }
            try {
                List<Path> list;
                if (args.size() == 1) {
                    list = listFiles(namespace, args.get(0).toString());
                } else {
                    list = listFiles(namespace);
                }

                List<PResource> ret = list.stream()
                        .map(path -> PResource.of(path.toUri()))
                        .collect(Collectors.toList());

                return List.of(PArray.of(ret));
            } catch (Exception ex) {
                throw new Exception(ex);
            }

        }

    }

    private static class FileNamesCmd implements InlineCommand {

        @Override
        public List<Value> process(Env env, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() > 1) {
                throw new Exception();
            }
            try {
                List<Path> list;
                /*if (args.getSize() == 2) {
                    list = listFiles(namespace, args.get(0).toString(), args.get(1).toString());
                } else*/
                if (args.size() == 1) {
                    list = listFiles(namespace, args.get(0).toString());
                } else {
                    list = listFiles(namespace);
                }

                List<PString> ret = list.stream()
                        .map(path -> PString.of(path.getFileName()))
                        .collect(Collectors.toList());

                return List.of(PArray.of(ret));
            } catch (Exception ex) {
                throw new Exception(ex);
            }
        }

    }

    private static class CdCmd implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new Exception();
            }
            try {
                URI uri = resolve(namespace, args.get(0).toString());
                if ("file".equals(uri.getScheme())) {
                    File d = new File(uri);
                    if (!d.isDirectory()) {
                        throw new Exception("Not a valid directory");
                    }
                }
                PResource dir = PResource.of(uri);
                namespace.getOrCreateVariable(Env.PWD, dir).setValue(dir);
                return List.of(dir);
            } catch (URISyntaxException ex) {
                throw new Exception(ex);
            }
        }

    }

    private static class PwdCmd implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            return List.of(PResource.of(getPWD(namespace)));
        }

    }

    private static class LoadCmd implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Wrong number of arguments");
            }
            Path path = PResource.from(args.get(0))
                    .map(PResource::value)
                    .map(Path::of)
                    .orElseThrow(IllegalArgumentException::new);
            return StackFrame.async(() -> PString.of(Files.readString(path)));
        }

    }

}
