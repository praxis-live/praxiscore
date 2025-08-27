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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Value;

/**
 * A BindingContext will normally have one Binding for each bound address. The
 * Binding may have more than one BindingAdaptor attached to it.
 */
public abstract class Binding {

    /**
     * Rates for perdiodic syncing. Except for {@code None}, how these values
     * translate to a sync period in milliseconds is governed by the
     * {@link BindingContext} implementation.
     */
    public static enum SyncRate {

        /**
         * No periodic sync.
         */
        None,
        /**
         * Sync at a low periodic rate.
         */
        Low,
        /**
         * Sync at a medium periodic rate.
         */
        Medium,
        /**
         * Sync at a high periodic rate.
         */
        High;
    }

    /**
     * Get the ControlInfo of the bound Control, if available.
     *
     * @return Optional of ControlInfo
     */
    public abstract Optional<ControlInfo> getControlInfo();

    /**
     * Get the most recent synced values, if the Control is a property.
     *
     * @return synced values
     */
    public abstract List<Value> getValues();

    /**
     * Method called by adaptors to send a call to the bound control.
     *
     * @param adaptor sending adaptor
     * @param args argument list
     */
    protected abstract void send(Adaptor adaptor, List<Value> args);

    /**
     * Method called by adaptors on configuration changes such as activity or
     * sync rate to allow bindings to recalculate their configuration.
     *
     * @param adaptor changed adaptor
     */
    protected abstract void updateAdaptorConfiguration(Adaptor adaptor);

    /**
     * Method for Binding implementations to connect to an adaptor.
     *
     * @param adaptor adaptor to connect
     */
    protected void bind(Adaptor adaptor) {
        adaptor.setBinding(this);
    }

    /**
     * Method for Binding implementations to disconnect from an adaptor.
     *
     * @param adaptor adaptor to disconnect
     */
    protected void unbind(Adaptor adaptor) {
        adaptor.setBinding(null);
    }

    /**
     * Abstract type for binding to a Control.
     */
    public static abstract class Adaptor {

        private Binding binding;
        private SyncRate syncRate = SyncRate.None;
        private boolean active;

        private void setBinding(Binding binding) {
            if (this.binding != null && binding != null) {
                throw new IllegalStateException("Binding adaptor already connected");
            }
            this.binding = binding;
            updateBindingConfiguration();
        }

        /**
         * Get the Binding this adaptor is attached to. The binding provides
         * access to latest values and the ControlInfo.
         *
         * @return binding
         */
        public final Binding getBinding() {
            return binding;
        }

        /**
         * The current SyncRate.
         *
         * @return syncrate
         */
        public final SyncRate getSyncRate() {
            return syncRate;
        }

        /**
         * Whether this Adaptor is currently active.
         *
         * @return active
         */
        public final boolean isActive() {
            return active;
        }

        /**
         * Set whether this Adaptor is currently active. By default an Adaptor
         * is inactive. The Binding will not sync unless at least one attached
         * Adaptor is active and has a sync rate above None.
         *
         * @param active
         */
        public final void setActive(boolean active) {
            if (active != this.active) {
                this.active = active;
                if (binding != null) {
                    binding.updateAdaptorConfiguration(this);
                }
            }
        }

        /**
         * Set the SyncRate of the Adaptor. By default an Adaptor has a sync
         * rate of None. The Binding will not sync unless at least one attached
         * Adaptor is active and has a sync rate above None. The highest active
         * sync rate will be used by the binding.
         *
         * @param syncRate
         */
        public final void setSyncRate(SyncRate syncRate) {
            if (syncRate != this.syncRate) {
                this.syncRate = syncRate;
                if (binding != null) {
                    binding.updateAdaptorConfiguration(this);
                }
            }
        }

        /**
         * Send the provided values to the Control. Other attached Adaptors will
         * be immediately updated.
         *
         * @param args
         */
        protected final void send(List<Value> args) {
            if (this.binding != null) {
                binding.send(this, args);
            }
        }

        /**
         * Whether the Adaptor is currently actively updating and sending
         * values, eg. as a response to user input. The Binding implementation
         * will normally send quiet calls in such cases as the values are
         * expected to be superseded before a reply is received.
         *
         * @return value currently being adjusted
         */
        protected boolean getValueIsAdjusting() {
            return false;
        }

        /**
         * An optional hook for adaptors to access the returned response from a
         * call to send. This will only be called on the adaptor that initiated
         * the call.
         *
         * @param args returned values
         */
        protected void onResponse(List<Value> args) {
        }

        /**
         * An optional hook for adaptors to access any error response from a
         * call to send. This will only be called on the adaptor that initiated
         * the call.
         *
         * @param args error values
         */
        protected void onError(List<Value> args) {
        }

        /**
         * Optional hook called when the Binding configuration has changed. Eg.
         * new ControlInfo available.
         */
        protected void updateBindingConfiguration() {
        }

        /**
         * Optional hook called whenever values may have been updated, by a sync
         * call or another Adaptor. Checking whether the values have actually
         * changed is left up to the adaptor implementation, as adaptors may
         * have different requirements.
         */
        protected void update() {
        }

    }

    /**
     * An adaptor implementation for syncing to properties, as defined by
     * {@link ControlInfo.Type#Property} or
     * {@link ControlInfo.Type#ReadOnlyProperty}.
     * <p>
     * This class takes advantage of the fact that property controls only
     * support a single value argument. It also replaces hooks with optional
     * handlers.
     */
    public static final class PropertyAdaptor extends Adaptor {

        private Value value;
        private ControlInfo info;
        private Consumer<Value> onChangeHandler;
        private Consumer<PropertyAdaptor> onConfigChangeHandler;
        private Consumer<PropertyAdaptor> onSyncHandler;
        private Predicate<PropertyAdaptor> adjustingHandler;

        /**
         * Access the property value if available.
         *
         * @return property value
         */
        public Optional<Value> value() {
            return Optional.ofNullable(value);
        }

        /**
         * Access the property info if available. If the adaptor is bound to a
         * non-property control the Optional will be empty.
         *
         * @return property info
         */
        public Optional<ControlInfo> info() {
            return Optional.ofNullable(info);
        }

        /**
         * Send a new value to the bound property. Other attached Adaptors will
         * be immediately updated.
         *
         * @param value new value
         */
        public void send(Value value) {
            send(List.of(value));
        }

        /**
         * Set a handler to be called when the value of the property changes.
         * Only one change handler may be set at a time. A value of {code null}
         * will remove the handler.
         *
         * @param onChangeHandler handler to be called on value change
         * @return this for chaining
         */
        public PropertyAdaptor onChange(Consumer<Value> onChangeHandler) {
            this.onChangeHandler = onChangeHandler;
            return this;
        }

        /**
         * Set a handler to be called when the configuration changes, such as
         * when the {@link #info()} has updated. Only one configuration change
         * handler may be set at a time. A value of {code null} will remove the
         * handler.
         *
         * @param onConfigChangeHandler handler to be called on configuration
         * change
         * @return this for chaining
         */
        public PropertyAdaptor onConfigChange(Consumer<PropertyAdaptor> onConfigChangeHandler) {
            this.onConfigChangeHandler = onConfigChangeHandler;
            return this;
        }

        /**
         * Set a handler to be called whenever the binding has received a
         * successful sync response. The value may not have changed. Only one
         * sync handler may be set at a time. A value of {code null} will remove
         * the handler.
         *
         * @param onSyncHandler handler to be called on sync
         * @return this for chaining
         */
        public PropertyAdaptor onSync(Consumer<PropertyAdaptor> onSyncHandler) {
            this.onSyncHandler = onSyncHandler;
            return this;
        }

        /**
         * Set a handler to be used whenever the adaptor's
         * {@link #getValueIsAdjusting()} hook is called. The handler should
         * return {@code true} if the value is in the process of being
         * continually updated. The Binding implementation will normally send
         * quiet calls and reduce syncing in such cases as the value is expected
         * to be superseded before a reply is received.
         * <p>
         * Only one adjusting handler may be set at a time. A value of {code
         * null} will remove the handler. An adaptor without an adjusting
         * handler will always return {@code false} for the adjustment query.
         *
         * @param adjustingHandler handler for value is adjusting
         * @return this for chaining
         */
        public PropertyAdaptor onCheckAdjusting(Predicate<PropertyAdaptor> adjustingHandler) {
            this.adjustingHandler = adjustingHandler;
            return this;
        }

        @Override
        protected void update() {
            Binding binding = getBinding();
            Value oldValue = value;
            if (info != null && binding != null) {
                List<Value> values = binding.getValues();
                if (values.size() == 1) {
                    value = values.get(0);
                } else {
                    value = null;
                }
            } else {
                value = null;
            }
            if (!Objects.equals(oldValue, value) && onChangeHandler != null && value != null) {
                onChangeHandler.accept(value);
            }
            if (onSyncHandler != null) {
                onSyncHandler.accept(this);
            }
        }

        @Override
        protected boolean getValueIsAdjusting() {
            if (adjustingHandler == null) {
                return false;
            } else {
                return adjustingHandler.test(this);
            }
        }

        @Override
        protected void updateBindingConfiguration() {
            info = Optional.ofNullable(getBinding())
                    .flatMap(Binding::getControlInfo)
                    .filter(i -> i.controlType() == ControlInfo.Type.Property
                    || i.controlType() == ControlInfo.Type.ReadOnlyProperty)
                    .orElse(null);
            if (info == null) {
                value = null;
            }
            if (onConfigChangeHandler != null) {
                onConfigChangeHandler.accept(this);
            }
        }

    }

}
