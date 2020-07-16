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
package org.praxislive.code.services;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PArray;
import org.praxislive.core.Value;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PResource;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Env;
import org.praxislive.script.ExecutionException;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;
import org.praxislive.script.impl.AbstractSingleCallFrame;

/**
 *
 */
public class CompilerCommandInstaller implements CommandInstaller {

    private static final AddLibsCmd ADD_LIB = new AddLibsCmd(false);
    private static final AddLibsCmd ADD_LIBS = new AddLibsCmd(true);
    private static final LibrariesCmd LIBRARIES = new LibrariesCmd();
    private static final JavaReleaseCmd RELEASE = new JavaReleaseCmd();
    private static final CompilerCmd COMPILER = new CompilerCmd();

    @Override
    public void install(Map<String, Command> commands) {
        commands.put("add-lib", ADD_LIB);
        commands.put("add-libs", ADD_LIBS);
        commands.put("libraries", LIBRARIES);
        commands.put("java-compiler-release", RELEASE);
        commands.put("compiler", COMPILER);
    }

    private static class AddLibsCmd implements Command {

        private final boolean array;

        AddLibsCmd(boolean array) {
            this.array = array;
        }

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws ExecutionException {
            return new AddLibsStackFrame(namespace, args, array);
        }

    }

    private static class AddLibsStackFrame extends AbstractSingleCallFrame {

        private final boolean array;

        AddLibsStackFrame(Namespace namespace, List<Value> args, boolean array) {
            super(namespace, args);
            this.array = array;
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            PArray libs = array ? PArray.from(args.get(0)).orElseThrow(IllegalArgumentException::new)
                    : PArray.of((Value) args.get(0));
            ComponentAddress service = env.getLookup().find(Services.class)
                    .flatMap(sm -> sm.locate(CodeCompilerService.class))
                    .orElseThrow(ServiceUnavailableException::new);
            ControlAddress addLibsControl = ControlAddress.of(service, "add-libs");
            return Call.create(addLibsControl, env.getAddress(), env.getTime(), libs);
        }

    }

    private static class LibrariesCmd implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws ExecutionException {
            return new LibrariesStackFrame(namespace, args);
        }

    }

    private static class LibrariesStackFrame extends AbstractSingleCallFrame {

        private LibrariesStackFrame(Namespace namespace, List<Value> args) {
            super(namespace, args);
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            PArray libs = PArray.from(args.get(0))
                    .orElseThrow(IllegalArgumentException::new)
                    .stream()
                    .flatMap(lib -> expand(env, lib).stream())
                    .collect(PArray.collector());
            ComponentAddress service = env.getLookup().find(Services.class)
                    .flatMap(sm -> sm.locate(CodeCompilerService.class))
                    .orElseThrow(ServiceUnavailableException::new);
            ControlAddress addLibsControl = ControlAddress.of(service, "add-libs");
            return Call.create(addLibsControl, env.getAddress(), env.getTime(), libs);
        }

        private List<PResource> expand(Env context, Value lib) {
            return PResource.from(lib)
                    .map(List::of)
                    .orElseGet(() -> listFiles(context, lib));
        }

        private List<PResource> listFiles(Env context, Value lib) {
            try {
                var response = ((InlineCommand) getNamespace().getCommand("file-list"))
                        .process(context, getNamespace(), List.of(lib)).get(0);
                return PArray.from(response).orElseThrow().stream()
                        .map(v -> PResource.from(v).orElseThrow())
                        .collect(Collectors.toList());
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }

    }

    private static class CompilerCmd implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws ExecutionException {
            return new CompilerStackFrame(namespace, args);
        }

    }

    private static class CompilerStackFrame extends AbstractSingleCallFrame {

        private CompilerStackFrame(Namespace namespace, List<Value> args) {
            super(namespace, args);
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            PMap params = PMap.from(args.get(0)).orElseThrow(IllegalArgumentException::new);
            ComponentAddress service = env.getLookup().find(Services.class)
                    .flatMap(sm -> sm.locate(CodeCompilerService.class))
                    .orElseThrow(ServiceUnavailableException::new);
            ControlAddress releaseControl = ControlAddress.of(service, "release");
            return Call.create(releaseControl, env.getAddress(), env.getTime(), params.get("release"));
        }

    }

    private static class JavaReleaseCmd implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws ExecutionException {
            return new JavaReleaseStackFrame(namespace, args);
        }

    }

    private static class JavaReleaseStackFrame extends AbstractSingleCallFrame {

        JavaReleaseStackFrame(Namespace namespace, List<Value> args) {
            super(namespace, args);
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            ComponentAddress service = env.getLookup().find(Services.class)
                    .flatMap(sm -> sm.locate(CodeCompilerService.class))
                    .orElseThrow(ServiceUnavailableException::new);
            ControlAddress releaseControl = ControlAddress.of(service, "release");
            return Call.create(releaseControl, env.getAddress(), env.getTime(), args);
        }

    }

}
