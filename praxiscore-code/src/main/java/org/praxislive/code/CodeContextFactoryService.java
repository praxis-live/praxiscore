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
package org.praxislive.code;

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;

/**
 * A {@link Service} for creating new {@link CodeContext}. Must be running in
 * the same process as the CodeComponent due to Task and Result references.
 * Should make use of a {@link CodeCompilerService} implementation for compiling
 * source code (which does support other processes).
 */
public class CodeContextFactoryService implements Service {

    public final static String NEW_CONTEXT = "new-context";
    public final static ControlInfo NEW_CONTEXT_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(PReference.info(Task.class)),
                    List.of(PReference.info(Result.class)),
                    PMap.EMPTY);

    @Override
    public Stream<String> controls() {
        return Stream.of(NEW_CONTEXT);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        if (NEW_CONTEXT.equals(control)) {
            return NEW_CONTEXT_INFO;
        }
        throw new IllegalArgumentException();
    }

    /**
     * Task sent to the service to request a context and delegate be created
     * from the provided source code.
     *
     * @param <D> delegate type
     */
    public final static class Task<D extends CodeDelegate> {

        private final CodeFactory<D> factory;
        private final String code;
        private final LogLevel logLevel;
        private final Class<D> previous;

        /**
         * Create task.
         *
         * @param factory code factory that handles actual context creation
         * @param code source code
         * @param logLevel log level
         * @param previous previous delegate class, or null
         */
        public Task(CodeFactory<D> factory,
                String code,
                LogLevel logLevel,
                Class<D> previous) {
            this.factory = factory;
            this.code = code;
            this.logLevel = logLevel;
            this.previous = previous;
        }

        /**
         * Get the code factory.
         *
         * @return code factory
         */
        public CodeFactory<D> getFactory() {
            return factory;
        }

        /**
         * Get user source code.
         *
         * @return source code
         */
        public String getCode() {
            return code;
        }

        /**
         * Get active log level.
         *
         * @return log level
         */
        public LogLevel getLogLevel() {
            return logLevel;
        }

        /**
         * Previous delegate class, or null.
         *
         * @return previous delegate, or null
         */
        public Class<D> getPrevious() {
            return previous;
        }

    }

    /**
     * Result from service on successful creation of context and delegate.
     *
     * @param <D> delegate type
     */
    public final static class Result<D extends CodeDelegate> {

        private final CodeContext<D> context;
        private final LogBuilder log;

        /**
         * Create result, for use by service provider.
         *
         * @param context
         * @param log
         */
        public Result(CodeContext<D> context, LogBuilder log) {
            this.context = context;
            this.log = log;
        }

        /**
         * Get created context.
         * 
         * @return context
         */
        public CodeContext<D> getContext() {
            return context;
        }

        /**
         * Get log builder with any warning or error messages.
         * 
         * @return log
         */
        public LogBuilder getLog() {
            return log;
        }

    }

}
