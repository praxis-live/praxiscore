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
package org.praxislive.code.services.tools;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import javax.tools.JavaCompiler;
import org.praxislive.code.ClassBodyContext;

/**
 *
 * 
 */
public class ClassBodyCompiler {

    public final static String DEFAULT_CLASS_NAME = "$";

    private final ClassBodyContext<?> classBodyContext;
    private final Set<File> extClasspath;
    private final String defClasspath;
    private final String defModulepath;

    private MessageHandler messageHandler;
    private JavaCompiler compiler;
    private SourceVersion release;

    private ClassBodyCompiler(ClassBodyContext<?> classBodyContext) {
        this.classBodyContext = classBodyContext;
        this.release = SourceVersion.RELEASE_11;
        this.extClasspath = new LinkedHashSet<>();
        this.defClasspath = System.getProperty("java.class.path", "");
        this.defModulepath = System.getProperty("jdk.module.path", "");
    }

    public ClassBodyCompiler addMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        return this;
    }

    @Deprecated
    public ClassBodyCompiler extendClasspath(Set<File> libs) {
        extClasspath.addAll(libs);
        return this;
    }
    
    public ClassBodyCompiler extendClasspath(List<File> libs) {
        extClasspath.addAll(libs);
        return this;
    }

    public ClassBodyCompiler setCompiler(JavaCompiler compiler) {
        this.compiler = compiler;
        return this;
    }

    public ClassBodyCompiler setRelease(SourceVersion release) {
        this.release = release;
        return this;
    }

    public Map<String, byte[]> compile(String code) throws CompilationException {
        try {
            String source = ClassBodyWrapper.create()
                    .className(DEFAULT_CLASS_NAME)
                    .extendsType(classBodyContext.getExtendedClass())
                    .implementsTypes(List.of(classBodyContext.getImplementedInterfaces()))
                    .defaultImports(List.of(classBodyContext.getDefaultImports()))
                    .wrap(code);
            
            CompilerTask task = CompilerTask.create(Map.of(DEFAULT_CLASS_NAME, source));
            
            if (messageHandler != null) {
                task.messageHandler(messageHandler);
            }
            
            List<String> opts = List.of("-Xlint:all", "-proc:none",
                    "--release", String.valueOf(release.ordinal()),
                    "--add-modules", "ALL-MODULE-PATH",
                    "--module-path", defModulepath,
                    "-classpath", buildClasspath());
            
            task.options(opts);
            return task.compile();
            
        } catch (CompilationException ex) {
            throw new CompilationException(ex);
        } catch (Exception ex) {
            throw new CompilationException(ex);
        }
    }

    private String buildClasspath() {
        if (extClasspath.isEmpty()) {
            return defClasspath;
        } else {
            return extClasspath.stream()
                    .map(f -> f.getAbsolutePath())
                    .collect(Collectors.joining(File.pathSeparator,
                            "", File.pathSeparator + defClasspath));
        }
    }

    public static ClassBodyCompiler create(ClassBodyContext<?> classBodyContext) {
        return new ClassBodyCompiler(classBodyContext);
    }

}
