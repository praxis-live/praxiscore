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
import org.praxislive.core.Value;
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
import org.praxislive.logging.LogBuilder;
import org.praxislive.logging.LogLevel;

/**
 *
 */
public class DefaultCodeFactoryService extends AbstractRoot
        implements RootHub.ServiceProvider {

    private final static ConcurrentMap<ClassCacheKey, Class<? extends CodeDelegate>> CODE_CACHE
            = new ConcurrentHashMap<>();

    private final Map<String, Control> controls;
    private final ComponentRegistry registry;
    private final Set<PResource> libs;

    private LibraryClassloader libClassloader;

    public DefaultCodeFactoryService() {
        controls = Map.of(
                CodeComponentFactoryService.NEW_INSTANCE, new NewInstanceControl(),
                CodeContextFactoryService.NEW_CONTEXT, new NewContextControl()
        );
        registry = ComponentRegistry.getInstance();
        libs = new LinkedHashSet<>();
    }

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(CodeComponentFactoryService.class, CodeContextFactoryService.class);
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

    private PMap createCompilerTask(ClassBodyContext<?> cbc, LogLevel logLevel, String source) {
        return PMap.of(
                CodeCompilerService.KEY_CLASS_BODY_CONTEXT,
                cbc.getClass().getName(),
                CodeCompilerService.KEY_LOG_LEVEL,
                logLevel.asPString(),
                CodeCompilerService.KEY_CODE,
                source);
    }

    private Class<? extends CodeDelegate> extractCodeDelegateClass(Value response) throws Exception {
        PMap data = PMap.from(response).orElseThrow();
        PMap classes = PMap.from(data.get(CodeCompilerService.KEY_CLASSES)).orElseThrow();
        PArray.from(data.get(DefaultCompilerService.EXT_CLASSPATH)).ifPresent(this::processExtClasspath);
        ClassLoader classLoader = new PMapClassLoader(classes, libClassloader);
        return (Class<? extends CodeDelegate>) classLoader.loadClass("$");
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

    private void extractCompilerLog(Value response, LogBuilder logBuilder) throws Exception {
        PMap data = PMap.from(response).orElseThrow();
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
                        createCompilerTask(cbc, LogLevel.ERROR, src));
            }

        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            try {
                CodeFactory<CodeDelegate> codeFactory = findCodeFactory();
                Class<? extends CodeDelegate> cls = extractCodeDelegateClass(call.args().get(0));
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

    }

    private class NewContextControl extends AbstractAsyncControl {

        @Override
        @SuppressWarnings("unchecked")
        protected Call processInvoke(Call call) throws Exception {
            CodeContextFactoryService.Task<CodeDelegate> task = findTask();
            CodeFactory<CodeDelegate> factory = task.getFactory();
            ClassBodyContext<CodeDelegate> cbc = factory.getClassBodyContext();
            String src = task.getCode();
            Class<? extends CodeDelegate> cls;
            if (src.trim().isEmpty()) {
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
                return Call.create(
                        findCompilerService(),
                        call.to(),
                        call.time(),
                        createCompilerTask(cbc, LogLevel.ERROR, src));
            }

        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            try {
                CodeContextFactoryService.Task<CodeDelegate> task = findTask();
                Class<? extends CodeDelegate> cls = extractCodeDelegateClass(call.args().get(0));
                CodeDelegate delegate = cls.getDeclaredConstructor().newInstance();
                LogBuilder log = new LogBuilder(task.getLogLevel());
                extractCompilerLog(call.args().get(0), log);
                return getActiveCall().reply(PReference.of(createContext(task, log, delegate)));
            } catch (Throwable throwable) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                } else {
                    throw new Exception(throwable);
                }
            }
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
