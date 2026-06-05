/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.praxislive.base.Binding;
import org.praxislive.base.BindingContext;
import org.praxislive.code.userapi.Async;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.HubProxy;
import org.praxislive.core.ThreadContext;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PArray;

/**
 *
 */
class HubProxyImpl implements HubProxy {

    private final Map<ComponentAddress, ComponentProxy> components;
    private final Binding.PropertyAdaptor rootsBinding;

    private CodeContext<?> context;
    private ThreadContext threadContext;
    private BindingContext bindingContext;
    private ControlAddress rootsAddress;

    private HubProxyImpl() {
        components = new HashMap<>();
        rootsBinding = new Binding.PropertyAdaptor();
        rootsBinding.setActive(true);
        rootsBinding.setSyncRate(Binding.SyncRate.Low);
    }

    @Override
    public Component component(ComponentAddress address) {
        ComponentProxy cmp = components.computeIfAbsent(address,
                ad -> {
                    if (ad.depth() == 1) {
                        return new RootProxy(this, ad);
                    } else {
                        return new ComponentProxy(this, ad);
                    }
                });
        return cmp;
    }

    @Override
    public Root root(String id) {
        return (Root) component(ComponentAddress.of("/" + id));
    }

    @Override
    public Set<Component> proxies() {
        return Set.copyOf(components.values());
    }

    @Override
    public List<String> roots() {
        return rootsBinding.value()
                .flatMap(PArray::from)
                .map(a -> a.asListOf(String.class))
                .orElseGet(() -> List.of());
    }

    @Override
    public HubProxy unsyncAll() {
        components.forEach((ad, cmp) -> cmp.unsync());
        return this;
    }

    @Override
    public HubProxy clear() {
        components.forEach((ad, cmp) -> cmp.disposeImpl());
        components.clear();
        return this;
    }

    @Override
    public void dispose() {
        clear();
    }

    @Override
    public Future<List<Value>> eval(String script) {
        return new ThreadAwareFuture<>(
                Async.toCompletableFuture(
                        context.getDelegate()
                                .ask(ScriptService.class, ScriptService.EVAL, script))
                        .thenApply(call -> call.args()),
                threadContext);
    }

    private void attach(CodeContext<?> context) {
        this.context = context;
        this.threadContext = context.getLookup().find(ThreadContext.class).orElse(null);
        this.bindingContext = context.getLookup().find(BindingContext.class).orElse(null);
        this.rootsAddress = context.getLookup().find(Services.class)
                .flatMap(srvs -> srvs.locate(RootManagerService.class))
                .map(ad -> ControlAddress.of(ad, RootManagerService.ROOTS))
                .orElse(null);
    }

    private BindingContext bindings() {
        return bindingContext;
    }

    private void onDescriptorDispose() {
        onDescriptorReset();
        context = null;
        bindingContext = null;
        threadContext = null;
        rootsAddress = null;
    }

    private void onDescriptorInit() {
        if (bindingContext != null && rootsAddress != null) {
            bindingContext.bind(rootsAddress, rootsBinding);
        }
    }

    private void onDescriptorReset() {
        clear();
        if (bindingContext != null && rootsAddress != null) {
            bindingContext.unbind(rootsAddress, rootsBinding);
        }
    }

    private void removeComponent(ComponentProxy cmp) {
        components.remove(cmp.address(), cmp);
    }

    private ThreadContext threadContext() {
        return threadContext;
    }

    private static class ComponentProxy implements HubProxy.Component {

        private final HubProxyImpl hub;
        private final ComponentAddress address;
        private final Binding.PropertyAdaptor infoBinding;
        private final Map<String, Binding.PropertyAdaptor> bindings;

        private ComponentInfo info;
        private Set<String> sync;
        private CompletableFuture<Optional<ComponentInfo>> pendingInfo;

        private ComponentProxy(HubProxyImpl hub, ComponentAddress address) {
            this.hub = hub;
            this.address = address;
            infoBinding = new Binding.PropertyAdaptor();
            bindings = new HashMap<>();
            infoBinding.setSyncRate(Binding.SyncRate.Low);
            infoBinding.onChange(v -> {
                updateInfo(ComponentInfo.from(v).orElse(null));
            }).onSync(a -> {
                if (sync == null) {
                    a.setActive(false);
                }
                if (pendingInfo != null) {
                    pendingInfo.complete(info());
                    pendingInfo = null;
                }
            }).onSyncError(a -> {
                updateInfo(null);
            });
            hub.bindings().bind(
                    ControlAddress.of(address, ComponentProtocol.INFO),
                    infoBinding);
            infoBinding.setActive(true);
        }

        @Override
        public HubProxy hub() {
            return hub;
        }

        @Override
        public Component set(String id, Value value) {
            Binding.PropertyAdaptor binding = bindings.get(id);
            if (binding != null) {
                binding.send(value);
            }
            return this;
        }

        @Override
        public Optional<Value> get(String id) {
            return Optional.ofNullable(bindings.get(id))
                    .flatMap(Binding.PropertyAdaptor::value);
        }

        @Override
        public ComponentAddress address() {
            return address;
        }

        @Override
        public Optional<ComponentInfo> info() {
            return Optional.ofNullable(info);
        }

        @Override
        public void dispose() {
            hub.removeComponent(this);
            disposeImpl();
        }

        @Override
        public Component sync() {
            sync = Set.of();
            checkSyncing();
            return this;
        }

        @Override
        public Component sync(Set<String> properties) {
            sync = Set.copyOf(properties);
            checkSyncing();
            return this;
        }

        @Override
        public Component unsync() {
            sync = null;
            checkSyncing();
            return this;
        }

        @Override
        public Future<Optional<ComponentInfo>> validate() {
            if (pendingInfo == null) {
                pendingInfo = new CompletableFuture<>();
            }
            infoBinding.setActive(true);
            return new ThreadAwareFuture<>(pendingInfo, hub.threadContext());
        }

        @Override
        public String toString() {
            return "ComponentProxy : " + address();
        }

        private void checkSyncing() {
            infoBinding.setActive(sync != null);
            bindings.forEach((id, binding) -> {
                if (sync != null && (sync.isEmpty() || sync.contains(id))) {
                    binding.setActive(true);
                } else {
                    binding.setActive(false);
                }
            });
        }

        private void disposeImpl() {
            infoBinding.setActive(false);
            hub.bindings().unbind(
                    ControlAddress.of(address(), ComponentProtocol.INFO),
                    infoBinding);
            updateInfo(null);
        }

        private boolean isProperty(ComponentInfo info, String control) {
            ControlInfo controlInfo = info.controlInfo(control);
            return controlInfo != null
                    && (controlInfo.controlType() == ControlInfo.Type.Property
                    || controlInfo.controlType() == ControlInfo.Type.ReadOnlyProperty);
        }

        private void updateInfo(ComponentInfo info) {
            this.info = info;
            Set<String> props;
            if (info == null) {
                props = Set.of();
            } else {
                props = info.controls().stream()
                        .filter(id -> isProperty(info, id))
                        .collect(Collectors.toSet());
            }
            Set<String> working = new HashSet<>(bindings.keySet());
            working.removeAll(props);
            working.forEach(id -> {
                Binding.PropertyAdaptor removed = bindings.remove(id);
                if (removed != null) {
                    hub.bindings().unbind(ControlAddress.of(address, id), removed);
                }
            });
            working.clear();
            working.addAll(bindings.keySet());
            props.forEach(id -> {
                if (!working.contains(id)) {
                    Binding.PropertyAdaptor binding = new Binding.PropertyAdaptor();
                    binding.setSyncRate(Binding.SyncRate.Medium);
                    hub.bindings().bind(ControlAddress.of(address, id), binding);
                    bindings.put(id, binding);
                }
            });
            checkSyncing();
            if (pendingInfo != null) {
                pendingInfo.complete(Optional.ofNullable(info));
                pendingInfo = null;
            }
        }

    }

    private static class RootProxy extends ComponentProxy implements HubProxy.Root {

        public RootProxy(HubProxyImpl hub, ComponentAddress address) {
            super(hub, address);
        }

        @Override
        public String toString() {
            return "RootProxy : " + address();
        }

    }

    private static class ThreadAwareFuture<T> implements Future<T> {

        private final Future<T> delegate;
        private final ThreadContext threadContext;

        private ThreadAwareFuture(Future<T> delegate, ThreadContext threadContext) {
            this.delegate = delegate;
            this.threadContext = threadContext;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            checkThread();
            return delegate.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            checkThread();
            return delegate.get(timeout, unit);
        }

        private void checkThread() {
            if (threadContext != null && threadContext.isRootThread() && !isDone()) {
                throw new UnsupportedOperationException(
                        "Calling get() on root thread when not done will deadlock");
            }
        }

    }

    static class Descriptor extends ReferenceDescriptor<Descriptor> {

        private final Field hubField;

        private HubProxyImpl hub;

        private Descriptor(CodeConnector<?> connector, String id, Field field) {
            super(Descriptor.class, id);
            this.hubField = field;
        }

        @Override
        public void attach(CodeContext<?> context, Descriptor previous) {
            if (previous != null) {
                hub = previous.hub;
                previous.hub = null;
            }
            if (hub == null) {
                hub = new HubProxyImpl();
            }
            try {
                hubField.set(context.getDelegate(), hub);
                hub.attach(context);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
        }

        @Override
        public void onInit() {
            hub.onDescriptorInit();
        }

        @Override
        public void onReset() {
            hub.onDescriptorReset();
        }

        @Override
        public void dispose() {
            if (hub != null) {
                hub.onDescriptorDispose();
            }
            hub = null;
        }

        static Descriptor create(CodeConnector<?> connector, Field field) {
            if (HubProxy.class.equals(field.getType())) {
                field.setAccessible(true);
                return new Descriptor(connector, field.getName(), field);
            } else {
                return null;
            }
        }

    }

}
