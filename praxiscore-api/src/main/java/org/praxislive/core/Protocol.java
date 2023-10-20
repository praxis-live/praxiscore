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
package org.praxislive.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.SerializableProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.services.ComponentFactoryService;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.SystemManagerService;
import org.praxislive.core.services.TaskService;

/**
 * A Protocol defines known controls and behaviours that a component can
 * provide.
 */
public interface Protocol {

    /**
     * The names of the controls that a component advertising this protocol must
     * provide.
     *
     * @return stream of control names
     */
    public Stream<String> controls();

    /**
     * The names of additional controls that a component advertising this
     * protocol might provide. These controls are optional. Any caller should
     * check whether the component info contains the control, or otherwise
     * prepare for the control not to be available.
     * <p>
     * Implementation note : the protocol implementation should support querying
     * the control info via {@link #getControlInfo(java.lang.String)}. The
     * default implementation returns an empty stream.
     *
     * @return stream of optional control names
     */
    public default Stream<String> optionalControls() {
        return Stream.empty();
    }

    /**
     * Query the ControlInfo for the provided control name on this protocol. The
     * component implementing this protocol will generally use the control info
     * provided here inside its component info. In exceptional circumstances,
     * the component may extend or adapt the behaviour of the control, as long
     * as it is fully compatible with this control info and the specification.
     *
     * @param control name of control
     * @return control info for named control
     */
    public ControlInfo getControlInfo(String control);

    /**
     * A protocol type registration, allowing protocols to be discovered by
     * class or name. Additional types may be registered using
     * {@link TypeProvider}.
     *
     * @param <T> class of protocol
     */
    public static class Type<T extends Protocol> {

        private final Class<T> cls;
        private final String name;

        /**
         * Construct a type for the given Protocol class. The name will be the
         * simple name of the class.
         *
         * @param cls Protocol class
         */
        public Type(Class<T> cls) {
            this.cls = Objects.requireNonNull(cls);
            this.name = cls.getSimpleName();
        }

        /**
         * Access the class of the Protocol type.
         *
         * @return class
         */
        public Class<T> asClass() {
            return cls;
        }

        /**
         * Access the name of the Protocol type.
         *
         * @return name
         */
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int hashCode() {
            return cls.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Type<?> other = (Type<?>) obj;
            return Objects.equals(this.cls, other.cls);
        }

        /**
         * Lookup the Protocol type of the provided class. The type must be
         * registered.
         *
         * @param <T> class type
         * @param cls class
         * @return type
         */
        @SuppressWarnings("unchecked")
        public static <T extends Protocol> Type<T> of(Class<T> cls) {
            Type<T> type = (Type<T>) typesByClass.get(cls);
            if (type == null) {
                throw new IllegalArgumentException("Unregistered Protocol type : " + cls.getName());
            }
            return type;
        }

        /**
         * Lookup the Protocol type by name. If not registered an empty optional
         * is returned.
         *
         * @param name protocol name
         * @return optional of type
         */
        public static Optional<Type<? extends Protocol>> fromName(String name) {
            return Optional.ofNullable(typesByName.get(name));
        }

        private final static Map<Class<? extends Protocol>, Type<? extends Protocol>> typesByClass
                = new HashMap<>();
        private final static Map<String, Type<? extends Protocol>> typesByName
                = new HashMap<>();

        private static <T extends Protocol> void register(Type<T> type) {
            if (typesByClass.containsKey(type.asClass()) || typesByName.containsKey(type.name())) {
                throw new IllegalStateException("Already registered type");
            }
            typesByClass.put(type.asClass(), type);
            typesByName.put(type.name(), type);
        }

        static {

            register(new Type<>(ComponentProtocol.class));
            register(new Type<>(ContainerProtocol.class));
            register(new Type<>(StartableProtocol.class));
            register(new Type<>(SerializableProtocol.class));

            register(new Type<>(ComponentFactoryService.class));
            register(new Type<>(RootFactoryService.class));
            register(new Type<>(RootManagerService.class));
            register(new Type<>(ScriptService.class));
            register(new Type<>(SystemManagerService.class));
            register(new Type<>(TaskService.class));
            register(new Type<>(LogService.class));

            Lookup.SYSTEM.findAll(TypeProvider.class)
                    .flatMap(TypeProvider::types)
                    .forEachOrdered(Type::register);

        }

    }

    /**
     * Provide additional {@link Protocol.Type}. Instances should be registered
     * to be discovered via {@link Lookup#SYSTEM}.
     */
    public static interface TypeProvider {

        /**
         * Types to register.
         *
         * @return stream to types
         */
        Stream<Type> types();

    }

}
