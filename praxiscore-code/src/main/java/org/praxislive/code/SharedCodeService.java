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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;

/**
 *
 */
public class SharedCodeService implements Service {

    public final static String NEW_SHARED = "new-shared";
    public final static ControlInfo NEW_SHARED_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(PReference.info(Task.class)),
                    List.of(PReference.info(Result.class)),
                    PMap.EMPTY);

    @Override
    public Stream<String> controls() {
        return Stream.of(NEW_SHARED);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        if (NEW_SHARED.equals(control)) {
            return NEW_SHARED_INFO;
        }
        throw new IllegalArgumentException();
    }

    public static class Task {

        private final PMap sources;
        private final Map<String, DependentTask<?>> dependents;
        private final LogLevel logLevel;

        public Task(PMap sources,
                Map<String, DependentTask<?>> dependents,
                LogLevel logLevel) {
            this.sources = Objects.requireNonNull(sources);
            this.dependents = Map.copyOf(dependents);
            this.logLevel = logLevel;
        }

        public PMap getSources() {
            return sources;
        }

        public Map<String, DependentTask<?>> getDependents() {
            return dependents;
        }

        public LogLevel getLogLevel() {
            return logLevel;
        }

    }

    public static class Result {

        private final ClassLoader sharedClasses;
        private final Map<String, DependentResult<CodeDelegate>> dependents;
        private final LogBuilder log;

        public Result(ClassLoader sharedClasses,
                Map<String, DependentResult<CodeDelegate>> dependents,
                LogBuilder log) {
            this.sharedClasses = Objects.requireNonNull(sharedClasses);
            this.dependents = Map.copyOf(dependents);
            this.log = Objects.requireNonNull(log);
        }

        public ClassLoader getSharedClasses() {
            return sharedClasses;
        }

        public Map<String, DependentResult<CodeDelegate>> getDependents() {
            return dependents;
        }

        public LogBuilder getLog() {
            return log;
        }
        
    }

    public static class DependentTask<D extends CodeDelegate> {
        
        private final CodeFactory<D> factory;
        private final String existingSource;
        private final Class<D> existingClass;

        public DependentTask(CodeFactory<D> factory,
                String existingSource,
                Class<D> existingClass) {
            this.factory = Objects.requireNonNull(factory);
            this.existingSource = Objects.requireNonNull(existingSource);
            this.existingClass = Objects.requireNonNull(existingClass);
        }

        public CodeFactory<D> getFactory() {
            return factory;
        }
        
        public String getExistingSource() {
            return existingSource;
        }

        public Class<D> getExistingClass() {
            return existingClass;
        }
        
    }

    public static class DependentResult<D extends CodeDelegate> {
        
        private final CodeContext<D> context;
        private final Class<D> existing;

        public DependentResult(CodeContext<D> context,
                Class<D> existing) {
            this.context = Objects.requireNonNull(context);
            this.existing = Objects.requireNonNull(existing);
        }

        public CodeContext<D> getContext() {
            return context;
        }

        public Class<? extends D> getExisting() {
            return existing;
        }

    }

}
