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
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.impl.AbstractSingleCallFrame;

/**
 *
 */
public class CompilerCommandInstaller implements CommandInstaller {

    @Override
    public void install(Map<String, Command> commands) {
        commands.put("add-lib", (namespace, args) -> new AddLibs(namespace, args, false));
        commands.put("add-libs", (namespace, args) -> new AddLibs(namespace, args, true));
        commands.put("libraries", Libraries::new);
        commands.put("libraries-all", LibrariesAll::new);
        commands.put("libraries-path", LibrariesPath::new);
        commands.put("java-compiler-release", JavaRelease::new);
        commands.put("compiler", Compiler::new);
    }

    private static class AddLibs extends AbstractSingleCallFrame {

        private final boolean array;

        AddLibs(Namespace namespace, List<Value> args, boolean array) {
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


    private static class Libraries extends AbstractSingleCallFrame {

        private Libraries(Namespace namespace, List<Value> args) {
            super(namespace, args);
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            ComponentAddress service = env.getLookup().find(Services.class)
                    .flatMap(sm -> sm.locate(CodeCompilerService.class))
                    .orElseThrow(ServiceUnavailableException::new);
            if (args.isEmpty()) {
                ControlAddress librariesProperty = ControlAddress.of(service, "libraries");
                return Call.create(librariesProperty, env.getAddress(), env.getTime());
            } else {
                PArray libs = PArray.from(args.get(0))
                        .orElseThrow(IllegalArgumentException::new)
                        .stream()
                        .flatMap(lib -> expand(env, lib).stream())
                        .collect(PArray.collector());
                ControlAddress addLibsControl = ControlAddress.of(service, "add-libs");
                return Call.create(addLibsControl, env.getAddress(), env.getTime(), libs);
            }
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
    
    private static class LibrariesAll extends AbstractSingleCallFrame {

        private LibrariesAll(Namespace namespace, List<Value> args) {
            super(namespace, args);
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            ComponentAddress service = env.getLookup().find(Services.class)
                    .flatMap(sm -> sm.locate(CodeCompilerService.class))
                    .orElseThrow(ServiceUnavailableException::new);
            return Call.create(ControlAddress.of(service, "libraries-all"),
                    env.getAddress(), env.getTime());
        }
        
    }
    
    private static class LibrariesPath extends AbstractSingleCallFrame {

        private LibrariesPath(Namespace namespace, List<Value> args) {
            super(namespace, args);
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            ComponentAddress service = env.getLookup().find(Services.class)
                    .flatMap(sm -> sm.locate(CodeCompilerService.class))
                    .orElseThrow(ServiceUnavailableException::new);
            return Call.create(ControlAddress.of(service, "libraries-path"),
                    env.getAddress(), env.getTime());
        }
        
    }

    private static class Compiler extends AbstractSingleCallFrame {

        private Compiler(Namespace namespace, List<Value> args) {
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

    private static class JavaRelease extends AbstractSingleCallFrame {

        JavaRelease(Namespace namespace, List<Value> args) {
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
