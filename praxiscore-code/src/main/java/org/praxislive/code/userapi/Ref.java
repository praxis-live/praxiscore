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
package org.praxislive.code.userapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A generic object holder for safely passing references between different
 * iterations of code.
 * <p>
 * Use with {@link Inject} eg. {@code @Inject Ref<List<String>> strings;} Then
 * in init() / setup() use {@code strings.init(ArrayList::new);}
 * <p>
 * <strong>Most methods will throw an Exception if init() has not been called
 * with a Supplier function.</strong>
 * <p>
 * The default dispose handler checks if the referenced value is
 * {@link AutoCloseable} and automatically closes it.
 *
 * @param <T> type of reference
 */
public abstract class Ref<T> {

    private T value;
    private boolean inited;
    private Consumer<? super T> onResetHandler;
    private Consumer<? super T> onDisposeHandler;
    private List<Runnable> resetTasks;

    /**
     * Initialize the reference, calling the supplier function if a value is
     * needed.
     * <p>
     * The supplier may return null, although this is not recommended.
     *
     * @param supplier
     * @return this
     */
    public Ref<T> init(Supplier<? extends T> supplier) {
        if (!inited && value == null) {
            value = supplier.get();
        }
        inited = true;
        return this;
    }

    /**
     * Return the value. The Ref must be initialized by calling
     * {@link #init(java.util.function.Supplier) init} first.
     *
     * @return value
     */
    public T get() {
        checkInit();
        return value;
    }

    /**
     * Disposes the value and clears initialization. Before using the Ref again
     * it must be re-initialized.
     *
     * @return this
     */
    public Ref<T> clear() {
        dispose();
        return this;
    }

    /**
     * Pass the value to the provided Consumer function. The value must be
     * initialized first.
     *
     * @param consumer
     * @return this
     */
    public Ref<T> apply(Consumer<? super T> consumer) {
        checkInit();
        consumer.accept(value);
        return this;
    }

    /**
     * Transform the value using the supplied function. Either an existing or
     * new value may be returned. If a new value is returned, the value will be
     * replaced and any {@link #onReset(java.util.function.Consumer) onReset}
     * and {@link #onDispose(java.util.function.Consumer) onDispose} handlers
     * called.
     *
     * @param function
     * @return this
     */
    public Ref<T> compute(Function<? super T, ? extends T> function) {
        checkInit();
        T v = function.apply(value);
        if (v != value) {
            disposeValue();
            value = v;
        }
        return this;
    }

    /**
     * Run an intensive or time consuming function as a background task to
     * update the value. The function should be self-contained and try not to
     * capture or access any state from the component. Use the key argument to
     * pass in data required to compute the new value - ideally not the current
     * contents of the Ref unless it is immutable or thread-safe.
     *
     * @param <K> type of key value
     * @param key a key value used by the function to calculate the new value
     * @param function an intensive or time-consuming function
     * @return this
     */
    public <K> Ref<T> asyncCompute(K key, Function<K, ? extends T> function) {
        throw new UnsupportedOperationException();
    }

    /**
     * Bind something (usually a callback / listener) to the reference,
     * providing for automatic removal on reset or disposal. This also allows
     * for the easy removal of listeners that are lambdas or method references,
     * without the need to keep a reference to them.
     * <p>
     * The binder and unbinder arguments will usually be method references for
     * the add and remove listener methods. The bindee will usually be the
     * listener, often as a lambda or method reference.
     *
     * @param <V> the type of the value to bind to the reference, usually a
     * callback / listener
     * @param binder the function to bind the value, usually a method reference
     * on T that accepts a value V
     * @param unbinder the function to unbind the value, usually a method
     * reference on T that accepts a value V
     * @param bindee the value, usually a lambda or method reference
     * @return this
     */
    public <V> Ref<T> bind(BiConsumer<? super T, V> binder,
            BiConsumer<? super T, V> unbinder,
            V bindee) {
        checkInit();
        try {
            binder.accept(value, bindee);
            if (resetTasks == null) {
                resetTasks = new ArrayList<>();
            }
            resetTasks.add(()
                    -> unbinder.accept(value, bindee)
            );
        } catch (Exception ex) {
            log(ex);
        }
        return this;
    }

    /**
     * Pass the value to the provided Consumer function <strong>if one
     * exists.</strong>
     * <p>
     * Unlike {@link #apply(java.util.function.Consumer) apply} this may be
     * safely called prior to initialization.
     *
     * @param consumer
     * @return this
     */
    public Ref<T> ifPresent(Consumer<? super T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
        return this;
    }

    /**
     * Provide a function to run on the value whenever the Ref is reset - eg.
     * when the Ref is passed from one iteration of code to the next.
     *
     * @param onResetHandler
     * @return this
     */
    public Ref<T> onReset(Consumer<? super T> onResetHandler) {
        this.onResetHandler = onResetHandler;
        return this;
    }

    /**
     * Provide a function to run on the value whenever the value is being
     * disposed of, either because the Ref has been removed from the code, the
     * root is being stopped, or {@link #clear() clear} has been explicitly
     * called.
     *
     * @param onDisposeHandler
     * @return this
     */
    public Ref<T> onDispose(Consumer<? super T> onDisposeHandler) {
        this.onDisposeHandler = onDisposeHandler;
        return this;
    }

    protected void dispose() {
        disposeValue();
        value = null;
        onResetHandler = null;
        onDisposeHandler = null;
        inited = false;
    }

    protected void reset() {
        runResetTasks();
        if (value != null && onResetHandler != null) {
            try {
                onResetHandler.accept(value);
            } catch (Exception ex) {
                log(ex);
            }
        }
        onResetHandler = null;
        onDisposeHandler = null;
        inited = false;
    }

    protected abstract void log(Exception ex);

    private void checkInit() {
        if (!inited) {
            throw new IllegalStateException("Ref is not inited");
        }
    }

    private void runResetTasks() {
        if (resetTasks != null) {
            resetTasks.forEach(r -> {
                try {
                    r.run();
                } catch (Exception ex) {
                    log(ex);
                }
            });
            resetTasks = null;
        }
    }

    private void disposeValue() {
        runResetTasks();
        if (value != null && onResetHandler != null) {
            try {
                onResetHandler.accept(value);
            } catch (Exception ex) {
                log(ex);
            }
        }
        if (value != null && onDisposeHandler != null) {
            try {
                onDisposeHandler.accept(value);
            } catch (Exception ex) {
                log(ex);
            }
        } else if (value instanceof AutoCloseable) {
            try {
                ((AutoCloseable) value).close();
            } catch (Exception ex) {
                log(ex);
            }
        }
    }

    /**
     * Providers initialize Ref instances so that the underlying value can be
     * used directly as the injected field type.
     * <p>
     * A default provider covers some basic types, or a custom handler type may
     * be set in {@link Inject#handler()}. A custom handler must have a public,
     * no-arg constructor which registers initializers.
     */
    public static abstract class Provider {

        private final Map<Class<?>, Initializer<?>> initializers;

        /**
         * Constructor. Subclasses should register initializers during
         * construction.
         */
        public Provider() {
            this.initializers = new LinkedHashMap<>();
        }

        /**
         * Acquire the initializer for the given type.
         *
         * @param <T> generic type
         * @param type type
         * @return initializer for type, or null if not supported
         */
        @SuppressWarnings("unchecked")
        public final <T> Initializer<T> initializerFor(Class<T> type) {
            return (Initializer<T>) initializers.get(type);
        }

        /**
         * The set of types supported by this handler.
         *
         * @return supported types
         */
        public final Set<Class<?>> supportedTypes() {
            return Set.copyOf(initializers.keySet());
        }

        /**
         * Check if this handler supports the provided type.
         *
         * @param type type
         * @return supported
         */
        public final boolean isSupportedType(Class<?> type) {
            return initializers.containsKey(type);
        }

        /**
         * Register an initializer to configure a Ref for the given type. The
         * initializer should initialize the Ref, as well as add dispose or
         * other handlers as required.
         *
         * @param <T> generic type
         * @param type type
         * @param initializer initializer for type
         */
        protected final <T> void register(Class<T> type, Initializer<? extends T> initializer) {
            initializers.put(type, initializer);
        }

        /**
         * Register a constructor for the given type. This is a shortcut method
         * to register an initializer, equivalent to registering
         * <code>r -> r.init(constructor)</code>. The reference will use the
         * default disposer that handles {@link AutoCloseable} - see
         * documentation of {@link Ref}.
         *
         * @param <T> generic type
         * @param type type
         * @param constructor constructor for type
         */
        protected final <T> void provide(Class<T> type, Supplier<? extends T> constructor) {
            register(type, r -> r.init(constructor));
        }

        /**
         * Register a constructor and disposer for the given type. This is a
         * shortcut method to register an initializer, equivalent to registering
         * <code>r -> r.init(constructor).onDispose(disposer)</code>.
         *
         * @param <T> generic type
         * @param type type
         * @param constructor constructor for type
         * @param disposer disposer for type
         */
        protected final <T> void provide(Class<T> type,
                Supplier<? extends T> constructor,
                Consumer<? super T> disposer) {
            register(type, r -> r.init(constructor).onDispose(disposer));
        }

        /**
         * Get the default provider. The default provider supports fields of
         * type List, Map and Set, via ArrayList, LinkedHashMap and
         * LinkedHashSet respectively.
         *
         * @return default provider
         */
        public static Provider getDefault() {
            return DefaultHandler.INSTANCE;
        }

    }

    /**
     * A functional type for initializing a Ref, used by Providers.
     *
     * @param <T> generic type of Ref
     */
    @FunctionalInterface
    public static interface Initializer<T> {

        /**
         * Initialize the provided Ref, as well as add handlers for disposal,
         * etc. if required.
         *
         * @param ref Ref to initialize
         */
        public void initialize(Ref<T> ref);

    }

    private static final class DefaultHandler extends Provider {

        private final static DefaultHandler INSTANCE = new DefaultHandler();

        public DefaultHandler() {
            provide(List.class, ArrayList::new);
            provide(Map.class, LinkedHashMap::new);
            provide(Set.class, LinkedHashSet::new);
        }

    }

}
