/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2025 Neil C Smith.
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
package org.praxislive.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.ExecutionContext;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;

/**
 * An implementation of BindingContext based around a single Control. A
 * container just needs to provide an instance as a (hidden) control, and make
 * it available via Lookup.
 */
public class BindingContextControl implements Control, BindingContext {

    private final static System.Logger LOG
            = System.getLogger(BindingContextControl.class.getName());
    private static final long LOW_SYNC_DELAY = TimeUnit.MILLISECONDS.toNanos(1000);
    private static final long MED_SYNC_DELAY = TimeUnit.MILLISECONDS.toNanos(200);
    private static final long HIGH_SYNC_DELAY = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long INVOKE_TIMEOUT = TimeUnit.MILLISECONDS.toNanos(5000);
    private static final long QUIET_TIMEOUT = TimeUnit.MILLISECONDS.toNanos(200);

    private final ExecutionContext context;
    private final PacketRouter router;
    private final ControlAddress controlAddress;
    private final Map<ControlAddress, BindingImpl> bindings;
    private final Set<BindingImpl> syncing;

    /**
     * Create a BindingContextControl.
     *
     * @param controlAddress address of the control for sending and receiving
     * all messages
     * @param context the execution context (required for sync clock)
     * @param router the router for sending all messages
     */
    public BindingContextControl(
            ControlAddress controlAddress,
            ExecutionContext context,
            PacketRouter router
    ) {
        this.controlAddress = Objects.requireNonNull(controlAddress);
        this.context = Objects.requireNonNull(context);
        this.router = Objects.requireNonNull(router);
        bindings = new LinkedHashMap<>();
        syncing = new CopyOnWriteArraySet<>();
        context.addClockListener(this::tick);
    }

    @Override
    public void bind(ControlAddress address, Binding.Adaptor adaptor) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(adaptor);
        BindingImpl binding = bindings.get(address);
        if (binding == null) {
            binding = new BindingImpl(address);
            bindings.put(address, binding);
        }
        binding.addAdaptor(adaptor);
    }

    @Override
    public void unbind(ControlAddress address, Binding.Adaptor adaptor) {
        BindingImpl binding = bindings.get(address);
        if (binding != null) {
            binding.removeAdaptor(adaptor);
            if (binding.isEmpty()) {
                bindings.remove(address);
                binding.dispose();
            }
        }
    }

    @Override
    public void call(Call call, PacketRouter router) throws Exception {
        if (call.isReply() || call.isError()) {
            if (call.from().controlID().equals(ComponentProtocol.INFO)) {
                ComponentAddress infoOf = call.from().component();
                bindings.forEach((a, b) -> {
                    if (infoOf.equals(a.component())) {
                        b.process(call);
                    }
                });
            } else {
                var binding = bindings.get(call.from());
                if (binding != null) {
                    binding.process(call);
                }
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private void tick(ExecutionContext source) {
        long time = source.getTime();
        syncing.forEach(b -> b.processSync(time));
    }

    private class BindingImpl extends Binding {

        private final List<Binding.Adaptor> adaptors;
        private final ControlAddress boundAddress;
        private final InfoAdaptor infoAdaptor;

        private ControlInfo bindingInfo;
        private long nextSyncTime;
        private long syncPeriod;

        private boolean isSyncable;
        private boolean isWritableProperty;
        private Call activeCall;
        private Adaptor activeAdaptor;
        private List<Value> values;

        private BindingImpl(ControlAddress boundAddress) {
            adaptors = new ArrayList<>();
            this.boundAddress = boundAddress;
            values = Collections.emptyList();
            if (ComponentProtocol.INFO.equals(boundAddress.controlID())) {
                infoAdaptor = null;
                bindingInfo = ComponentProtocol.INFO_INFO;
                isSyncable = true;
            } else {
                ControlAddress infoAddress = boundAddress.component()
                        .control(ComponentProtocol.INFO);
                infoAdaptor = new InfoAdaptor();
                infoAdaptor.setSyncRate(SyncRate.Low);
                BindingContextControl.this.bind(infoAddress, infoAdaptor);
            }
        }

        @Override
        public Optional<ControlInfo> getControlInfo() {
            return Optional.ofNullable(bindingInfo);
        }

        @Override
        public List<Value> getValues() {
            return values;
        }

        @Override
        public String toString() {
            return "BindingImpl : " + boundAddress;
        }

        @Override
        protected void send(Adaptor adaptor, List<Value> args) {
            Call call;
            if (adaptor.getValueIsAdjusting()) {
                call = Call.createQuiet(boundAddress, controlAddress,
                        context.getTime(), args);
            } else {
                call = Call.create(boundAddress, controlAddress,
                        context.getTime(), args);
            }
            router.route(call);
            activeCall = call;
            activeAdaptor = adaptor;
            if (isWritableProperty) {
                List<Value> oldValues = values;
                values = call.args();
                if (!Objects.equals(oldValues, values)) {
                    adaptors.forEach(ad -> {
                        if (ad != adaptor) {
                            ad.update();
                        }
                    });
                }
            }
        }

        @Override
        protected void updateAdaptorConfiguration(Adaptor adaptor) {
            updateSyncConfiguration();
        }

        private void addAdaptor(Adaptor adaptor) {
            if (adaptor == null) {
                throw new NullPointerException();
            }
            if (adaptors.contains(adaptor)) {
                return;
            }
            adaptors.add(adaptor);
            bind(adaptor);
            updateAdaptorConfiguration(adaptor); // duplicate functionality
        }

        private void removeAdaptor(Adaptor adaptor) {
            if (adaptors.remove(adaptor)) {
                unbind(adaptor);
            }
            updateSyncConfiguration();
        }

        private void dispose() {
            if (infoAdaptor != null) {
                ControlAddress infoAddress = boundAddress.component()
                        .control(ComponentProtocol.INFO);
                BindingContextControl.this.unbind(infoAddress, infoAdaptor);
            }
        }

        private boolean isEmpty() {
            return adaptors.isEmpty();
        }

        private void updateSyncConfiguration() {
            LOG.log(System.Logger.Level.DEBUG, "Updating sync configuration on {0}", boundAddress);
            boolean active = false;
            SyncRate highRate = SyncRate.None;
            for (Adaptor a : adaptors) {
                if (a.isActive()) {
                    active = true;
                    SyncRate aRate = a.getSyncRate();
                    if (aRate.compareTo(highRate) > 0) {
                        highRate = aRate;
                    }
                }
            }
            if (infoAdaptor != null) {
                infoAdaptor.setActive(active);
            }
            if (!isSyncable || !active || highRate == SyncRate.None) {
                syncPeriod = 0;
                syncing.remove(this);
            } else {
                syncPeriod = delayForRate(highRate);
                nextSyncTime = 0;
                syncing.add(this);
            }
        }

        private long delayForRate(SyncRate rate) {
            return switch (rate) {
                case Low ->
                    LOW_SYNC_DELAY;
                case Medium ->
                    MED_SYNC_DELAY;
                case High ->
                    HIGH_SYNC_DELAY;
                case None ->
                    0;
            };
        }

        private void updateInfo(ControlInfo info) {
            if (Objects.equals(bindingInfo, info)) {
                return;
            }
            if (info != null) {
                ControlInfo.Type type = info.controlType();
                isSyncable = (type == ControlInfo.Type.Property || type == ControlInfo.Type.ReadOnlyProperty);
                isWritableProperty = (type == ControlInfo.Type.Property);
            } else {
                isSyncable = false;
                isWritableProperty = false;
            }
            bindingInfo = info;
            for (Adaptor a : adaptors) {
                a.updateBindingConfiguration();
            }
            updateSyncConfiguration();
        }

        private void process(Call call) {
            if (call.isReply()) {
                processResponse(call);
            } else if (call.isError()) {
                processError(call);
            }
        }

        private void processResponse(Call call) {
            if (activeCall != null && call.matchID() == activeCall.matchID()) {
                if (activeAdaptor != null) {
                    activeAdaptor.onResponse(call.args());
                    activeAdaptor = null;
                }
                if (isSyncable) {
                    List<Value> oldValues = values;
                    values = call.args();
                    if (!Objects.equals(oldValues, values)) {
                        adaptors.forEach(Adaptor::update);
                    }
                }
                activeCall = null;
            }
        }

        private void processError(Call call) {
            if (activeCall != null && call.matchID() == activeCall.matchID()) {
                if (activeAdaptor != null) {
                    activeAdaptor.onError(call.args());
                    activeAdaptor = null;
                } else {
                    LOG.log(System.Logger.Level.DEBUG, "Error on sync call - {0}", call.from());
                }
                activeCall = null;
            }
        }

        private void processSync(long time) {
            if (nextSyncTime == 0 || nextSyncTime - time < 0) {
                nextSyncTime = time + syncPeriod;
            }

            if (activeCall != null) {
                if (activeCall.isReplyRequired()) {
                    if ((time - activeCall.time()) < INVOKE_TIMEOUT) {
                        return;
                    }
                } else {
                    if ((time - activeCall.time()) < QUIET_TIMEOUT) {
                        return;
                    }
                }
            }
            if (isSyncable) {
                Call call = Call.create(boundAddress, controlAddress, time);
                router.route(call);
                activeCall = call;
                activeAdaptor = null;
            }

        }

        private class InfoAdaptor extends Binding.Adaptor {

            @Override
            protected void update() {
                List<Value> args = getBinding().getValues();
                if (!args.isEmpty()) {
                    updateInfo(ComponentInfo.from(args.get(0))
                            .map(cmp -> cmp.controlInfo(boundAddress.controlID()))
                            .orElse(null));
                }
            }

        }

    }
}
