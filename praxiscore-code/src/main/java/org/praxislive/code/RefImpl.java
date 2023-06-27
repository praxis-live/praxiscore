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
package org.praxislive.code;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;
import org.praxislive.code.userapi.Ref;
import org.praxislive.core.services.LogLevel;

/**
 *
 */
class RefImpl<T> extends Ref<T> {

    private final Type type;
    private final Class<?> rawType;

    private CodeContext<?> context;
    private Descriptor desc;

    private RefImpl(Type type) {
        this.type = type;
        if (type instanceof ParameterizedType) {
            rawType = (Class<?>) ((ParameterizedType) type).getRawType();
        } else if (type instanceof Class) {
            rawType = (Class<?>) type;
        } else {
            rawType = Object.class;
        }
    }

    private void attach(CodeContext<?> context, Descriptor desc) {
        this.context = context;
        this.desc = desc;
    }

    @Override
    @Deprecated
    public <K> Ref<T> asyncCompute(K key, Function<K, ? extends T> function) {
        var async = context.async(key, k -> (T) function.apply(k));
        setAsync(async);
        return this;
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
    protected void valueChanged(T newValue, T oldValue) {
        if (desc.publishTo != null && desc.publishBus != null) {
            desc.publishBus.notifySubscribers(desc.publishTo, this);
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

    static class Descriptor extends ReferenceDescriptor {

        private final Field refField;
        private final Type refType;
        private final String subscribeTo;
        private final String publishTo;

        private RefImpl<?> ref;
        private RefBus subscribeBus;
        private RefBus publishBus;

        private Descriptor(CodeConnector<?> connector, String id, Field refField, Type refType) {
            super(id);
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
        public void attach(CodeContext<?> context, ReferenceDescriptor previous) {
            if (previous instanceof RefImpl.Descriptor) {
                RefImpl.Descriptor pd = (RefImpl.Descriptor) previous;
                if (isCompatible(pd)) {
                    ref = pd.ref;
                    pd.removePublishing();
                    pd.removeSubscription();
                    if (pd.subscribeTo != null && subscribeTo == null) {
                        ref.clear();
                    }
                    pd.ref = null;
                } else {
                    pd.dispose();
                }
            } else if (previous != null) {
                previous.dispose();
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

            checkPublishing();
            checkSubscription();

        }

        private boolean isCompatible(Descriptor other) {
            return refType.equals(other.refType);
        }

        @Override
        public void reset(boolean full) {
            if (ref != null) {
                if (full) {
                    ref.dispose();
                    removePublishing();
                    removeSubscription();
                } else {
                    ref.reset();
                }
                checkPublishing();
                checkSubscription();
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
                var type = extractRefType(field);
                if (type != null) {
                    field.setAccessible(true);
                    return new Descriptor(connector, field.getName(), field, type);
                }
            }
            return null;
        }

        private static Type extractRefType(Field field) {
            var type = field.getGenericType();
            if (type instanceof ParameterizedType) {
                var parType = (ParameterizedType) type;
                var types = parType.getActualTypeArguments();
                if (types.length == 1) {
                    return types[0];
                }
            }
            return null;
        }

    }

}
