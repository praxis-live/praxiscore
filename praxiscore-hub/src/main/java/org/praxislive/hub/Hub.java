/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2023 Neil C Smith.
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
package org.praxislive.hub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.core.Clock;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.Packet;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.Services;
import org.praxislive.script.impl.ScriptServiceImpl;

/**
 * Support for configuring and running a {@link RootHub}, along with the
 * {@link Root}s within it.
 * <p>
 * This class doesn't implement either RootHub or Root directly. It uses a core
 * root, usually an instance or subclass of {@link BasicCoreRoot}, to manage
 * other system and user roots.
 * <p>
 * Use {@link #builder()} to configure and create an instance of this class.
 */
public final class Hub {

    public final static String SYS_PREFIX = "_sys_";
    public final static String CORE_PREFIX = SYS_PREFIX + "core_";
    public final static String EXT_PREFIX = SYS_PREFIX + "ext_";

    private final ConcurrentMap<String, Root.Controller> roots;
    private final ConcurrentMap<Class<? extends Service>, List<ComponentAddress>> services;
    private final Root core;
    private final Lookup lookup;
    private final RootHubImpl rootHub;
    private final List<String> rootIDs;

    private Root.Controller coreController;
    long startTime;

    private Hub(Builder builder) {
        CoreRootFactory coreFactory = builder.coreRootFactory;
        List<Root> exts = new ArrayList<>();
        extractExtensions(builder, exts);
        core = coreFactory.createCoreRoot(new Accessor(), exts);
        List<Object> lookupContent = new ArrayList<>();
        lookupContent.add(new ServicesImpl());
        lookupContent.add(new ComponentRegistryImpl());
        lookupContent.addAll(builder.lookupContent);
        Lookup lkp = Lookup.of(lookupContent.toArray());
        lkp = coreFactory.extendLookup(lkp);
        lookup = lkp;
        roots = new ConcurrentHashMap<>();
        services = new ConcurrentHashMap<>();
        rootHub = new RootHubImpl();
        rootIDs = new CopyOnWriteArrayList<>();
    }

    private void extractExtensions(Builder builder, List<Root> exts) {
        exts.add(new DefaultComponentFactoryService());
        exts.add(new ScriptServiceImpl());
        exts.add(new DefaultTaskService());
        exts.addAll(builder.extensions);
    }

    /**
     * Start the hub. This will start the core root, which will in turn start
     * other services. A hub cannot be started more than once and is not
     * reusable.
     *
     * @throws Exception if start fails or the hub has already been started
     */
    public synchronized void start() throws Exception {
        if (coreController != null) {
            throw new IllegalStateException();
        }
        startTime = System.nanoTime();
        String coreID = CORE_PREFIX + Integer.toHexString(core.hashCode());
        coreController = core.initialize(coreID, rootHub);
        roots.put(coreID, coreController);
        coreController.start(Lookup.EMPTY);
    }

    /**
     * Signal the hub to shutdown. This will signal the core root to terminate,
     * which will terminate all other roots.
     */
    public void shutdown() {
        coreController.shutdown();
    }

    /**
     * Wait for the core root and hub to terminate.
     *
     * @throws InterruptedException if interrupted
     */
    public void await() throws InterruptedException {
        while (true) {
            try {
                await(1, TimeUnit.MINUTES);
                return;
            } catch (TimeoutException ex) {
                // loop again
            }
        }
    }

    /**
     * Wait for the given time period for the core root and hub to terminate.
     *
     * @param time time to wait
     * @param unit unit of time to wait
     * @throws InterruptedException if interrupted
     * @throws TimeoutException if the hub has not terminated in the given time
     */
    public void await(long time, TimeUnit unit) throws InterruptedException, TimeoutException {
        try {
            coreController.awaitTermination(time, unit);
        } catch (ExecutionException ex) {
            return;
        }
    }

    /**
     * Query whether the hub and core root are running.
     *
     * @return true if active
     */
    public boolean isAlive() {
        return coreController.isAlive();
    }

    /**
     * Return an exit value for the hub. This may be used as the exit value for
     * the hub process. The default value is 0.
     *
     * @return exit value
     */
    public int exitValue() {
        if (core instanceof BasicCoreRoot) {
            return ((BasicCoreRoot) core).exitValue();
        } else {
            return 0;
        }
    }

    private boolean registerRootController(String id, Root.Controller controller) {
        if (id == null || controller == null) {
            throw new NullPointerException();
        }
        Root.Controller existing = roots.putIfAbsent(id, controller);
        if (existing == null) {
            rootIDs.add(id);
            return true;
        } else {
            return false;
        }
    }

    private Root.Controller unregisterRootController(String id) {
        rootIDs.remove(id);
        return roots.remove(id);
    }

    private Root.Controller getRootController(String id) {
        return roots.get(id);
    }

    private String[] getRootIDs() {
        return rootIDs.toArray(String[]::new);
    }

    private RootHub getRootHub() {
        return rootHub;
    }

    private void registerService(Class<? extends Service> service,
            ComponentAddress provider) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(provider);
        services.merge(service, List.of(provider), (existingValues, newValue) -> {
            var list = new ArrayList<ComponentAddress>(newValue);
            list.addAll(existingValues);
            return list;
        });
    }

    private Set<Class<? extends Service>> getServices() {
        return Collections.unmodifiableSet(services.keySet());
    }

    /**
     * Create a {@link Hub.Builder}.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private class RootHubImpl implements RootHub, Clock {

        @Override
        public boolean dispatch(Packet packet) {
            Root.Controller dest = roots.get(packet.rootID());
            try {
                if (dest != null) {
                    return dest.submitPacket(packet);
                } else {
                    return coreController.submitPacket(packet);
                }
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Lookup getLookup() {
            return lookup;
        }

        @Override
        public Clock getClock() {
            return this;
        }

        @Override
        public long getTime() {
            return System.nanoTime() - startTime;
        }

    }

    private class ServicesImpl implements Services {

        @Override
        public Optional<ComponentAddress> locate(Class<? extends Service> service) {
            var list = services.get(service);
            if (list == null || list.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(list.get(0));
            }
        }

        @Override
        public Stream<ComponentAddress> locateAll(Class<? extends Service> service) {
            return services.getOrDefault(service, List.of()).stream();
        }

    }

    private class ComponentRegistryImpl implements org.praxislive.core.ComponentRegistry {

        private final Result result;

        private ComponentRegistryImpl() {
            result = ComponentRegistry.getInstance().createRegistryResult();
        }

        @Override
        public Result query() {
            return result;
        }

    }

    /**
     * Provides access to control of the RootHub. An instance of this class is
     * passed to the core root factory to provide private access to the RootHub
     * for the core root implementation.
     */
    public final class Accessor {

        private Accessor() {

        }

        /**
         * Register the root controller under the provided id. This method does
         * not start the root.
         *
         * @param id root id
         * @param controller root controller
         * @return true on success
         */
        public boolean registerRootController(String id, Root.Controller controller) {
            return Hub.this.registerRootController(id, controller);
        }

        /**
         * Unregister the root controller with the provided id. The registered
         * controller is returned, if it exists. This method does not terminate
         * the root.
         *
         * @param id root id
         * @return controller or null
         */
        public Root.Controller unregisterRootController(String id) {
            return Hub.this.unregisterRootController(id);
        }

        /**
         * Get the root controller with the provided id, if one is registered.
         *
         * @param id root id
         * @return controller or null
         */
        public Root.Controller getRootController(String id) {
            return Hub.this.getRootController(id);
        }

        /**
         * Get a list of the registered root IDs.
         *
         * @return registered root IDs
         */
        public String[] getRootIDs() {
            return Hub.this.getRootIDs();
        }

        /**
         * Register a service provider.
         *
         * @param service implemented service
         * @param provider service address
         */
        public void registerService(Class<? extends Service> service,
                ComponentAddress provider) {
            Hub.this.registerService(service, provider);
        }

//        public Set<Class<? extends Service>> getServices() {
//            return Hub.this.getServices();
//        }
        /**
         * Get the {@link RootHub} implementation.
         *
         * @return root hub
         */
        public RootHub getRootHub() {
            return Hub.this.getRootHub();
        }

    }

    /**
     * An interface for creating custom core root implementations.
     */
    public interface CoreRootFactory {

        /**
         * Create a core root implementation with the provided RootHub accessor
         * and extensions. The return type of this method will usually be a
         * subclass of {@link BasicCoreRoot}.
         *
         * @param accessor private access to control the RootHub
         * @param extensions extensions to install
         * @return core root implementation
         */
        public abstract Root createCoreRoot(Accessor accessor, List<Root> extensions);

        /**
         * Provide the option for the factory to extend or alter the hub lookup.
         * This should usually be done by passing the provided lookup in as
         * parent to
         * {@link Lookup#of(org.praxislive.core.Lookup, java.lang.Object...)}.
         * The default implementation returns the provided lookup unchanged.
         *
         * @param lookup existing lookup
         * @return extended lookup
         */
        public default Lookup extendLookup(Lookup lookup) {
            return lookup;
        }

    }

    /**
     * A builder for Hubs.
     */
    public static class Builder {

        private final List<Root> extensions;
        private final List<Object> lookupContent;
        private CoreRootFactory coreRootFactory;

        private Builder() {
            extensions = new ArrayList<>();
            lookupContent = new ArrayList<>();
            coreRootFactory = BasicCoreRoot.factory();
            extensions.addAll(findDefaultExtensions());
        }

        private List<Root> findDefaultExtensions() {
            return Lookup.SYSTEM.findAll(RootHub.ExtensionProvider.class)
                    .flatMap(ep -> ep.getExtensions().stream())
                    .collect(Collectors.toList());
        }

        /**
         * Configure the {@link CoreRootFactory} to use to build the core root.
         * The default configuration will create a {@link BasicCoreRoot}.
         *
         * @param coreRootFactory factory for core root
         * @return this
         */
        public Builder setCoreRootFactory(CoreRootFactory coreRootFactory) {
            this.coreRootFactory = Objects.requireNonNull(coreRootFactory);
            return this;
        }

        /**
         * Add a root to install as an extension. If the extension implements
         * {@link RootHub.ServiceProvider} then it will be automatically
         * registered as a provider of those services. Order of extensions is
         * important - later providers will supersede earlier ones.
         *
         * @param extension extension to add
         * @return this
         */
        public Builder addExtension(Root extension) {
            extensions.add(Objects.requireNonNull(extension));
            return this;
        }

        /**
         * Extend the hub lookup with the provided object.
         *
         * @param obj object to add
         * @return this
         */
        public Builder extendLookup(Object obj) {
            lookupContent.add(Objects.requireNonNull(obj));
            return this;
        }

        /**
         * Build the hub.
         *
         * @return hub
         */
        public Hub build() {

            Hub hub = new Hub(this);
            return hub;
        }

    }

}
