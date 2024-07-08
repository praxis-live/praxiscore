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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;
import org.praxislive.core.services.LogBuilder;

/**
 * A CodeFactory wraps configuration and task creation for creating code
 * components and contexts for a given delegate base type.
 *
 * @param <D> base delegate type
 */
public class CodeFactory<D extends CodeDelegate> {

    /**
     * Key for use in control info properties with the name of the base delegate
     * type (class) as the value.
     */
    public static final String BASE_CLASS_KEY = "base-class";

    /**
     * Key for use in control info properties with the base imports that should
     * be available in source code. The value should be a PArray of import
     * lines, each containing a full import declaration.
     */
    public static final String BASE_IMPORTS_KEY = "base-imports";

    private final Class<D> baseClass;
    private final List<String> baseImports;
    private final ComponentType type;
    private final String template;
    private final Class<? extends D> defaultDelegateClass;
    private final Lookup lookup;
    private final Supplier<? extends CodeComponent<D>> componentCreator;
    private final BiFunction<CodeFactory.Task<D>, D, CodeContext<D>> contextCreator;

    private CodeFactory(Base<D> base, ComponentType type, Class<? extends D> defaultCls, String template) {
        this.baseClass = base.baseClass;
        this.baseImports = base.baseImports;
        this.componentCreator = base.componentCreator;
        this.contextCreator = base.contextCreator;
        this.lookup = Lookup.of(base.lookup, base);
        this.type = Objects.requireNonNull(type);
        this.template = Objects.requireNonNull(template);
        this.defaultDelegateClass = defaultCls;
    }

    /**
     * Get the component type.
     *
     * @return component type
     */
    public final ComponentType componentType() {
        return type;
    }

    /**
     * The source template corresponding to the default delegate class.
     *
     * @return source template
     */
    public final String sourceTemplate() {
        return template;
    }

    /**
     * Query the default delegate class.
     *
     * @return default delegate class
     */
    public final Optional<Class<? extends D>> defaultDelegateClass() {
        return Optional.ofNullable(defaultDelegateClass);
    }

    /**
     * Query the base delegate class. This is the superclass for the source
     * template and any derived code body.
     *
     * @return base delegate class
     */
    public final Class<D> baseClass() {
        return baseClass;
    }

    /**
     * Query the base imports to be automatically added to the source body.
     *
     * @return base imports
     */
    public final List<String> baseImports() {
        return baseImports;
    }

    /**
     * Create a task for constructing a context or component from a delegate
     * class.
     *
     * @return code factory task
     */
    public Task<D> task() {
        return new Task<>(this);
    }

    /**
     * Access the lookup associated with this code factory.
     *
     * @return lookup
     */
    public Lookup lookup() {
        return lookup;
    }

    /**
     * Create a component {@link CodeFactory.Base} for the given base delegate
     * class, from which can be created individual CodeFactory instances. The
     * base class and default imports will be used to wrap user sources passed
     * across to the compiler. The context creator function is used to wrap the
     * compiled delegate in a {@link CodeContext}, and will usually correspond
     * to <code>(task, delegate) -> new XXXCodeContext(new
     * XXXCodeConnector(task, delegate))</code>
     *
     * @param <B> base delegate type
     * @param baseClass base delegate superclass
     * @param baseImports default base imports
     * @param contextCreator create context for delegate
     * @return code factory base
     */
    public static <B extends CodeDelegate> Base<B> base(Class<B> baseClass,
            List<String> baseImports,
            BiFunction<CodeFactory.Task<B>, B, CodeContext<B>> contextCreator) {
        return new Base<>(baseClass, baseImports, CodeComponent::new, contextCreator, Lookup.EMPTY);
    }

    /**
     * Create a container {@link CodeFactory.Base} for the given base delegate
     * class, from which can be created individual CodeFactory instances. The
     * base class and default imports will be used to wrap user sources passed
     * across to the compiler. The context creator function is used to wrap the
     * compiled delegate in a {@link CodeContext}, and will usually correspond
     * to <code>(task, delegate) -> new XXXCodeContext(new
     * XXXCodeConnector(task, delegate))</code>
     *
     * @param <B> base delegate type
     * @param baseClass base delegate superclass
     * @param baseImports default base imports
     * @param contextCreator create context for delegate
     * @return code factory base
     */
    public static <B extends CodeContainerDelegate> Base<B> containerBase(Class<B> baseClass,
            List<String> baseImports,
            BiFunction<CodeFactory.Task<B>, B, CodeContext<B>> contextCreator) {
        return new Base<>(baseClass, baseImports, CodeContainer::new, contextCreator, Lookup.EMPTY);
    }

    /**
     * Create a root component {@link CodeFactory.Base} for the given base
     * delegate class, from which can be created individual CodeFactory
     * instances. The base class and default imports will be used to wrap user
     * sources passed across to the compiler. The context creator function is
     * used to wrap the compiled delegate in a {@link CodeContext}, and will
     * usually correspond to <code>(task, delegate) -> new XXXCodeContext(new
     * XXXCodeConnector(task, delegate))</code>
     *
     * @param <B> base delegate type
     * @param baseClass base delegate superclass
     * @param baseImports default base imports
     * @param contextCreator create context for delegate
     * @return code factory base
     */
    public static <B extends CodeRootDelegate> Base<B> rootBase(Class<B> baseClass,
            List<String> baseImports,
            BiFunction<CodeFactory.Task<B>, B, CodeContext<B>> contextCreator) {
        return new Base<>(baseClass, baseImports, CodeRoot::new, contextCreator, Lookup.EMPTY);
    }

    /**
     * Create a root container {@link CodeFactory.Base} for the given base
     * delegate class, from which can be created individual CodeFactory
     * instances. The base class and default imports will be used to wrap user
     * sources passed across to the compiler. The context creator function is
     * used to wrap the compiled delegate in a {@link CodeContext}, and will
     * usually correspond to <code>(task, delegate) -> new XXXCodeContext(new
     * XXXCodeConnector(task, delegate))</code>
     *
     * @param <B> base delegate type
     * @param baseClass base delegate superclass
     * @param baseImports default base imports
     * @param contextCreator create context for delegate
     * @return code factory base
     */
    public static <B extends CodeRootContainerDelegate> Base<B> rootContainerBase(Class<B> baseClass,
            List<String> baseImports,
            BiFunction<CodeFactory.Task<B>, B, CodeContext<B>> contextCreator) {
        return new Base<>(baseClass, baseImports, CodeRootContainer::new, contextCreator, Lookup.EMPTY);
    }

    /**
     * A task for creating a component or context for a given delegate.
     *
     * @param <D> delegate base type
     */
    public static final class Task<D extends CodeDelegate> {

        private final CodeFactory<D> factory;

        private LogBuilder log;
        private Class<D> previous;

        private Task(CodeFactory<D> factory) {
            this.factory = Objects.requireNonNull(factory);
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
         * Create a CodeComponent for the provided delegate. This calls
         * {@link #createContext(org.praxislive.code.CodeDelegate)} and installs
         * the context on a new instance of CodeComponent.
         *
         * @param delegate delegate to create component for
         * @return code component
         */
        public CodeComponent<D> createComponent(D delegate) {
            CodeComponent<D> cmp = factory.componentCreator.get();
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
            return factory.contextCreator.apply(this, delegate);
        }

        /**
         * Get the log for reporting messages during context creation.
         *
         * @return log builder
         */
        public LogBuilder getLog() {
            return log;
        }

        /**
         * Get the previous delegate class installed on the component. May be
         * null.
         *
         * @return previous delegate class
         */
        public Class<D> getPrevious() {
            return previous;
        }

        /**
         * Get access to the CodeFactory this task was created for.
         *
         * @return code factory
         */
        public CodeFactory<D> getFactory() {
            return factory;
        }

    }

    /**
     * Base code factory for a given base delegate class. Encompasses shared
     * configuration, component and context creation. Create specific
     * CodeFactory instances with the create methods. See
     * {@link #base(java.lang.Class, java.util.List, java.util.function.BiFunction)}
     * and
     * {@link #containerBase(java.lang.Class, java.util.List, java.util.function.BiFunction)}
     *
     * @param <B> base delegate type
     */
    public static final class Base<B extends CodeDelegate> {

        private final Class<B> baseClass;
        private final List<String> baseImports;
        private final Supplier<? extends CodeComponent<B>> componentCreator;
        private final BiFunction<CodeFactory.Task<B>, B, CodeContext<B>> contextCreator;
        private final Lookup lookup;

        Base(Class<B> baseClass,
                List<String> baseImports,
                Supplier<? extends CodeComponent<B>> componentCreator,
                BiFunction<CodeFactory.Task<B>, B, CodeContext<B>> contextCreator,
                Lookup lookup) {
            this.baseClass = Objects.requireNonNull(baseClass);
            this.baseImports = List.copyOf(baseImports);
            this.componentCreator = Objects.requireNonNull(componentCreator);
            this.contextCreator = Objects.requireNonNull(contextCreator);
            this.lookup = Objects.requireNonNull(lookup);
        }

        /**
         * Query the base delegate class that all CodeFactory instances will
         * create subclasses of.
         *
         * @return base delegate class
         */
        public final Class<B> baseClass() {
            return baseClass;
        }

        /**
         * Query the base imports that all CodeFactory instance will add to the
         * source body.
         *
         * @return base imports
         */
        public final List<String> baseImports() {
            return baseImports;
        }

        /**
         * Create a CodeFactory with the given component type and default
         * source, without a precompiled delegate class.
         *
         * @param type component type
         * @param defaultSource default source code
         * @return code factory
         */
        public CodeFactory<B> create(String type, String defaultSource) {
            return create(ComponentType.of(type), defaultSource);
        }

        /**
         * Create a CodeFactory with the given component type and default
         * source, without a precompiled delegate class.
         *
         * @param type component type
         * @param defaultSource default source code
         * @return code factory
         */
        public CodeFactory<B> create(ComponentType type, String defaultSource) {
            return create(type, null, defaultSource);
        }

        /**
         * Create a CodeFactory with the given component type, default
         * precompiled delegate class, and source class body corresponding to
         * the compiled delegate.
         *
         * @param type component type as String, passed to
         * {@link ComponentType#of(java.lang.String)}
         * @param defaultDelegate default delegate class
         * @param defaultSource default source class body
         * @return code factory
         */
        public CodeFactory<B> create(String type, Class<? extends B> defaultDelegate, String defaultSource) {
            return create(ComponentType.of(type), defaultDelegate, defaultSource);
        }

        /**
         * Create a CodeFactory with the given component type, default
         * precompiled delegate class, and source class body corresponding to
         * the compiled delegate.
         *
         * @param type component type
         * @param defaultDelegate default delegate class
         * @param defaultSource default source class body
         * @return code factory
         */
        public CodeFactory<B> create(ComponentType type, Class<? extends B> defaultDelegate, String defaultSource) {
            return new CodeFactory<>(this, type, defaultDelegate, defaultSource);
        }

    }

}
