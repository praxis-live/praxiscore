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
package org.praxislive.code;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.OrderedMap;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;

/**
 * A {@link Service} for handling shared code updates and creating dependent
 * {@link CodeContext}. Must be running in the same process as the components
 * due to task and result references. Should make use of a
 * {@link CodeCompilerService} implementation for compiling source code (which
 * does support other processes).
 */
public final class SharedCodeService implements Service {

    /**
     * Control ID of the new shared code control.
     */
    public final static String NEW_SHARED = "new-shared";

    /**
     * ControlInfo of the new shared code control.
     */
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

    /**
     * Task containing new shared code and dependents to be updated, for sending
     * to the SharedCodeService.
     */
    public static class Task {

        private final PMap sources;
        private final Map<ControlAddress, DependentTask<?>> dependents;
        private final LogLevel logLevel;

        /**
         * Create a Task.
         *
         * @param sources new shared code sources
         * @param dependents map of dependent tasks by address
         * @param logLevel logging level
         */
        public Task(PMap sources,
                Map<ControlAddress, DependentTask<?>> dependents,
                LogLevel logLevel) {
            this.sources = Objects.requireNonNull(sources);
            this.dependents = Map.copyOf(dependents);
            this.logLevel = logLevel;
        }

        /**
         * Get the shared code sources.
         *
         * @return sources
         */
        public PMap getSources() {
            return sources;
        }

        /**
         * Get the map of dependents.
         *
         * @return dependents
         */
        public Map<ControlAddress, DependentTask<?>> getDependents() {
            return dependents;
        }

        /**
         * Get the logging level.
         *
         * @return logging level
         */
        public LogLevel getLogLevel() {
            return logLevel;
        }

    }

    /**
     * Result with shared classes, dependent code contexts, and log.
     */
    public static class Result {

        private final ClassLoader sharedClasses;
        private final OrderedMap<ControlAddress, DependentResult<CodeDelegate>> dependents;
        private final LogBuilder log;

        /**
         * Create an empty Result.
         */
        public Result() {
            this.sharedClasses = null;
            this.dependents = OrderedMap.of();
            this.log = new LogBuilder(LogLevel.ERROR);
        }

        /**
         * Create a Result.
         * <p>
         * The dependents map will be copied and sorted by depth.
         *
         * @param sharedClasses new shared classes classloader
         * @param dependents map of dependent results
         * @param log log
         */
        public Result(ClassLoader sharedClasses,
                Map<ControlAddress, DependentResult<CodeDelegate>> dependents,
                LogBuilder log) {
            this.sharedClasses = Objects.requireNonNull(sharedClasses);
            this.dependents = orderDependents(dependents);
            this.log = Objects.requireNonNull(log);
        }

        /**
         * Get the shared classes classloader.
         *
         * @return shared classes
         */
        public ClassLoader getSharedClasses() {
            return sharedClasses;
        }

        /**
         * Get the map of dependent results.
         * <p>
         * The map is an immutable ordered map, sorted by depth from root, then
         * by natural order. Dependents should be processed in the iteration
         * order of the map.
         *
         * @return dependents
         */
        public Map<ControlAddress, DependentResult<CodeDelegate>> getDependents() {
            return dependents;
        }

        /**
         * Get the log
         *
         * @return log
         */
        public LogBuilder getLog() {
            return log;
        }

        @SuppressWarnings("unchecked")
        private OrderedMap<ControlAddress, DependentResult<CodeDelegate>>
                orderDependents(Map<ControlAddress, DependentResult<CodeDelegate>> dependents) {
            if (dependents.isEmpty()) {
                return OrderedMap.of();
            } else {
                return OrderedMap.ofEntries(
                        dependents.entrySet().stream().sorted(
                                Comparator.<Map.Entry<ControlAddress, ?>>comparingInt(
                                        e -> e.getKey().component().depth())
                                        .thenComparing(e -> e.getKey().component().toString()
                                        )).toArray(Map.Entry[]::new));
            }
        }

    }

    /**
     * A dependent task for recompiling a {@link CodeDelegate} against the new
     * shared code classes.
     *
     * @param <D> base delegate type
     */
    public static class DependentTask<D extends CodeDelegate> {

        private final CodeFactory<D> factory;
        private final String existingSource;
        private final Class<D> existingClass;

        /**
         * Create a DependentTask.
         *
         * @param factory code factory for delegate
         * @param existingSource existing source to recompile
         * @param existingClass existing class
         */
        public DependentTask(CodeFactory<D> factory,
                String existingSource,
                Class<D> existingClass) {
            this.factory = Objects.requireNonNull(factory);
            this.existingSource = Objects.requireNonNull(existingSource);
            this.existingClass = Objects.requireNonNull(existingClass);
        }

        /**
         * Get code factory.
         *
         * @return code factory
         */
        public CodeFactory<D> getFactory() {
            return factory;
        }

        /**
         * Get the existing source.
         *
         * @return existing source
         */
        public String getExistingSource() {
            return existingSource;
        }

        /**
         * Get the existing class.
         *
         * @return existing class
         */
        public Class<D> getExistingClass() {
            return existingClass;
        }

    }

    /**
     * A dependent result with new code context linked to new shared code
     * classes.
     *
     * @param <D> base delegate type
     */
    public static class DependentResult<D extends CodeDelegate> {

        private final CodeContext<D> context;
        private final Class<D> existing;

        /**
         * Create a DependentResult.
         *
         * @param context code context
         * @param existing the existing (previous) class
         */
        public DependentResult(CodeContext<D> context,
                Class<D> existing) {
            this.context = Objects.requireNonNull(context);
            this.existing = Objects.requireNonNull(existing);
        }

        /**
         * Get the code context.
         *
         * @return code context
         */
        public CodeContext<D> getContext() {
            return context;
        }

        /**
         * Get the existing (previous) class. Used for validation.
         *
         * @return existing (previous) class
         */
        public Class<? extends D> getExisting() {
            return existing;
        }

    }

}
