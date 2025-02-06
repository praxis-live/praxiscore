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
package org.praxislive.code;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import org.praxislive.code.userapi.AuxOut;
import org.praxislive.code.userapi.Out;
import org.praxislive.code.userapi.Ref;
import org.praxislive.core.services.LogLevel;

/**
 *
 */
class RefImpl<T> extends Ref<T> {

    private final Type type;

    private CodeContext<?> context;
    private Descriptor desc;

    private RefImpl(Type type) {
        this.type = type;
    }

    private void attach(CodeContext<?> context, Descriptor desc) {
        this.context = context;
        this.desc = desc;
    }

    @Override
    protected void reset() {
        super.reset();
    }

    @Override
    protected void dispose() {
        super.dispose();
    }

    @Override
    protected void log(Exception ex) {
        context.getLog().log(LogLevel.ERROR, ex);
    }

    @Override
    protected void valueChanged(T currentValue, T previousValue) {
        if (desc.publishTo != null && desc.publishBus != null) {
            desc.publishBus.notifySubscribers(desc.publishTo, this);
        }
        if (desc.outputPort != null) {
            desc.outputPort.fireChange();
        }
    }

    @SuppressWarnings("unchecked")
    void updateFromPublisher(RefImpl<?> publisher) {
        if (type.equals(publisher.type)) {
            T val = (T) publisher.orElse(null);
            if (val != null) {
                init(() -> val).compute(o -> val);
            } else {
                clear();
            }
        } else {
            clear();
        }
    }

    static class Descriptor extends ReferenceDescriptor<Descriptor> {

        private final Field refField;
        private final Type refType;
        private final String subscribeTo;
        private final String publishTo;

        private RefImpl<?> ref;
        private RefBus subscribeBus;
        private RefBus publishBus;
        private RefPort.OutputDescriptor outputPort;

        private Descriptor(CodeConnector<?> connector, String id, Field refField, Type refType) {
            super(Descriptor.class, id);
            this.refField = refField;
            this.refType = refType;
            Ref.Publish pub = refField.getAnnotation(Ref.Publish.class);
            if (pub != null) {
                var name = pub.name();
                publishTo = name.isBlank() ? refType.toString() : name;
            } else {
                publishTo = null;
            }
            Ref.Subscribe sub = refField.getAnnotation(Ref.Subscribe.class);
            if (sub != null) {
                var name = sub.name();
                subscribeTo = name.isBlank() ? refType.toString() : name;
            } else {
                subscribeTo = null;
            }
        }

        @Override
        public void attach(CodeContext<?> context, Descriptor previous) {
            if (previous != null) {
                if (isCompatible(previous)) {
                    ref = previous.ref;
                    previous.removePublishing();
                    previous.removeSubscription();
                    if (previous.subscribeTo != null && subscribeTo == null) {
                        ref.clear();
                    }
                    previous.ref = null;
                } else {
                    previous.dispose();
                }
            }

            if (ref == null) {
                ref = new RefImpl<>(refType);
            }

            ref.attach(context, this);

            try {
                refField.set(context.getDelegate(), ref);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }

        }

        private boolean isCompatible(Descriptor other) {
            return refType.equals(other.refType);
        }

        @Override
        public void onInit() {
            checkPublishing();
            checkSubscription();
        }
        
        @Override
        public void onReset() {
            if (ref != null) {
                ref.reset();
            }
        }

        @Override
        public void onStop() {
            if (ref != null) {
                ref.dispose();
                removePublishing();
                removeSubscription();
            }
        }

        @Override
        public void dispose() {
            if (ref != null) {
                removePublishing();
                removeSubscription();
                ref.dispose();
                ref = null;
            }
        }

        // Output port support
        Type getRefType() {
            return refType;
        }

        RefImpl<?> getRef() {
            return ref;
        }

        RefPort.OutputDescriptor getPortDescriptor() {
            return outputPort;
        }

        private void checkPublishing() {
            if (publishTo != null && publishBus == null) {
                publishBus = findPublishBus(ref.context.getComponent());
                if (publishBus != null) {
                    publishBus.publish(publishTo, ref);
                }
            }
        }

        private void removePublishing() {
            if (publishBus != null && publishTo != null && ref != null) {
                publishBus.unpublish(publishTo, ref);
                publishBus = null;
            }
        }

        private void checkSubscription() {
            if (subscribeTo != null && subscribeBus == null) {
                subscribeBus = findSubscribeBus(ref.context.getComponent());
                if (subscribeBus != null) {
                    subscribeBus.subscribe(subscribeTo, ref);
                }
            }
        }

        private void removeSubscription() {
            if (subscribeBus != null && subscribeTo != null && ref != null) {
                subscribeBus.unsubscribe(subscribeTo, ref);
                subscribeBus = null;
            }
        }

        private RefBus findPublishBus(CodeComponent<?> cmp) {
            if (cmp instanceof CodeContainer) {
                return ((CodeContainer<?>) cmp).getRefBus();
            } else if (cmp instanceof CodeRootContainer) {
                return ((CodeRootContainer<?>) cmp).getRefBus();
            } else {
                return null;
            }
        }

        private RefBus findSubscribeBus(CodeComponent<?> cmp) {
            var parent = cmp.getParent();
            if (parent instanceof CodeComponent) {
                return findPublishBus((CodeComponent<?>) parent);
            } else {
                return null;
            }
        }

        static Descriptor create(CodeConnector<?> connector, Field field) {
            if (Ref.class.equals(field.getType())) {
                var type = TypeUtils.extractTypeParameter(field, Ref.class);
                if (type != null) {
                    field.setAccessible(true);
                    return new Descriptor(connector, field.getName(), field, type);
                }
            }
            return null;
        }

        static Descriptor create(CodeConnector<?> connector, Field field, Out out) {
            var desc = create(connector, field);
            if (desc != null) {
                desc.outputPort = RefPort.OutputDescriptor.create(connector.findID(field), out, desc);
                return desc;
            } else {
                return null;
            }
        }

        static Descriptor create(CodeConnector<?> connector, Field field, AuxOut auxOut) {
            var desc = create(connector, field);
            if (desc != null) {
                desc.outputPort = RefPort.OutputDescriptor.create(connector.findID(field), auxOut, desc);
                return desc;
            } else {
                return null;
            }
        }

    }

}
