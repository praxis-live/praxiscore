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
package org.praxislive.code.services;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.base.AbstractRoot;
import javax.lang.model.SourceVersion;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.code.LibraryResolver;
import org.praxislive.code.services.tools.CompilerTask;
import org.praxislive.code.services.tools.MessageHandler;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
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

    private static final ComponentInfo INFO;

    static {
        INFO = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(CodeCompilerService.API_INFO)
                .control("libraries", c -> c.readOnlyProperty().output(PArray.class))
                .control("libraries-all", c -> c.readOnlyProperty().output(PArray.class))
                .control("libraries-path", c -> c.readOnlyProperty().output(PArray.class))
        );
    }

    private final Map<String, Control> controls;
    private final JavaCompiler compiler;
    private final Set<PResource> libResolved;
    private final Set<PResource> libProvided;
    private final Set<PResource> libSystem;
    private final Set<Path> libFiles;
    private final List<LibraryResolver> libResolvers;
    private final String defClasspath;
    private final String defModulepath;

    private SourceVersion release;
    private PArray libs;
    private PArray libsAll;
    private PArray libPath;
    private PArray libsSys;

    public DefaultCompilerService() {

        controls = Map.of(
                CodeCompilerService.COMPILE, new CompileControl(),
                "add-libs", new AddLibsControl(),
                "release", new JavaReleaseControl(),
                "libraries", (call, router) -> {
                    if (call.isRequest()) {
                        router.route(call.reply(libs));
                    }
                },
                "libraries-all", (call, router) -> {
                    if (call.isRequest()) {
                        router.route(call.reply(libsAll));
                    }
                },
                "libraries-system", (call, router) -> {
                    if (call.isRequest()) {
                        router.route(call.reply(libsSys));
                    }
                },
                "libraries-path", (call, router) -> {
                    if (call.isRequest()) {
                        router.route(call.reply(libPath));
                    }
                },
                ComponentProtocol.INFO, (call, router) -> {
                    if (call.isRequest()) {
                        router.route(call.reply(INFO));
                    }
                }
        );
        compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("No compiler found");
        }
        release = SourceVersion.RELEASE_11;
        libFiles = new LinkedHashSet<>();
        libResolved = new LinkedHashSet<>();
        libProvided = new LinkedHashSet<>();
        libResolvers = ServiceLoader.load(LibraryResolver.Provider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .map(LibraryResolver.Provider::createResolver)
                .collect(Collectors.toList());
        libs = PArray.EMPTY;
        libsAll = PArray.EMPTY;
        libPath = PArray.EMPTY;
        var systemProvided = ServiceLoader.load(LibraryResolver.SystemInfo.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .flatMap(LibraryResolver.SystemInfo::provided)
                .collect(Collectors.toList());
        libSystem = new LinkedHashSet<>(systemProvided);
        libsSys = PArray.of(libSystem);
        this.defClasspath = System.getProperty("java.class.path", "");
        this.defModulepath = System.getProperty("jdk.module.path", "");
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
                PMap ret;
                ret = process(map);
                router.route(call.reply(ret));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private PMap process(PMap map) throws Exception {
            PMap sources = PMap.from(map.get(CodeCompilerService.KEY_SOURCES))
                    .orElseThrow(IllegalArgumentException::new);

            Map<String, String> extractedSources
                    = sources.keys().stream()
                            .collect(Collectors.toUnmodifiableMap(k -> k,
                                    k -> sources.get(k).toString()));

            LogBuilder log = new LogBuilder(getLogLevel(map));

            List<String> options = List.of(
                    "-proc:none",
                    "-parameters",
                    "--release", String.valueOf(release.ordinal()),
                    "--add-modules", "ALL-MODULE-PATH",
                    "--module-path", defModulepath,
                    "--class-path", buildClasspath());

            Map<String, Supplier<InputStream>> shared
                    = Optional.ofNullable(map.get(CodeCompilerService.KEY_SHARED_CLASSES))
                            .flatMap(PMap::from)
                            .map(m -> processExistingClasses(m))
                            .orElse(Map.of());

            Map<String, byte[]> classFiles
                    = CompilerTask.create(extractedSources)
                            .existingClasses(shared)
                            .options(options)
                            .messageHandler(new LogMessageHandler(log))
                            .compile();

            PMap classes = convertClasses(classFiles);
            PMap response = PMap.of(CodeCompilerService.KEY_CLASSES, classes,
                    CodeCompilerService.KEY_LOG, PArray.of(log.toList()),
                    EXT_CLASSPATH, libPath);
            return response;
        }

        private LogLevel getLogLevel(PMap map) {
            String level = map.getString(CodeCompilerService.KEY_LOG_LEVEL, null);
            if (level != null) {
                return LogLevel.valueOf(level);
            } else {
                return LogLevel.WARNING;
            }
        }

        private String buildClasspath() {
            if (libFiles.isEmpty()) {
                return defClasspath;
            } else {
                String cp = libFiles.stream()
                        .map(p -> p.toAbsolutePath().toString())
                        .collect(Collectors.joining(File.pathSeparator));
                if (!defClasspath.isBlank()) {
                    cp += (File.pathSeparator + defClasspath);
                }
                return cp;
            }
        }

        private PMap convertClasses(Map<String, byte[]> classes) {
            PMap.Builder bld = PMap.builder();
            classes.entrySet().stream().forEach((type) -> {
                bld.put(type.getKey(), PBytes.valueOf(type.getValue()));
            });
            return bld.build();
        }

        private Map<String, Supplier<InputStream>> processExistingClasses(PMap classes) {
            return classes.keys().stream()
                    .map(cls -> Map.entry(cls, (Supplier<InputStream>) ()
                    -> PBytes.from(classes.get(cls))
                            .map(PBytes::asInputStream)
                            .orElseGet(PBytes.EMPTY::asInputStream)))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }

    }

    private class AddLibsControl implements Control, LibraryResolver.Context {

        private static final String MAP_FILE_NAME = "libraries.map";
        private static final String MAP_KEY_LIBRARIES = "libraries";
        private static final String MAP_KEY_PROVIDED = "provided";
        private static final String MAP_KEY_FILES = "files";

        private final LogBuilder log;

        private AddLibsControl() {
            this.log = new LogBuilder(LogLevel.INFO);
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                PArray addLibs = PArray.from(call.args().get(0))
                        .orElseThrow(IllegalArgumentException::new);
                process(addLibs);
                if (!log.isEmpty()) {
                    getLookup().find(Services.class)
                            .flatMap(s -> s.locate(LogService.class))
                            .ifPresent(ad -> router.route(Call.createQuiet(
                            ControlAddress.of(ad, LogService.LOG),
                            call.to(), call.time(), log.toList()))
                            );
                    log.clear();
                }
                router.route(call.reply(libs));
            }
        }

        private void process(PArray addLibs) throws Exception {
            for (Value value : addLibs) {
                PResource resource = PResource.from(value)
                        .orElseThrow(IllegalArgumentException::new);
                if (libResolved.contains(resource)) {
                    continue;
                }
                if (libProvided.contains(resource)) {
                    libResolved.add(resource);
                    continue;
                }
                if (!processLibrariesMap(resource)) {
                    processResource(resource);
                }
            }
            libs = PArray.of(libResolved);
            libsAll = PArray.of(libProvided);
            libPath = libFiles.stream()
                    .map(Path::toUri)
                    .map(PResource::of)
                    .collect(PArray.collector());
        }

        private boolean processLibrariesMap(PResource resource) throws Exception {
            if (resource.toString().endsWith(MAP_FILE_NAME)) {
                Path mapFile = findFile(resource, p -> p.toString().endsWith(MAP_FILE_NAME));
                if (mapFile != null) {
                    PMap map = PMap.parse(Files.readString(mapFile));
                    List<PResource> resolved = extractStream(map, MAP_KEY_LIBRARIES)
                            .map(v -> PResource.from(v).orElseThrow(IllegalArgumentException::new))
                            .toList();
                    List<PResource> provided = extractStream(map, MAP_KEY_PROVIDED)
                            .map(v -> PResource.from(v).orElseThrow(IllegalArgumentException::new))
                            .toList();
                    List<Path> files = extractStream(map, MAP_KEY_FILES)
                            .map(v -> Path.of(v.toString()))
                            .map(p -> mapFile.getParent().resolve(p))
                            .toList();
                    libResolved.add(resource);
                    libProvided.add(resource);
                    libResolved.addAll(resolved);
                    libProvided.addAll(provided);
                    libFiles.addAll(files);
                    return true;
                }
            }
            return false;
        }

        private void processResource(PResource resource) throws Exception {
            LibraryResolver.Entry entry = resolve(resource);
            libResolved.add(entry.resource());
            libProvided.addAll(entry.provides());
            libFiles.addAll(entry.files());
        }

        private LibraryResolver.Entry resolve(PResource resource) throws Exception {
            for (var resolver : libResolvers) {
                var response = resolver.resolve(resource, this);
                if (response.isPresent()) {
                    var entry = response.get();
                    return entry;
                }
            }
            Path jarFile = findFile(resource, p -> p.toString().endsWith(".jar"));
            if (jarFile != null) {
                return new LibraryResolver.Entry(resource, List.of(jarFile));
            } else {
                throw new IllegalArgumentException("Invalid library : " + resource);
            }
        }

        private Path findFile(PResource resource, Predicate<Path> filter) {
            List<URI> uris = resource.resolve(getLookup());
            return uris.stream()
                    .filter(u -> "file".equalsIgnoreCase(u.getScheme()))
                    .map(Path::of)
                    .filter(p -> filter.test(p) && Files.isReadable(p))
                    .findFirst().orElse(null);
        }

        private Stream<Value> extractStream(PMap map, String key) {
            return Optional.ofNullable(map.get(key))
                    .flatMap(PArray::from).orElseThrow(IllegalArgumentException::new)
                    .stream();
        }

        @Override
        public Stream<PResource> resolved() {
            return libResolved.stream();
        }

        @Override
        public Stream<PResource> provided() {
            return Stream.concat(libSystem.stream(), libProvided.stream());
        }

        @Override
        public Stream<Path> files() {
            return libFiles.stream();
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
