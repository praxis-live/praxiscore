/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2024 Neil C Smith.
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.praxislive.base.AbstractAsyncControl;
import org.praxislive.base.AbstractRoot;
import org.praxislive.code.CodeChildFactoryService;
import org.praxislive.code.CodeCompilerService;
import org.praxislive.code.CodeComponent;
import org.praxislive.code.CodeComponentFactoryService;
import org.praxislive.code.CodeContext;
import org.praxislive.code.CodeContextFactoryService;
import org.praxislive.code.CodeDelegate;
import org.praxislive.code.CodeFactory;
import org.praxislive.code.CodeRootFactoryService;
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
import org.praxislive.core.types.PBytes;

/**
 *
 */
public class DefaultCodeFactoryService extends AbstractRoot
        implements RootHub.ServiceProvider {

    private final static String SHARED_PREFIX = "SHARED.";
    private final static String WRAPPED_CLASS_NAME = "$";

    private final Map<String, Control> controls;
    private final ComponentRegistry registry;
    private final Set<PResource> libs;

    private LibraryClassloader libClassloader;

    public DefaultCodeFactoryService() {
        controls = Map.of(
                CodeComponentFactoryService.NEW_INSTANCE, new NewInstanceControl(),
                CodeRootFactoryService.NEW_ROOT_INSTANCE, new NewRootInstanceControl(),
                CodeChildFactoryService.NEW_CHILD_INSTANCE, new NewChildInstanceControl(),
                CodeContextFactoryService.NEW_CONTEXT, new NewContextControl(),
                SharedCodeService.NEW_SHARED, new NewSharedControl()
        );
        registry = ComponentRegistry.getInstance();
        libs = new LinkedHashSet<>();
    }

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(CodeComponentFactoryService.class,
                CodeRootFactoryService.class,
                CodeChildFactoryService.class,
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

    private PMap createCompilerTask(CodeFactory<?> codeFactory,
            LogLevel logLevel,
            String fullClassName,
            String code,
            ClassLoader sharedCodeClassLoader) {
        String source = wrapClassBody(codeFactory.baseClass(),
                codeFactory.baseImports(),
                fullClassName, code);
        PMap sharedClasses;
        if (sharedCodeClassLoader instanceof PMapClassLoader pMapCL) {
            sharedClasses = pMapCL.getClassesMap();
        } else {
            sharedClasses = PMap.EMPTY;
        }
        if (sharedClasses.isEmpty()) {
            return PMap.of(CodeCompilerService.KEY_SOURCES, PMap.of(fullClassName, source),
                    CodeCompilerService.KEY_LOG_LEVEL, logLevel);
        } else {
            return PMap.of(CodeCompilerService.KEY_SOURCES, PMap.of(fullClassName, source),
                    CodeCompilerService.KEY_LOG_LEVEL, logLevel,
                    CodeCompilerService.KEY_SHARED_CLASSES, sharedClasses);

        }
    }

    private ControlAddress findCompilerService() throws Exception {
        return ControlAddress.of(findService(CodeCompilerService.class),
                CodeCompilerService.COMPILE);
    }

    private String wrapClassBody(Class<?> baseClass, List<String> imports, String fullClassName, String code) {
        return ClassBodyWrapper.create()
                .className(fullClassName)
                .extendsType(baseClass)
                .defaultImports(imports)
                .wrap(code);
    }

    private Class<? extends CodeDelegate> extractCodeDelegateClass(
            PMap data, ClassLoader parentClassLoader) throws Exception {
        PMap classes = PMap.from(data.get(CodeCompilerService.KEY_CLASSES)).orElseThrow();
        String className = classes.keys().stream()
                .filter(name -> WRAPPED_CLASS_NAME.equals(name) || name.endsWith("." + WRAPPED_CLASS_NAME))
                .findFirst()
                .orElseThrow();
        PArray.from(data.get(DefaultCompilerService.EXT_CLASSPATH)).ifPresent(this::processExtClasspath);
        ClassLoader classLoader = new PMapClassLoader(classes,
                parentClassLoader == null ? libClassloader : parentClassLoader);
        return (Class<? extends CodeDelegate>) classLoader.loadClass(className);
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

    static String codeAddressToPackage(ControlAddress address) {
        String pkg = address.toString()
                .replace("/", ".")
                .replace("_", "$") // unlikely
                .replace("-", "_");
        return genCodePrefix() + pkg;
    }

    static boolean codeAddressMatchesPackage(ControlAddress address, String pkg) {
        if (!pkg.startsWith("CODE")) {
            return false;
        }
        String addressPkg = codeAddressToPackage(address);
        return addressPkg.substring(addressPkg.indexOf(".")).equals(pkg.substring(pkg.indexOf(".")));
    }

    private static String genCodePrefix() {
        return String.format(Locale.ROOT, "CODE%04x", (int) (Math.random() * 0xFFFF));
    }

    private class NewInstanceControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            var codeFactory = findCodeFactory();
            var src = codeFactory.sourceTemplate();
            var cls = codeFactory.defaultDelegateClass().orElse(null);
            if (cls != null) {
                return call.reply(PReference.of(createComponent(codeFactory, cls)));
            } else {
                String fullClassName = genCodePrefix() + ".NEW_INSTANCE." + WRAPPED_CLASS_NAME;
                return Call.create(
                        findCompilerService(),
                        call.to(),
                        call.time(),
                        createCompilerTask(codeFactory, LogLevel.ERROR, fullClassName, src, null));
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
                return getActiveCall().reply(PReference.of(cmp));
            } catch (Throwable throwable) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                } else {
                    throw new Exception(throwable);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private CodeFactory<CodeDelegate> findCodeFactory() throws Exception {
            ComponentType type = ComponentType.from(getActiveCall().args().get(0)).orElseThrow();
            ComponentFactory cmpFactory = registry.getComponentFactory(type);
            return cmpFactory.componentData(type).find(CodeFactory.class).orElse(null);
        }

        private CodeComponent<CodeDelegate> createComponent(
                CodeFactory<CodeDelegate> codeFactory,
                Class<? extends CodeDelegate> delegateClass) throws Exception {
            return codeFactory.task().createComponent(delegateClass.getDeclaredConstructor().newInstance());
        }

    }

    private class NewRootInstanceControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            var codeFactory = findCodeFactory();
            var src = codeFactory.sourceTemplate();
            var cls = codeFactory.defaultDelegateClass().orElse(null);
            if (cls != null) {
                return call.reply(PReference.of(createComponent(codeFactory, cls)));
            } else {
                String fullClassName = genCodePrefix() + ".NEW_INSTANCE." + WRAPPED_CLASS_NAME;
                return Call.create(
                        findCompilerService(),
                        call.to(),
                        call.time(),
                        createCompilerTask(codeFactory, LogLevel.ERROR, fullClassName, src, null));
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
                return getActiveCall().reply(PReference.of(cmp));
            } catch (Throwable throwable) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                } else {
                    throw new Exception(throwable);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private CodeFactory<CodeDelegate> findCodeFactory() throws Exception {
            ComponentType type = ComponentType.from(getActiveCall().args().get(0)).orElseThrow();
            ComponentFactory cmpFactory = registry.getComponentFactory(type);
            return cmpFactory.rootData(type).find(CodeFactory.class).orElse(null);
        }

        private CodeComponent<CodeDelegate> createComponent(
                CodeFactory<CodeDelegate> codeFactory,
                Class<? extends CodeDelegate> delegateClass) throws Exception {
            return codeFactory.task().createComponent(delegateClass.getDeclaredConstructor().newInstance());
        }

    }

    private class NewChildInstanceControl extends AbstractAsyncControl {

        CodeFactory<CodeDelegate> activeCodeFactory;

        @Override
        protected Call processInvoke(Call call) throws Exception {
            activeCodeFactory = null;
            CodeChildFactoryService.Task task = findTask();
            Class<? extends CodeDelegate> baseType = task.baseDelegate();
            CodeFactory.Base<CodeDelegate> factoryBase = ComponentRegistry.getInstance().findSuitableBase(baseType);
            if (factoryBase == null) {
                throw new IllegalArgumentException("No base support found for " + baseType);
            }
            ClassLoader shared = baseType.getClassLoader() instanceof PMapClassLoader pmcl
                    ? pmcl : null;
            String source = "extends " + baseType.getCanonicalName() + ";";
            if (!task.codeTemplate().isBlank()) {
                source += "\n" + task.codeTemplate();
            }
            String fullClassName = genCodePrefix() + ".NEW_INSTANCE." + WRAPPED_CLASS_NAME;
            activeCodeFactory = factoryBase.create(task.componentType(), source);
            return Call.create(
                    findCompilerService(),
                    call.to(),
                    call.time(),
                    createCompilerTask(activeCodeFactory, task.logLevel(), fullClassName, source, shared)
            );
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            try {
                CodeFactory<CodeDelegate> codeFactory = activeCodeFactory;
                activeCodeFactory = null;
                PMap data = PMap.from(call.args().get(0)).orElseThrow(IllegalArgumentException::new);
                CodeChildFactoryService.Task task = findTask();
                Class<? extends CodeDelegate> cls = extractCodeDelegateClass(
                        data,
                        task.baseDelegate().getClassLoader() instanceof PMapClassLoader pmcl
                        ? pmcl : null);
                CodeDelegate delegate = cls.getDeclaredConstructor().newInstance();
                LogBuilder log = new LogBuilder(task.logLevel());
                extractCompilerLog(data, log);
                CodeChildFactoryService.Result result = createResult(codeFactory, log, delegate);
                return getActiveCall().reply(PReference.of(result));
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
            activeCodeFactory = null;
            return super.processError(call);
        }

        private CodeChildFactoryService.Task findTask() throws Exception {
            return PReference.from(getActiveCall().args().get(0))
                    .flatMap(r -> r.as(CodeChildFactoryService.Task.class))
                    .orElseThrow();
        }

        private CodeChildFactoryService.Result createResult(
                CodeFactory<CodeDelegate> codeFactory,
                LogBuilder log,
                CodeDelegate delegate) {
            CodeComponent<CodeDelegate> context = codeFactory.task()
                    .attachLogging(log)
                    .createComponent(delegate);
            return new CodeChildFactoryService.Result(context, log);
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
            String src = task.getCode();
            Class<? extends CodeDelegate> cls;
            if (src.isBlank()) {
                src = factory.sourceTemplate();
                cls = factory.defaultDelegateClass().orElse(null);
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
                String fullClassName = codeAddressToPackage(call.from()) + "." + WRAPPED_CLASS_NAME;
                return Call.create(
                        findCompilerService(),
                        call.to(),
                        call.time(),
                        createCompilerTask(task.getFactory(),
                                task.getLogLevel(),
                                fullClassName,
                                src,
                                usingShared ? task.getSharedClassLoader() : null));
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

        @SuppressWarnings("unchecked")
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

    }

    private class NewSharedControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            SharedCodeService.Task task = findTask();
            PMap sources = validateSources(task.getSources());
            if (sources.isEmpty()) {
                if (task.getDependents().isEmpty()) {
                    return call.reply(PReference.of(new SharedCodeService.Result()));
                }
            }
            Map<String, String> dependentSources = processDependentSources(task);
            if (!dependentSources.isEmpty()) {
                sources = mergeSources(sources, dependentSources);
            }
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
            PMap data = PMap.from(call.args().get(0)).orElseThrow();
            PMap classes = PMap.from(data.get(CodeCompilerService.KEY_CLASSES)).orElseThrow();
            SharedCodeService.Task task = findTask();
            PArray.from(data.get(DefaultCompilerService.EXT_CLASSPATH))
                    .ifPresent(ext -> processExtClasspath(ext));
            Map<String, List<String>> partionedClasses = partitionClasses(classes);
            ClassLoader sharedClasses = createClassloader(libClassloader, classes,
                    partionedClasses.get(SHARED_PREFIX));
            LogBuilder log = new LogBuilder(task.getLogLevel());
            extractCompilerLog(data, log);
            Map<ControlAddress, SharedCodeService.DependentResult<CodeDelegate>> depResults
                    = task.getDependents().entrySet().stream()
                            .map(e -> Map.entry(e.getKey(),
                            processDependent(e.getKey(),
                                    sharedClasses,
                                    log,
                                    e.getValue(),
                                    classes,
                                    partionedClasses)))
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

        private Map<String, String> processDependentSources(SharedCodeService.Task task) {
            return task.getDependents().entrySet().stream()
                    .map(e -> {
                        var depAddress = e.getKey();
                        var depTask = e.getValue();
                        String clsName = codeAddressToPackage(depAddress) + "." + WRAPPED_CLASS_NAME;
                        var codeFactory = depTask.getFactory();
                        String source = wrapClassBody(
                                codeFactory.baseClass(),
                                codeFactory.baseImports(),
                                clsName,
                                depTask.getExistingSource());
                        return Map.entry(clsName, source);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        private PMap mergeSources(PMap shared, Map<String, String> dependents) {
            var b = PMap.builder();
            shared.keys().stream().forEach(k -> b.put(k, shared.get(k)));
            dependents.entrySet().forEach(e -> b.put(e.getKey(), e.getValue()));
            return b.build();
        }

        private Map<String, List<String>> partitionClasses(PMap classes) {
            return classes.keys().stream()
                    .collect(Collectors.groupingBy(cls -> {
                        if (cls.startsWith(SHARED_PREFIX)) {
                            return SHARED_PREFIX;
                        } else {
                            return cls.substring(0, cls.lastIndexOf("."));
                        }
                    }));
        }

        private ClassLoader createClassloader(ClassLoader parent, PMap classes, List<String> classFilter) {
            var b = PMap.builder();
            classFilter.forEach(cls -> b.put(cls, PBytes.from(classes.get(cls)).orElseThrow()));
            return new PMapClassLoader(b.build(), parent);
        }

        private SharedCodeService.DependentResult processDependent(
                ControlAddress address,
                ClassLoader parent,
                LogBuilder log,
                SharedCodeService.DependentTask depTask,
                PMap allClasses,
                Map<String, List<String>> partionedClasses) {

            try {
                List<String> reqClasses = partionedClasses.entrySet().stream()
                        .filter(e -> codeAddressMatchesPackage(address, e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst().orElseThrow();
                ClassLoader depClassLoader = createClassloader(parent, allClasses, reqClasses);
                String className = reqClasses.stream()
                        .filter(name -> WRAPPED_CLASS_NAME.equals(name) || name.endsWith("." + WRAPPED_CLASS_NAME))
                        .findFirst()
                        .orElseThrow();
                Class<?> depClass = depClassLoader.loadClass(className);
                CodeContext<?> context = depTask.getFactory().task()
                        .attachLogging(log)
                        .attachPrevious(depTask.getExistingClass())
                        .createContext((CodeDelegate) depClass.getDeclaredConstructor().newInstance());
                return new SharedCodeService.DependentResult<>(context, depTask.getExistingClass());
            } catch (Throwable t) {
                throw new IllegalStateException(t);
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
