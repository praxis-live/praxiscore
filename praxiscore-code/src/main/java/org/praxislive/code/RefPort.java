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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.praxislive.code.userapi.AuxIn;
import org.praxislive.code.userapi.AuxOut;
import org.praxislive.code.userapi.In;
import org.praxislive.code.userapi.Out;
import org.praxislive.code.userapi.Ref;
import org.praxislive.core.Port;
import org.praxislive.core.PortConnectionException;
import org.praxislive.core.PortInfo;
import org.praxislive.core.PortListener;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PMap;

/**
 * Ports for sharing Ref values.
 *
 * @param <T> type of references
 */
public abstract class RefPort<T> implements Port {

    /**
     * Ref input, linked to {@link Ref.In} implementation.
     *
     * @param <T> type of references
     */
    public static final class Input<T> extends RefPort<T> {

        private final RefInputImpl<T> refInput;
        private final List<Output<T>> connections;
        private final List<PortListener> listeners;

        private java.lang.reflect.Type type;

        private Input(InputDescriptor desc) {
            refInput = new RefInputImpl<>();
            connections = new ArrayList<>();
            listeners = new CopyOnWriteArrayList<>();
            type = desc.type;
        }

        private void reconfigure(InputDescriptor desc) {
            this.type = desc.type;
        }

        @Override
        public void addListener(PortListener listener) {
            listeners.add(Objects.requireNonNull(listener));
        }

        @Override
        public void connect(Port port) throws PortConnectionException {
            if (port instanceof Output) {
                port.connect(this);
            } else {
                throw new PortConnectionException();
            }
        }

        @Override
        public List<? extends Port> connections() {
            return List.copyOf(connections);
        }

        @Override
        public void disconnect(Port port) {
            if (port instanceof Output) {
                port.disconnect(this);
            }
        }

        @Override
        public void disconnectAll() {
            for (var output : connections()) {
                disconnect(output);
            }
        }

        @Override
        public void removeListener(PortListener listener) {
            listeners.remove(listener);
        }

        private void addRefOutputPort(Output<T> port) throws PortConnectionException {
            if (connections.contains(port)) {
                throw new PortConnectionException();
            }
            connections.add(port);
            updateRefImpl();
            listeners.forEach(l -> l.connectionsChanged(this));
        }

        private void removeRefOutputPort(Output<T> port) {
            if (connections.remove(port)) {
                updateRefImpl();
                listeners.forEach(l -> l.connectionsChanged(this));
            }
        }

        @SuppressWarnings("unchecked")
        private void updateRefImpl() {
            List<Ref<T>> refs = new ArrayList<>(connections.size());
            for (var connection : connections) {
                refs.add((Ref<T>) connection.refDesc.getRef());
            }
            refInput.update(refs);
        }

    }

    private static class RefInputImpl<T> extends Ref.Input<T> {

        @Override
        protected void update(List<Ref<T>> refs) {
            super.update(refs);
        }

    }

    static final class InputDescriptor extends PortDescriptor<InputDescriptor> {

        private final Field field;
        private final java.lang.reflect.Type type;
        private final PortInfo info;

        private Input<?> port;

        private InputDescriptor(String id,
                Category category,
                int index,
                Field field,
                java.lang.reflect.Type type) {
            super(InputDescriptor.class, id, category, index);
            this.field = field;
            this.type = type;
            this.info = PortInfo.create(RefPort.class,
                    PortInfo.Direction.IN,
                    PMap.of("category", TypeUtils.portCategory(type)));
        }

        @Override
        public void attach(CodeContext<?> context, InputDescriptor previous) {
            if (previous != null && TypeUtils.equivalent(type, previous.type)) {
                port = previous.port;
                port.reconfigure(this);
            } else {
                if (previous != null) {
                    previous.dispose();
                }
                port = new Input<>(this);
            }
            try {
                field.set(context.getDelegate(), port.refInput);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
        }

        @Override
        public PortInfo portInfo() {
            return info;
        }

        @Override
        public Port port() {
            return port;
        }

        @Override
        public void reset() {
            port.refInput.clearLinks();
        }

        static InputDescriptor create(CodeConnector<?> connector, In ann, Field field) {
            return create(connector, PortDescriptor.Category.In, ann.value(), field);
        }

        static InputDescriptor create(CodeConnector<?> connector, AuxIn ann, Field field) {
            return create(connector, PortDescriptor.Category.AuxIn, ann.value(), field);
        }

        private static InputDescriptor create(CodeConnector<?> connector,
                PortDescriptor.Category category, int index, Field field) {
            if (Ref.Input.class.equals(field.getType())) {
                java.lang.reflect.Type type = TypeUtils.extractTypeParameter(field, Ref.Input.class);
                if (type == null) {
                    return null;
                }
                field.setAccessible(true);
                return new InputDescriptor(connector.findID(field), category, index, field, type);
            } else {
                return null;
            }
        }

    }

    /**
     * Output port linked to {@link Ref} implementation.
     *
     * @param <T> type of reference
     */
    public static final class Output<T> extends RefPort<T> {

        private final List<Input<T>> connections;
        private final List<PortListener> listeners;

        private RefImpl.Descriptor refDesc;

        private Output(OutputDescriptor desc) {
            this.connections = new ArrayList<>();
            this.listeners = new CopyOnWriteArrayList<>();
            this.refDesc = desc.refDesc;
        }

        private void reconfigure(OutputDescriptor desc) {
            this.refDesc = desc.refDesc;
        }

        @Override
        public void addListener(PortListener listener) {
            listeners.add(Objects.requireNonNull(listener));
        }

        @Override
        @SuppressWarnings("unchecked")
        public void connect(Port port) throws PortConnectionException {
            if (port instanceof Input) {
                Input<T> input = (Input<T>) port;
                if (connections.contains(input)) {
                    throw new PortConnectionException();
                }
                // check type
                input.addRefOutputPort(this);
                connections.add(input);
                listeners.forEach(l -> l.connectionsChanged(this));
            } else {
                throw new PortConnectionException();
            }
        }

        @Override
        public List<? extends Port> connections() {
            return List.copyOf(connections);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void disconnect(Port port) {
            if (port instanceof Input) {
                Input<T> input = (Input<T>) port;
                if (connections.contains(input)) {
                    input.removeRefOutputPort(this);
                    connections.remove(input);
                    listeners.forEach(l -> l.connectionsChanged(this));
                }
            }
        }

        @Override
        public void disconnectAll() {
            for (var input : connections()) {
                disconnect(input);
            }
        }

        @Override
        public void removeListener(PortListener listener) {
            listeners.remove(listener);
        }

        private void revalidateInputs() {
            connections.forEach(Input::updateRefImpl);
        }

    }

    static final class OutputDescriptor extends PortDescriptor<OutputDescriptor> {

        private final RefImpl.Descriptor refDesc;
        private final PortInfo info;

        private Output<?> port;

        private OutputDescriptor(String id,
                Category category,
                int index,
                RefImpl.Descriptor refDesc) {
            super(OutputDescriptor.class, id, category, index);
            this.refDesc = refDesc;
            this.info = PortInfo.create(RefPort.class,
                    PortInfo.Direction.OUT,
                    PMap.of("category", TypeUtils.portCategory(refDesc.getRefType())));
        }

        @Override
        public void attach(CodeContext<?> context, OutputDescriptor previous) {
            if (previous != null && TypeUtils.equivalent(refDesc.getRefType(),
                    previous.refDesc.getRefType())) {
                port = previous.port;
                port.reconfigure(this);
            } else {
                if (previous != null) {
                    previous.dispose();
                }
                port = new Output<>(this);
            }
        }

        @Override
        public PortInfo portInfo() {
            return info;
        }

        @Override
        public Port port() {
            return port;
        }

        void fireChange() {
            port.revalidateInputs();
        }

        static OutputDescriptor create(String id, Out ann, RefImpl.Descriptor refDesc) {
            return new OutputDescriptor(id, Category.Out, ann.value(), refDesc);
        }

        static OutputDescriptor create(String id, AuxOut ann, RefImpl.Descriptor refDesc) {
            return new OutputDescriptor(id, Category.AuxOut, ann.value(), refDesc);
        }

    }

}
