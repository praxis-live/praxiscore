
/*
 * Copyright 2021 Neil C Smith
 *
 * Forked from Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.praxislive.code.services.tools;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import javax.tools.ToolProvider;

public class CompilerTask {

    private final static MessageHandler DEFAULT_MESSAGE_HANDLER = new MessageHandler() {
        @Override
        public void handleError(String msg) {
        }

        @Override
        public void handleWarning(String msg) {
        }
    };

    private final Map<String, String> sources;
    private MessageHandler messageHandler;
    private List<String> options;

    private Map<String, byte[]> classes;

    private CompilerTask(Map<String, String> sources) {
        this.sources = Map.copyOf(sources);
        messageHandler = DEFAULT_MESSAGE_HANDLER;
        options = List.of("-Xlint:all");
    }

    public CompilerTask options(List<String> options) {
        assertNotCompiled();
        this.options = List.copyOf(options);
        return this;
    }

    public CompilerTask messageHandler(MessageHandler messageHandler) {
        assertNotCompiled();
        this.messageHandler = Objects.requireNonNull(messageHandler);
        return this;
    }

    public Map<String, byte[]> compile() throws CompilationException, IOException {

        assertNotCompiled();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler == null) {
            throw new CompilationException(
                    "JDK Java compiler not available",
                    null
            );
        }

        // Get the original FM, which reads class files through this JVM's BOOTCLASSPATH and
        // CLASSPATH.
        final JavaFileManager fm = compiler.getStandardFileManager(null, null, null);

        // Wrap it so that the output files (in our case class files) are stored in memory rather
        // than in files.
        final MemoryJavaFileManager fileManager = new MemoryJavaFileManager(fm);
        List<JavaFileObject> compilationUnits = sources.entrySet().stream()
                .map(e -> fileManager.addSource(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // Run the compiler.
        try {
            final CompilationException[] caughtCompilationException = new CompilationException[1];
            if (!compiler.getTask(
                    null, // out
                    fileManager, // fileManager
                    new DiagnosticListener<JavaFileObject>() { // diagnosticListener

                @Override
                public void report(Diagnostic<? extends JavaFileObject> diagnostic) {

                    String message = "[" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber()
                            + "] " + diagnostic.getMessage(null) + " (" + diagnostic.getCode() + ")";

                    try {
                        switch (diagnostic.getKind()) {
                            case ERROR:
                                if (CompilerTask.this.messageHandler != null) {
                                    messageHandler.handleError(message);
                                }
                                throw new CompilationException(message);
                            case MANDATORY_WARNING:
                            case WARNING:
                                if (messageHandler != null) {
                                    messageHandler.handleWarning(message);
                                }
                                break;
                            case NOTE:
                            case OTHER:
                            default:
                                break;

                        }
                    } catch (CompilationException ce) {
                        if (caughtCompilationException[0] == null) {
                            caughtCompilationException[0] = ce;
                        }
                    }
                }
            },
                    options,
                    null, // classes for annotation processing
                    compilationUnits
            ).call()) {
                if (caughtCompilationException[0] != null) {
                    throw caughtCompilationException[0];
                }
                throw new CompilationException("Compilation failed", null);
            }
        } catch (RuntimeException rte) {

            // Unwrap the compilation exception and throw it.
            for (Throwable t = rte.getCause(); t != null; t = t.getCause()) {
                if (t instanceof CompilationException) {
                    throw (CompilationException) t; // SUPPRESS CHECKSTYLE AvoidHidingCause
                }
                if (t instanceof IOException) {
                    throw (IOException) t; // SUPPRESS CHECKSTYLE AvoidHidingCause
                }
            }
            throw rte;
        }

        classes = fileManager.extractClassData();

        return classes;

    }

    public Map<String, byte[]> getCompiledClasses() {
        assertCompiled();
        return classes;
    }

    private void assertNotCompiled() {
        if (classes != null) {
            throw new IllegalStateException("Classes already compiled");
        }
    }

    private void assertCompiled() {
        if (classes == null) {
            throw new IllegalStateException("Classes not yet compiled");
        }
    }

    public static CompilerTask create(Map<String, String> sources) {
        return new CompilerTask(sources);
    }

}
