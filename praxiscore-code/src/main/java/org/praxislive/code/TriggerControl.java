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
 *
 */
package org.praxislive.code;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.praxislive.code.userapi.Inject;
import org.praxislive.code.userapi.T;
import org.praxislive.code.userapi.Trigger;
import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Port;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.PortInfo;
import org.praxislive.core.types.PMap;
import org.praxislive.core.services.LogLevel;

/**
 *
 */
public class TriggerControl extends Trigger implements Control {

    private final static ControlInfo INFO = ControlInfo.createActionInfo(PMap.EMPTY);

    private final Binding binding;
    private CodeContext<?> context;

    TriggerControl(Binding binding) {
        binding = binding == null ? new DefaultBinding() : binding;
        this.binding = binding;
    }

    @Override
    public void call(Call call, PacketRouter router) throws Exception {
        if (call.isRequest()) {
            trigger(call.time());
            if (call.isReplyRequired()) {
                router.route(call.reply());
            }
        }
    }

    @Override
    protected void trigger(long time) {
        context.invoke(time, () -> {
            try {
                binding.trigger(time);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
            super.trigger(time);
        });
    }

    private void attachImpl(CodeContext<?> context, Control previous) {
        this.context = context;
        binding.attach(context);
        if (previous instanceof TriggerControl triggerControl) {
            try {
                boolean val = triggerControl.poll();
                if (val) {
                    binding.trigger(context.getTime());
                }
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
            super.attach(context, triggerControl);
        } else {
            super.attach(context, null);
        }
    }

    public boolean poll() {
        return binding.poll();
    }

    public static abstract class Binding {

        protected void attach(CodeContext<?> delegate) {
            // no op hook
        }

        public abstract void trigger(long time) throws Exception;

        public abstract boolean poll();

        public abstract boolean peek();

    }

    private static class DefaultBinding extends Binding {

        private boolean triggered;

        @Override
        public void trigger(long time) {
            triggered = true;
        }

        @Override
        public boolean poll() {
            if (triggered) {
                triggered = false;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean peek() {
            return triggered;
        }

    }

    private static class BooleanBinding extends Binding {

        private final Field field;
        private CodeDelegate delegate;

        private BooleanBinding(Field field) {
            this.field = field;
        }

        @Override
        protected void attach(CodeContext<?> context) {
            this.delegate = context.getDelegate();
        }

        @Override
        public void trigger(long time) throws Exception {
            field.setBoolean(delegate, true);
        }

        @Override
        public boolean poll() {
            try {
                boolean val = field.getBoolean(delegate);
                if (val) {
                    field.setBoolean(delegate, false);
                }
                return val;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public boolean peek() {
            try {
                return field.getBoolean(delegate);
            } catch (Exception ex) {
                return false;
            }
        }

    }

    private static class MethodBinding extends Binding {

        private final Method method;
        private CodeContext<?> context;
        private boolean triggered;

        private MethodBinding(Method method) {
            this.method = method;
        }

        @Override
        protected void attach(CodeContext<?> context) {
            this.context = context;
        }

        @Override
        public void trigger(long time) throws Exception {
            triggered = true;
            context.invoke(time, method);
            triggered = false;
        }

        @Override
        public boolean poll() {
            return triggered;
        }

        @Override
        public boolean peek() {
            return triggered;
        }

    }

    public static class Descriptor extends ControlDescriptor<Descriptor> {

        private final TriggerControl control;
        private Field triggerField;

        @Deprecated(forRemoval = true)
        public Descriptor(String id, int index, Binding binding) {
            this(id, index, binding, null);
        }

        @Deprecated(forRemoval = true)
        public Descriptor(String id, int index, Binding binding, Field triggerField) {
            super(Descriptor.class, id, Category.Action, index);
            control = new TriggerControl(binding);
            this.triggerField = triggerField;
        }

        private Descriptor(String id, Category category, int index, Binding binding, Field triggerField) {
            super(Descriptor.class, id, category, index);
            control = new TriggerControl(binding);
            this.triggerField = triggerField;
        }

        @Override
        public ControlInfo controlInfo() {
            return category() == Category.Action ? INFO : null;
        }

        @Override
        public void attach(CodeContext<?> context, Descriptor previous) {
            if (previous != null) {
                control.attachImpl(context, previous.control);
            } else {
                control.attachImpl(context, null);
            }
            if (triggerField != null) {
                try {
                    triggerField.set(context.getDelegate(), control);
                } catch (Exception ex) {
                    context.getLog().log(LogLevel.ERROR, ex);
                }
            }
        }

        @Override
        public void onReset() {
            control.clearLinks();
            control.maxIndex(Integer.MAX_VALUE);
        }

        @Override
        public void onStart() {
            control.index(0);
        }

        @Override
        public Control control() {
            return control;
        }

        public PortDescriptor createPortDescriptor() {
            return new PortDescImpl(id(), index(), control);
        }

        public static Descriptor create(CodeConnector<?> connector,
                T ann, Field field) {
            field.setAccessible(true);
            String id = connector.findID(field);
            int index = ann.value();
            Class<?> type = field.getType();
            if (type == boolean.class) {
                return new Descriptor(id, Category.Action, index, new BooleanBinding(field), null);
            } else if (Trigger.class.isAssignableFrom(type)) {
                return new Descriptor(id, Category.Action, index, new DefaultBinding(), field);
            } else {
                return null;
            }
        }

        public static Descriptor create(CodeConnector<?> connector,
                T ann, Method method) {
            method.setAccessible(true);
            if (method.getParameterTypes().length > 0) {
                return null;
            }
            String id = connector.findID(method);
            int index = ann.value();
            return new Descriptor(id, Category.Action, index, new MethodBinding(method), null);
        }

        public static Descriptor create(CodeConnector<?> connector,
                Inject ann, Field field) {
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (Trigger.class.isAssignableFrom(type)) {
                String id = connector.findID(field);
                int index = connector.getSyntheticIndex();
                return new Descriptor(id, Category.Synthetic, index, new DefaultBinding(), field);
            } else {
                return null;
            }
        }

    }

    private static class PortDescImpl extends PortDescriptor<PortDescImpl>
            implements ControlInput.Link {

        private final TriggerControl control;

        private ControlInput port;

        private PortDescImpl(String id, int index, TriggerControl control) {
            super(PortDescImpl.class, id, Category.Action, index);
            this.control = control;
        }

        @Override
        public void attach(CodeContext<?> context, PortDescImpl previous) {
            if (previous != null) {
                port = previous.port;
                port.setLink(this);
            } else {
                port = new ControlInput(this);
            }
        }

        @Override
        public Port port() {
            assert port != null;
            return port;
        }

        @Override
        public PortInfo portInfo() {
            return ControlInput.INFO;
        }

        @Override
        public void receive(long time, double value) {
            try {
                control.trigger(time);
            } catch (Exception ex) {

            }
        }

        @Override
        public void receive(long time, Value value) {
            try {
                control.trigger(time);
            } catch (Exception ex) {

            }
        }

    }

}
