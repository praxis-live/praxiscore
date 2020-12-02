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

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.praxislive.base.AbstractRoot;
import javax.lang.model.SourceVersion;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.code.services.tools.ClassBodyCompiler;
import org.praxislive.code.ClassBodyContext;
import org.praxislive.code.LibraryResolver;
import org.praxislive.code.services.tools.MessageHandler;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBytes;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PResource;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.Services;

/**
 *
 */
public class DefaultCompilerService extends AbstractRoot
        implements RootHub.ServiceProvider {

    static final String EXT_CLASSPATH = "ext-classpath";

    private final Map<String, Control> controls;
    private final JavaCompiler compiler;
    private final Set<File> libJARs;
    private final List<LibraryResolver> libResolvers;
    private final List<LibraryResolver.Entry> libEntries;
    private SourceVersion release;

    public DefaultCompilerService() {

        controls = Map.of(
                CodeCompilerService.COMPILE, new CompileControl(),
                "add-libs", new AddLibsControl(),
                "release", new JavaReleaseControl());
        compiler = ToolProvider.getSystemJavaCompiler();
//                Lookup.SYSTEM.find(JavaCompilerProvider.class)
//                .map(JavaCompilerProvider::getJavaCompiler)
//                .orElse(ToolProvider.getSystemJavaCompiler());
        if (compiler == null) {
            throw new RuntimeException("No compiler found");
        }
        release = SourceVersion.RELEASE_11;
        libJARs = new LinkedHashSet<>();
        libResolvers = ServiceLoader.load(LibraryResolver.Provider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .map(LibraryResolver.Provider::createResolver)
                .collect(Collectors.toList());
        libEntries = new ArrayList<>();
    }

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(CodeCompilerService.class);
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        try {
            controls.get(call.to().controlID()).call(call, router);
        } catch (Exception ex) {
            router.route(call.error(PError.of(ex)));
        }
    }

    private class CompileControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                PMap map = PMap.from(call.args().get(0)).orElseThrow();
                PMap ret = process(map);
                router.route(call.reply(ret));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private PMap process(PMap map) throws Exception {
            String code = map.getString(CodeCompilerService.KEY_CODE, "");
            ClassBodyContext<?> cbc = getClassBodyContext(map);
            LogBuilder log = new LogBuilder(LogLevel.WARNING);
            Map<String, byte[]> classFiles
                    = ClassBodyCompiler.create(cbc)
                            .setCompiler(compiler)
                            .setRelease(release)
                            .addMessageHandler(new LogMessageHandler(log))
                            .extendClasspath(libJARs)
                            .compile(code);
            PMap classes = convertClasses(classFiles);
            PMap response = PMap.of(CodeCompilerService.KEY_CLASSES, classes,
                    CodeCompilerService.KEY_LOG, PArray.of(log.toList()),
                    EXT_CLASSPATH, convertClasspath());
            return response;
        }

        private ClassBodyContext<?> getClassBodyContext(PMap map) throws Exception {
            String cbcClass = map.getString(CodeCompilerService.KEY_CLASS_BODY_CONTEXT, null);
            return (ClassBodyContext<?>) Class.forName(cbcClass, true, Thread.currentThread().getContextClassLoader())
                    .getDeclaredConstructor()
                    .newInstance();
        }

        private LogLevel getLogLevel(PMap map) {
            String level = map.getString(CodeCompilerService.KEY_LOG_LEVEL, null);
            if (level != null) {
                return LogLevel.valueOf(level);
            } else {
                return LogLevel.ERROR;
            }
        }

        private PMap convertClasses(Map<String, byte[]> classes) {
            PMap.Builder bld = PMap.builder(classes.size());
            classes.entrySet().stream().forEach((type) -> {
                bld.put(type.getKey(), PBytes.valueOf(type.getValue()));
            });
            return bld.build();
        }

        private PArray convertClasspath() {
            return libJARs.stream()
                    .map(f -> PResource.of(f.toURI()))
                    .collect(PArray.collector());
        }

    }

    private class AddLibsControl implements Control, LibraryResolver.Context {

        private final LogBuilder log;

        private AddLibsControl() {
            this.log = new LogBuilder(LogLevel.INFO);
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                PArray libs = PArray.from(call.args().get(0)).orElseThrow();
                PArray ret = process(libs);
                if (!log.isEmpty()) {
                    getLookup().find(Services.class)
                            .flatMap(s -> s.locate(LogService.class))
                            .ifPresent(ad -> router.route(Call.createQuiet(
                                    ControlAddress.of(ad, LogService.LOG),
                                    call.to(), call.time(), log.toList()))
                    );
                    log.clear();
                }
                router.route(call.reply(ret));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private PArray process(PArray libs) throws Exception {
            for (var value : libs) {
                var resource = PResource.from(value)
                        .orElseThrow(IllegalArgumentException::new);
                var entry = resolve(resource);
                libEntries.add(entry);
                for (var path : entry.paths()) {
                    libJARs.add(path.toFile());
                }
            }
            return libJARs.stream()
                    .map(f -> PResource.of(f.toURI()))
                    .collect(PArray.collector());
        }

        private LibraryResolver.Entry resolve(PResource resource) throws Exception {
            for (var resolver : libResolvers) {
                var response = resolver.resolve(resource, this);
                if (response.isPresent()) {
                    var entry = response.get();
                    libEntries.add(entry);
                    return entry;
                }
            }
            return new LibraryResolver.Entry(resource, List.of(jarFile(resource).toPath()));
        }

        private File jarFile(PResource res) {
            List<URI> uris = res.resolve(getLookup());
            return uris.stream()
                    .filter(u -> "file".equals(u.getScheme()))
                    .map(File::new)
                    .filter(f -> f.exists() && f.getName().endsWith(".jar"))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid library : " + res));
        }

        @Override
        public List<LibraryResolver.Entry> resolved() {
            return List.copyOf(libEntries);
        }

        @Override
        public LogBuilder log() {
            return log;
        }

        @Override
        public Lookup getLookup() {
            return DefaultCompilerService.this.getLookup();
        }

    }

    private class JavaReleaseControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                int requestedRelease = PNumber.from(call.args().get(0))
                        .orElseThrow().toIntValue();
                process(requestedRelease);
                if (call.isReplyRequired()) {
                    router.route(call.reply(call.args()));
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private void process(int requestedRelease) throws Exception {
            if (requestedRelease <= release.ordinal()) {
                return;
            }
//            if (requestedRelease < release.ordinal()) {
//                throw new IllegalArgumentException("Cannot set release version lower than existing : " + release.ordinal());
//            }
            SourceVersion requested = compiler.getSourceVersions().stream()
                    .filter(v -> v.ordinal() == requestedRelease)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported release version : " + requestedRelease));
            release = requested;
        }

    }

    private static class LogMessageHandler implements MessageHandler {

        private final LogBuilder log;

        private LogMessageHandler(LogBuilder log) {
            this.log = log;
        }

        @Override
        public void handleError(String msg) {
            log.log(LogLevel.ERROR, msg);
        }

        @Override
        public void handleWarning(String msg) {
            log.log(LogLevel.WARNING, msg);
        }

    }

}
