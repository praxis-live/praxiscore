/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith.
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.praxislive.base.AbstractAsyncControl;
import org.praxislive.base.AbstractRoot;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.code.CodeComponent;
import org.praxislive.code.CodeComponentFactoryService;
import org.praxislive.code.CodeContext;
import org.praxislive.code.CodeContextFactoryService;
import org.praxislive.code.CodeDelegate;
import org.praxislive.code.CodeFactory;
import org.praxislive.code.ClassBodyContext;
import org.praxislive.code.SharedCodeService;
import org.praxislive.code.services.tools.ClassBodyWrapper;
import org.praxislive.core.Call;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PResource;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;

/**
 *
 */
public class DefaultCodeFactoryService extends AbstractRoot
        implements RootHub.ServiceProvider {

    private final static ConcurrentMap<ClassCacheKey, Class<? extends CodeDelegate>> CODE_CACHE
            = new ConcurrentHashMap<>();

    private final static String SHARED_PREFIX = "SHARED.";
    private final static String WRAPPED_CLASS_NAME = "$";

    private final Map<String, Control> controls;
    private final ComponentRegistry registry;
    private final Set<PResource> libs;

    private LibraryClassloader libClassloader;

    public DefaultCodeFactoryService() {
        controls = Map.of(
                CodeComponentFactoryService.NEW_INSTANCE, new NewInstanceControl(),
                CodeContextFactoryService.NEW_CONTEXT, new NewContextControl(),
                SharedCodeService.NEW_SHARED, new NewSharedControl()
        );
        registry = ComponentRegistry.getInstance();
        libs = new LinkedHashSet<>();
    }

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(CodeComponentFactoryService.class,
                CodeContextFactoryService.class,
                SharedCodeService.class);
    }

    @Override
    protected void activating() {
        libClassloader = new LibraryClassloader(Thread.currentThread().getContextClassLoader());
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        try {
            controls.get(call.to().controlID()).call(call, router);
        } catch (Exception ex) {
            router.route(call.error(PError.of(ex)));
        }
    }

    private ControlAddress findCompilerService() throws Exception {
        return ControlAddress.of(findService(CodeCompilerService.class),
                CodeCompilerService.COMPILE);
    }

    private String wrapClassBody(ClassBodyContext<?> cbc, String code) {
        return ClassBodyWrapper.create()
                .className(WRAPPED_CLASS_NAME)
                .extendsType(cbc.getExtendedClass())
                .implementsTypes(List.of(cbc.getImplementedInterfaces()))
                .defaultImports(List.of(cbc.getDefaultImports()))
                .wrap(code);
    }

    private Class<? extends CodeDelegate> extractCodeDelegateClass(
            PMap data, ClassLoader parentClassLoader) throws Exception {
        PMap classes = PMap.from(data.get(CodeCompilerService.KEY_CLASSES)).orElseThrow();
        PArray.from(data.get(DefaultCompilerService.EXT_CLASSPATH)).ifPresent(this::processExtClasspath);
        ClassLoader classLoader = new PMapClassLoader(classes,
                parentClassLoader == null ? libClassloader : parentClassLoader);
        return (Class<? extends CodeDelegate>) classLoader.loadClass(WRAPPED_CLASS_NAME);
    }

    private void processExtClasspath(PArray extCP) {
        if (extCP.isEmpty()) {
            return;
        }
        List<PResource> extLibs = extCP.stream()
                .map(v -> PResource.from(v).orElseThrow(() -> new IllegalArgumentException()))
                .collect(Collectors.toCollection(ArrayList::new));

        extLibs.removeAll(libs);
        Lookup lkp = getLookup();
        extLibs.forEach(res -> {
            URI lib = res.resolve(lkp).stream()
                    .filter(uri -> !"file".equals(uri.getScheme()) || new File(uri).exists())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Can't find library : " + res));
            try {
                libClassloader.addURL(lib.toURL());
            } catch (MalformedURLException ex) {
                throw new IllegalArgumentException(ex);
            }
            libs.add(res);
        });
    }

    private void extractCompilerLog(PMap data, LogBuilder logBuilder) throws Exception {
        PArray log = PArray.from(data.get(CodeCompilerService.KEY_LOG)).orElseThrow();
        for (int i = 0; i < log.size(); i += 2) {
            logBuilder.log(LogLevel.valueOf(log.get(i).toString()), log.get(i + 1).toString());
        }
    }

    private class NewInstanceControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            CodeFactory<CodeDelegate> codeFactory = findCodeFactory();
            ClassBodyContext<?> cbc = codeFactory.getClassBodyContext();
            String src = codeFactory.getSourceTemplate();
            Class<? extends CodeDelegate> cls = codeFactory.getDefaultDelegateClass()
                    .orElseGet(() -> CODE_CACHE.get(new ClassCacheKey(cbc, src)));
            if (cls != null) {
                return call.reply(PReference.of(createComponent(codeFactory, cls)));
            } else {
                return Call.create(
                        findCompilerService(),
                        call.to(),
                        call.time(),
                        createCompilerTask(cbc, src));
            }

        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            try {
                PMap data = PMap.from(call.args().get(0)).orElseThrow(IllegalArgumentException::new);
                CodeFactory<CodeDelegate> codeFactory = findCodeFactory();
                Class<? extends CodeDelegate> cls = extractCodeDelegateClass(data, null);
                CodeDelegate delegate = cls.getDeclaredConstructor().newInstance();
                CodeComponent<CodeDelegate> cmp = codeFactory.task().createComponent(delegate);
                CODE_CACHE.putIfAbsent(new ClassCacheKey(codeFactory.getClassBodyContext(), codeFactory.getSourceTemplate()), cls);
                return getActiveCall().reply(PReference.of(cmp));
            } catch (Throwable throwable) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                } else {
                    throw new Exception(throwable);
                }
            }
        }

        private CodeFactory<CodeDelegate> findCodeFactory() throws Exception {
            ComponentType type = ComponentType.from(getActiveCall().args().get(0)).orElseThrow();
            ComponentFactory cmpFactory = registry.getComponentFactory(type);
            return cmpFactory.getMetaData(type).getLookup()
                    .find(CodeFactory.class).orElse(null);
        }

        private CodeComponent<CodeDelegate> createComponent(
                CodeFactory<CodeDelegate> codeFactory,
                Class<? extends CodeDelegate> delegateClass) throws Exception {
            return codeFactory.task().createComponent(delegateClass.getDeclaredConstructor().newInstance());
        }

        private PMap createCompilerTask(ClassBodyContext<?> cbc, String code) {
            String source = wrapClassBody(cbc, code);
            return PMap.of(CodeCompilerService.KEY_SOURCES, PMap.of(WRAPPED_CLASS_NAME, source));
        }

    }

    private class NewContextControl extends AbstractAsyncControl {

        private boolean usingShared;

        @Override
        @SuppressWarnings("unchecked")
        protected Call processInvoke(Call call) throws Exception {
            usingShared = false;
            CodeContextFactoryService.Task<CodeDelegate> task = findTask();
            CodeFactory<CodeDelegate> factory = task.getFactory();
            ClassBodyContext<CodeDelegate> cbc = factory.getClassBodyContext();
            String src = task.getCode();
            Class<? extends CodeDelegate> cls;
            if (src.isBlank()) {
                src = factory.getSourceTemplate();
                cls = CODE_CACHE.get(new ClassCacheKey(cbc, src));
            } else {
                // @TODO weak code cache for user code
                cls = null;
            }
            if (cls != null) {
                LogBuilder log = new LogBuilder(task.getLogLevel());
                CodeDelegate delegate = cls.getDeclaredConstructor().newInstance();
                return call.reply(PReference.of(createContext(task, log, delegate)));
            } else {
                usingShared = src.contains(SHARED_PREFIX);
                return Call.create(
                        findCompilerService(),
                        call.to(),
                        call.time(),
                        createCompilerTask(task, cbc, src, usingShared));
            }

        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            boolean shared = usingShared;
            usingShared = false;
            try {
                PMap data = PMap.from(call.args().get(0)).orElseThrow(IllegalArgumentException::new);
                CodeContextFactoryService.Task<CodeDelegate> task = findTask();
                Class<? extends CodeDelegate> cls = extractCodeDelegateClass(data,
                        shared ? task.getSharedClassLoader() : null);
                CodeDelegate delegate = cls.getDeclaredConstructor().newInstance();
                LogBuilder log = new LogBuilder(task.getLogLevel());
                extractCompilerLog(data, log);
                return getActiveCall().reply(PReference.of(createContext(task, log, delegate)));
            } catch (Throwable throwable) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                } else {
                    throw new Exception(throwable);
                }
            }
        }

        @Override
        protected Call processError(Call call) throws Exception {
            usingShared = false;
            return super.processError(call);
        }

        private CodeContextFactoryService.Task<CodeDelegate> findTask() throws Exception {
            return PReference.from(getActiveCall().args().get(0))
                    .flatMap(r -> r.as(CodeContextFactoryService.Task.class))
                    .orElseThrow();
        }

        private CodeContextFactoryService.Result<CodeDelegate> createContext(
                CodeContextFactoryService.Task<CodeDelegate> task,
                LogBuilder log,
                CodeDelegate delegate) {
            CodeContext<CodeDelegate> context = task.getFactory().task()
                    .attachPrevious(task.getPrevious())
                    .attachLogging(log)
                    .createContext(delegate);
            return new CodeContextFactoryService.Result<>(context, log);
        }

        private PMap createCompilerTask(CodeContextFactoryService.Task<?> task,
                ClassBodyContext<?> cbc, String code, boolean shared) {
            String source = wrapClassBody(cbc, code);
            if (shared) {
                PMap sharedClasses;
                ClassLoader sharedCL = task.getSharedClassLoader();
                if (sharedCL instanceof PMapClassLoader) {
                    sharedClasses = ((PMapClassLoader) sharedCL).getClassesMap();
                } else {
                    sharedClasses = PMap.EMPTY;
                }
                return PMap.of(CodeCompilerService.KEY_SOURCES, PMap.of(WRAPPED_CLASS_NAME, source),
                        CodeCompilerService.KEY_LOG_LEVEL, task.getLogLevel(),
                        CodeCompilerService.KEY_SHARED_CLASSES, sharedClasses);
            } else {
                return PMap.of(CodeCompilerService.KEY_SOURCES, PMap.of(WRAPPED_CLASS_NAME, source),
                        CodeCompilerService.KEY_LOG_LEVEL, task.getLogLevel());

            }
        }

    }

    private class NewSharedControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            SharedCodeService.Task task = findTask();
            PMap sources = validateSources(task.getSources());
            return Call.create(
                    findCompilerService(),
                    call.to(),
                    call.time(),
                    PMap.of(CodeCompilerService.KEY_SOURCES, sources,
                            CodeCompilerService.KEY_LOG_LEVEL, task.getLogLevel())
            );
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            PMap data = PMap.from(call.args().get(0)).orElseThrow(IllegalArgumentException::new);
            SharedCodeService.Task task = findTask();
            ClassLoader sharedClasses = extractClassLoader(data);
            LogBuilder log = new LogBuilder(task.getLogLevel());
            extractCompilerLog(data, log);
            Map<String, SharedCodeService.DependentResult<CodeDelegate>> depResults
                    = task.getDependents().entrySet().stream()
                            .map(e -> Map.entry(e.getKey(), processDependent(sharedClasses, log, e.getValue())))
                            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            SharedCodeService.Result result = new SharedCodeService.Result(
                    sharedClasses, depResults, log);
            return getActiveCall().reply(PReference.of(result));
        }

        private SharedCodeService.Task findTask() throws Exception {
            return PReference.from(getActiveCall().args().get(0))
                    .flatMap(r -> r.as(SharedCodeService.Task.class))
                    .orElseThrow();
        }

        private PMap validateSources(PMap sources) {
            if (!sources.keys().stream().allMatch(name -> name.startsWith("SHARED."))) {
                throw new IllegalArgumentException("Sources contains class outside SHARED package");
            }
            return sources;
        }

        private ClassLoader extractClassLoader(PMap data) {
            PMap classes = PMap.from(data.get(CodeCompilerService.KEY_CLASSES))
                    .orElseThrow();
            PArray.from(data.get(DefaultCompilerService.EXT_CLASSPATH))
                    .ifPresent(ext -> processExtClasspath(ext));
            return new PMapClassLoader(classes, libClassloader);
        }

        private SharedCodeService.DependentResult processDependent(
                ClassLoader sharedClasses,
                LogBuilder log,
                SharedCodeService.DependentTask depTask) {
            try {
                PMap depClasses =
                        ((PMapClassLoader) depTask.getExistingClass().getClassLoader()).getClassesMap();
                ClassLoader newCL = new PMapClassLoader(depClasses, sharedClasses);
                Class<?> depClass = newCL.loadClass(WRAPPED_CLASS_NAME);
                var factoryTask = depTask.getFactory().task();
                CodeContext<?> context = factoryTask
                        .attachLogging(log)
                        .attachPrevious(depTask.getExistingClass())
                        .createContext((CodeDelegate) depClass.getDeclaredConstructor().newInstance());
                return new SharedCodeService.DependentResult(context, depTask.getExistingClass());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

    }

    private static class LibraryClassloader extends URLClassLoader {

        public LibraryClassloader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        protected void addURL(URL url) {
            super.addURL(url);
        }
    }

}
