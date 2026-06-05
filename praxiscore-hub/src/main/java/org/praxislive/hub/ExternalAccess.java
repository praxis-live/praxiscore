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
package org.praxislive.hub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.praxislive.base.AbstractRoot;
import org.praxislive.base.Binding;
import org.praxislive.base.BindingContext;
import org.praxislive.base.BindingContextControl;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.Value;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PString;
import org.praxislive.core.HubProxy;
import org.praxislive.core.protocols.ComponentProtocol;

final class ExternalAccess extends AbstractRoot {

    private static final String SCRIPT_CONTROL_ID = "_ext-eval";

    private final Hub hub;
    private final Map<Integer, CompletableFuture<List<Value>>> evalPending;

    private Map<String, Control> controls;
    private BindingContextControl bindings;

    ExternalAccess(Hub hub) {
        this.hub = hub;
        this.evalPending = new HashMap<>();
    }

    @Override
    public Controller initialize(String id, RootHub hub) {
        AbstractRoot.Controller ctrl = super.initialize(id, hub);
        bindings = new BindingContextControl(ControlAddress.of(getAddress(), "_bindings"),
                getExecutionContext(),
                getRouter());
        controls = Map.of(
                SCRIPT_CONTROL_ID, this::processEvalResponse,
                "_bindings", bindings);
        return ctrl;
    }

    @Override
    protected void activating() {
        setRunning();
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        try {
            controls.get(call.to().controlID()).call(call, router);
        } catch (Exception ex) {
            router.route(call.error(PError.of(ex)));
        }
    }

    @Override
    protected void terminating() {
        evalPending.forEach((i, f) -> f.completeExceptionally(new IllegalStateException()));
        evalPending.clear();
    }

    Future<List<Value>> eval(String script) {
        CompletableFuture<List<Value>> future = new CompletableFuture<>();
        invokeLater(() -> processEvalRequest(script, future));
        return future;
    }

    HubProxy createHubProxy() {
        return new HubProxyImpl(this);
    }

    private void processEvalRequest(String script, CompletableFuture<List<Value>> future) {
        try {
            ControlAddress to = ControlAddress.of(findService(ScriptService.class),
                    ScriptService.EVAL);
            ControlAddress from = ControlAddress.of(getAddress(), SCRIPT_CONTROL_ID);
            Call request = Call.create(to, from,
                    getExecutionContext().getTime(), PString.of(script));
            getRouter().route(request);
            evalPending.put(request.matchID(), future);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }
    }

    private void processEvalResponse(Call call, PacketRouter router) {
        if (call.isRequest()) {
            throw new UnsupportedOperationException();
        }
        CompletableFuture<List<Value>> future = evalPending.remove(call.matchID());
        if (future != null) {
            if (call.isReply()) {
                future.complete(call.args());
            } else if (call.isError()) {
                Exception ex = Call.findError(call)
                        .<Exception>map(PError.WrapperException::new)
                        .orElseGet(Exception::new);
                future.completeExceptionally(ex);
            }
        }
    }

    private static class HubProxyImpl implements HubProxy {

        private final ExternalAccess root;
        private final ConcurrentMap<ComponentAddress, ComponentProxy> components;

        private HubProxyImpl(ExternalAccess root) {
            this.root = root;
            components = new ConcurrentHashMap<>();
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
            invokeLater(cmp::validateImpl);
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
            return root.hub.roots();
        }

        @Override
        public HubProxy unsyncAll() {
            components.forEach((ad, cmp) -> cmp.unsync());
            return this;
        }

        @Override
        public HubProxy clear() {
            Map<ComponentAddress, ComponentProxy> cmps = Map.copyOf(components);
            components.clear();
            invokeLater(() -> {
                cmps.forEach((ad, cmp) -> cmp.disposeImpl());
            });
            return this;
        }

        @Override
        public void dispose() {
            clear();
        }

        @Override
        public Future<List<Value>> eval(String script) {
            return root.eval(script);
        }

        private void invokeLater(Runnable task) {
            root.invokeLater(task);
        }

        private void removeComponent(ComponentProxy cmp) {
            components.remove(cmp.address(), cmp);
        }

        private BindingContext bindings() {
            return root.bindings;
        }

    }

    private static class ComponentProxy implements HubProxy.Component {

        private final HubProxyImpl hub;
        private final ComponentAddress address;
        private final Binding.PropertyAdaptor infoBinding;
        private final AtomicReference<ComponentInfo> info;
        private final AtomicReference<Set<String>> sync;
        private final Map<String, Binding.PropertyAdaptor> bindings;
        private final ConcurrentMap<String, Value> values;
        private final AtomicReference<CompletableFuture<Optional<ComponentInfo>>> pendingInfo;

        private ComponentProxy(HubProxyImpl hubProxy, ComponentAddress address) {
            this.hub = hubProxy;
            this.address = address;
            infoBinding = new Binding.PropertyAdaptor();
            info = new AtomicReference<>();
            pendingInfo = new AtomicReference<>();
            sync = new AtomicReference<>();
            bindings = new ConcurrentHashMap<>();
            values = new ConcurrentHashMap<>();
            infoBinding.setSyncRate(Binding.SyncRate.Low);
            infoBinding.onChange(v -> {
                updateInfo(ComponentInfo.from(v).orElse(null));
            }).onSync(a -> {
                if (sync.get() == null) {
                    a.setActive(false);
                }
                CompletableFuture<Optional<ComponentInfo>> futureInfo
                        = pendingInfo.getAndSet(null);
                if (futureInfo != null) {
                    futureInfo.complete(info());
                }
            }).onSyncError(a -> {
                updateInfo(null);
            });
            hubProxy.invokeLater(() -> {
                hubProxy.bindings().bind(
                        ControlAddress.of(address, ComponentProtocol.INFO),
                        infoBinding);
                infoBinding.setActive(true);
            });
        }

        @Override
        public HubProxy hub() {
            return hub;
        }

        @Override
        public HubProxy.Component set(String id, Value value) {
            values.put(id, value);
            hub.invokeLater(() -> {
                Binding.PropertyAdaptor binding = bindings.get(id);
                if (binding != null) {
                    binding.send(value);
                } else {
                    values.remove(id, value);
                }
            });
            return this;
        }

        @Override
        public Optional<Value> get(String id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public ComponentAddress address() {
            return address;
        }

        @Override
        public Optional<ComponentInfo> info() {
            return Optional.ofNullable(info.get());
        }

        @Override
        public void dispose() {
            hub.removeComponent(this);
            hub.invokeLater(this::disposeImpl);
        }

        @Override
        public HubProxy.Component sync() {
            sync.set(Set.of());
            hub.invokeLater(this::checkSyncing);
            return this;
        }

        @Override
        public HubProxy.Component sync(Set<String> properties) {
            sync.set(Set.copyOf(properties));
            hub.invokeLater(this::checkSyncing);
            return this;
        }

        @Override
        public HubProxy.Component unsync() {
            sync.set(null);
            hub.invokeLater(this::checkSyncing);
            return this;
        }

        @Override
        public Future<Optional<ComponentInfo>> validate() {
            CompletableFuture<Optional<ComponentInfo>> future
                    = pendingInfo.updateAndGet(f -> {
                        return f == null ? new CompletableFuture<>() : f;
                    });
            hub.invokeLater(this::validateImpl);
            return future;
        }

        @Override
        public String toString() {
            return "ComponentProxy : " + address();
        }

        private void checkSyncing() {
            Set<String> syncSet = sync.get();
            infoBinding.setActive(syncSet != null);
            bindings.forEach((id, binding) -> {
                if (syncSet != null && (syncSet.isEmpty() || syncSet.contains(id))) {
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
                values.remove(id);
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
                    binding.onChange(value -> values.put(id, value));
                    hub.bindings().bind(ControlAddress.of(address, id), binding);
                    bindings.put(id, binding);
                }
            });
            checkSyncing();
            this.info.set(info);
            CompletableFuture<Optional<ComponentInfo>> futureInfo
                    = pendingInfo.getAndSet(null);
            if (futureInfo != null) {
                futureInfo.complete(Optional.ofNullable(info));
            }
        }

        private void validateImpl() {
            infoBinding.setActive(true);
        }

    }

    private static class RootProxy extends ComponentProxy implements HubProxy.Root {

        private RootProxy(HubProxyImpl hub, ComponentAddress address) {
            super(hub, address);
        }

        @Override
        public String toString() {
            return "RootProxy : " + address();
        }

    }

}
