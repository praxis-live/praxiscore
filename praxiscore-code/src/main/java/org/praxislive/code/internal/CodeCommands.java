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
package org.praxislive.code.internal;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.code.SharedCodeProtocol;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.types.PArray;
import org.praxislive.core.Value;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Env;
import org.praxislive.script.Namespace;
import org.praxislive.script.ScriptUtils;
import org.praxislive.script.StackFrame;
import org.praxislive.script.Variable;

/**
 * Pcl script commands for code support.
 */
public class CodeCommands implements CommandInstaller {

    static final ResourceBundle MESSAGES
            = ResourceBundle.getBundle(CodeCommands.class.getPackageName() + ".Messages");

    private static final Map<String, Command> COMMANDS = Map.ofEntries(
            Map.entry("add-lib", new AddLibs(false)),
            Map.entry("add-libs", new AddLibs(true)),
            Map.entry("compiler", new Compiler()),
            Map.entry("java-release", new JavaRelease()),
            Map.entry("libraries", new Libraries()),
            Map.entry("libraries-all",
                    new LibrariesQuery(CodeCompilerService.LIBRARIES_ALL)),
            Map.entry("libraries-system",
                    new LibrariesQuery(CodeCompilerService.LIBRARIES_SYSTEM)),
            Map.entry("libraries-path",
                    new LibrariesQuery(CodeCompilerService.LIBRARIES_PATH)),
            Map.entry(SharedCodeProtocol.SHARED_CODE,
                    new SharedCode(SharedCodeProtocol.SHARED_CODE)),
            Map.entry(SharedCodeProtocol.SHARED_CODE_ADD,
                    new SharedCode(SharedCodeProtocol.SHARED_CODE_ADD)),
            Map.entry(SharedCodeProtocol.SHARED_CODE_MERGE,
                    new SharedCode(SharedCodeProtocol.SHARED_CODE_MERGE)),
            Map.entry("sources", new Sources()),
            Map.entry("sources-write", new SourcesWrite())
    );

    @Override
    public void install(Map<String, Command> commands) {
        commands.putAll(COMMANDS);
    }

    private static class AddLibs implements Command {

        private final boolean array;

        AddLibs(boolean array) {
            this.array = array;
        }

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            PArray libs = array ? PArray.from(args.get(0)).orElseThrow(IllegalArgumentException::new)
                    : PArray.of((Value) args.get(0));
            return StackFrame.serviceCall(CodeCompilerService.class, CodeCompilerService.LIBRARIES, libs);
        }

    }

    private static class Libraries implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.isEmpty()) {
                return StackFrame.serviceCall(CodeCompilerService.class,
                        CodeCompilerService.LIBRARIES, List.of());
            } else {
                PArray libs = PArray.from(args.get(0))
                        .orElseThrow(IllegalArgumentException::new)
                        .stream()
                        .flatMap(lib -> expand(namespace, lib).stream())
                        .collect(PArray.collector());
                return StackFrame.serviceCall(CodeCompilerService.class,
                        CodeCompilerService.LIBRARIES, libs);
            }
        }

        private List<PResource> expand(Namespace namespace, Value lib) {
            return PResource.from(lib)
                    .map(List::of)
                    .orElseGet(() -> listFiles(namespace, lib));
        }

        private List<PResource> listFiles(Namespace namespace, Value lib) {
            try {
                return ScriptUtils.listFiles(namespace, lib.toString()).stream()
                        .map(PResource::of)
                        .toList();
            } catch (Exception ex) {
                return List.of();
            }
        }

        @Override
        public String description() {
            return MESSAGES.getString("libraries.description");
        }

    }

    private static class LibrariesQuery implements Command {

        private final String control;

        private LibrariesQuery(String control) {
            this.control = control;
        }

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            return StackFrame.serviceCall(CodeCompilerService.class, control, List.of());
        }

        @Override
        public String description() {
            return switch (control) {
                case "libraries-all" ->
                    MESSAGES.getString("libraries-all.description");
                case "libraries-system" ->
                    MESSAGES.getString("libraries-system.description");
                case "libraries-path" ->
                    MESSAGES.getString("libraries-path.description");
                default ->
                    "";
            };
        }

    }

    private static class Compiler implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            return StackFrame.serviceCall(CodeCompilerService.class,
                    CodeCompilerService.OPTIONS, args);
        }

        @Override
        public String description() {
            return MESSAGES.getString("compiler.description");
        }

    }

    private static class JavaRelease implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            return StackFrame.log(LogLevel.WARNING,
                    MESSAGES.getString("java-release.deprecation"))
                    .andThen(v -> StackFrame.serviceCall(CodeCompilerService.class,
                            CodeCompilerService.OPTIONS, PMap.of("release", args.get(0))));
        }

    }

    private static class SharedCode implements Command {

        private final String control;

        SharedCode(String control) {
            this.control = control;
        }

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            ComponentAddress context = Optional.ofNullable(
                    namespace.getVariable(Env.CONTEXT))
                    .map(Variable::getValue)
                    .flatMap(ComponentAddress::from)
                    .filter(ad -> !ad.rootID().startsWith("_"))
                    .orElseThrow(() -> new IllegalStateException("No context available"));
            ComponentAddress root = ComponentAddress.of("/" + context.rootID());
            return StackFrame.call(ControlAddress.of(root, control), args);
        }

        @Override
        public String description() {
            return switch (control) {
                case SharedCodeProtocol.SHARED_CODE ->
                    MESSAGES.getString("shared-code.description");
                case SharedCodeProtocol.SHARED_CODE_ADD ->
                    MESSAGES.getString("shared-code-add.description");
                case SharedCodeProtocol.SHARED_CODE_MERGE ->
                    MESSAGES.getString("shared-code-merge.description");
                default ->
                    "";
            };
        }

    }

    private static class Sources implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }
            URI path = ScriptUtils.resolvePath(namespace, args.get(0).toString());
            return StackFrame.async(() -> SourcesSupport.readSources(path));
        }

        @Override
        public String description() {
            return MESSAGES.getString("sources.description");
        }

    }

    private static class SourcesWrite implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 2) {
                throw new IllegalArgumentException("Incorrect number of arguments");
            }
            PMap sources = PMap.from(args.get(0)).orElseThrow(IllegalArgumentException::new);
            URI path = ScriptUtils.resolvePath(namespace, args.get(1).toString());
            return StackFrame.async(() -> {
                List<String> warnings = SourcesSupport.writeSources(sources, path);
                if (warnings.isEmpty()) {
                    return PString.EMPTY;
                } else {
                    return PString.of(warnings.stream().collect(Collectors.joining("\n")));
                }
            }).andThen(v -> {
                PString msg = PString.of(v.get(0));
                if (!msg.isEmpty()) {
                    return StackFrame.log(LogLevel.WARNING, msg);
                } else {
                    return StackFrame.empty();
                }
            });

        }

        @Override
        public String description() {
            return MESSAGES.getString("sources-write.description");
        }

    }

}
