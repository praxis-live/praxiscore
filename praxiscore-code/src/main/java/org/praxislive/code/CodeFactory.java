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

import java.util.Optional;
import org.praxislive.core.ComponentType;
import org.praxislive.core.services.LogBuilder;

/**
 * A CodeFactory wraps configuration and task creation for creating code
 * components and contexts for a given delegate base type.
 *
 * @param <D> base delegate type
 */
public abstract class CodeFactory<D extends CodeDelegate> {

    private final ComponentType type;
    private final ClassBodyContext<D> cbc;
    private final String template;
    private final Class<? extends D> defaultDelegateClass;

    /**
     * Construct CodeFactory for a type extending the base code delegate type.
     * This constructor allows for a precompiled default delegate class to be
     * provided. This must correspond to the code compiled from wrapping the
     * provided class body template in the provided class body context.
     *
     * @param cbc class body context that will wrap source code
     * @param type the component type
     * @param defaultCls precompiled default delegate
     * @param template code template reflecting default delegate
     */
    protected CodeFactory(
            ClassBodyContext<D> cbc,
            ComponentType type,
            Class<? extends D> defaultCls,
            String template) {
        this.cbc = cbc;
        this.type = type;
        this.defaultDelegateClass = defaultCls;
        this.template = template;
    }

    /**
     * Construct CodeFactory for a type extending the base code delegate type.
     * This constructor is used where the default delegate is compiled from the
     * template at runtime.
     *
     * @param cbc class body context that will wrap source code
     * @param type the component type
     * @param template code template reflecting default delegate
     */
    protected CodeFactory(
            ClassBodyContext<D> cbc,
            ComponentType type,
            String template) {
        this(cbc, type, null, template);
    }

    /**
     * Get the component type.
     *
     * @return component type
     */
    public final ComponentType getComponentType() {
        return type;
    }

    /**
     * Class body context used to create wrapping class for source code.
     *
     * @return class body context
     */
    public final ClassBodyContext<D> getClassBodyContext() {
        return cbc;
    }

    /**
     * The source template corresponding to the default delegate class.
     *
     * @return source template
     */
    public final String getSourceTemplate() {
        return template;
    }

    /**
     * Optional precompiled version of the default delegate class.
     *
     * @return optional precompiled default delegate
     */
    public final Optional<Class<? extends D>> getDefaultDelegateClass() {
        return Optional.ofNullable(defaultDelegateClass);
    }

    /**
     * Create a task for constructing a context or component from a delegate
     * class. This will return a suitable Task subclass that should be
     * configured and used to create a context or component.
     *
     * @return code factory task
     */
    public abstract Task<D> task();

    /**
     * A task for creating a component or context for a given delegate.
     *
     * @param <D> delegate base type
     */
    public static abstract class Task<D extends CodeDelegate> {

        private final CodeFactory<D> factory;

        private LogBuilder log;
        private Class<D> previous;

        /**
         * Construct a task for the given factory.
         *
         * @param factory
         */
        public Task(CodeFactory<D> factory) {
            this.factory = factory;
        }

        /**
         * Attach a log builder to the task.
         *
         * @param log log builder
         * @return this for chaining
         */
        public Task<D> attachLogging(LogBuilder log) {
            this.log = log;
            return this;
        }

        /**
         * Attach the previous iteration of delegate class, if available, that
         * is being replaced. May be null.
         *
         * @param previous previous iteration of delegate class
         * @return this for chaining
         */
        public Task<D> attachPrevious(Class<D> previous) {
            this.previous = previous;
            return this;
        }

        /**
         * Create a CodeComponent for the provided delegate. By default, this
         * calls {@link #createContext(org.praxislive.code.CodeDelegate)} and
         * installs the context on a new instance of CodeComponent.
         *
         * @param delegate delegate to create component for
         * @return code component
         */
        public CodeComponent<D> createComponent(D delegate) {
            CodeComponent<D> cmp = new CodeComponent<>();
            cmp.install(createContext(delegate));
            return cmp;
        }

        /**
         * Create a CodeContext for the provided delegate, for installation in
         * an existing component. By default just calls through to
         * {@link #createCodeContext(org.praxislive.code.CodeDelegate)}.
         *
         * @param delegate delegate to create context for
         * @return code context
         */
        public CodeContext<D> createContext(D delegate) {
            return createCodeContext(delegate);
        }

        /**
         * Get the log for reporting messages during context creation.
         *
         * @return log builder
         */
        protected LogBuilder getLog() {
            return log;
        }

        /**
         * Get the previous delegate class installed on the component. May be
         * null.
         *
         * @return previous delegate class
         */
        protected Class<D> getPrevious() {
            return previous;
        }

        /**
         * Get access to the CodeFactory this task was created for.
         *
         * @return code factory
         */
        protected CodeFactory<D> getFactory() {
            return factory;
        }

        /**
         * Create the code context for the given delegate. A typical
         * implementation is <code>return new XXXCodeContext(new
         * XXXCodeConnector(this, delegate));</code>.
         *
         * @param delegate delegate to create context for
         * @return code context
         */
        protected abstract CodeContext<D> createCodeContext(D delegate);

    }

}
