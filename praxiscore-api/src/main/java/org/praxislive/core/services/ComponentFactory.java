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
package org.praxislive.core.services;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;
import org.praxislive.core.Root;
import org.praxislive.core.types.PReference;

/**
 * A provider of component types registered into the system via
 * {@link ComponentFactoryProvider}. The available components in the local
 * system will be a combination of results from all registered component
 * factories.
 * <p>
 * The default {@link ComponentFactoryService} or {@link RootFactoryService}
 * will create instances of components or roots by calling the factory methods
 * {@link #createComponent(org.praxislive.core.ComponentType)} or
 * {@link #createRootComponent(org.praxislive.core.ComponentType)}. As an
 * alternative, redirects can be provided to other services that will be used to
 * construct the requested type. The alternative service might use the factory
 * methods, other data in the component data lookups, or its own registry to
 * construct the component.
 * <p>
 * The data lookups can also be used to provide additional metadata related to
 * each component type.
 */
public interface ComponentFactory {

    /**
     * Component types provided by this factory.
     *
     * @return stream of component types
     */
    public Stream<ComponentType> componentTypes();

    /**
     * Root types provided by this factory.
     *
     * @return stream of root types
     */
    public Stream<ComponentType> rootTypes();

    @Deprecated
    public default MetaData<? extends Component> getMetaData(ComponentType type) {
        Supplier<Lookup> redirect = () -> componentData(type);
        return new MetaData(redirect) {
        };
    }

    @Deprecated
    public default MetaData<? extends Root> getRootMetaData(ComponentType type) {
        Supplier<Lookup> redirect = () -> rootData(type);
        return new MetaData(redirect) {
        };
    }

    /**
     * Query the data associated with this component type.
     *
     * @param type component type
     * @return lookup of data
     */
    public default Lookup componentData(ComponentType type) {
        var metadata = getMetaData(type);
        if (metadata == null) {
            return null;
        } else if (metadata.redirect != null) {
            return Lookup.EMPTY;
        } else {
            return metadata.getLookup();
        }
    }

    /**
     * Query the data associated with this root type.
     *
     * @param type root type
     * @return lookup of data
     */
    public default Lookup rootData(ComponentType type) {
        var metadata = getRootMetaData(type);
        if (metadata == null) {
            return null;
        } else if (metadata.redirect != null) {
            return Lookup.EMPTY;
        } else {
            return metadata.getLookup();
        }
    }

    /**
     * Create an instance of the component associated with this type. Component
     * factories with a redirect may not support this method, and always throw
     * an exception. The default implementation always throws an exception.
     *
     * @param type component type to create
     * @return created component instance
     * @throws ComponentInstantiationException
     */
    public default Component createComponent(ComponentType type) throws ComponentInstantiationException {
        throw new ComponentInstantiationException();
    }

    /**
     * Create an instance of the root associated with this type. Component
     * factories with a redirect may not support this method, and always throw
     * an exception. The default implementation always throws an exception.
     *
     * @param type root type to create
     * @return created root instance
     * @throws ComponentInstantiationException
     */
    public default Root createRoot(ComponentType type) throws ComponentInstantiationException {
        throw new ComponentInstantiationException();
    }

    @Deprecated
    public default Root createRootComponent(ComponentType type) throws ComponentInstantiationException {
        return createRoot(type);
    }

    @Deprecated
    public default Class<? extends ComponentFactoryService> getFactoryService() {
        return ComponentFactoryService.class;
    }

    @Deprecated
    public default Class<? extends RootFactoryService> getRootFactoryService() {
        return RootFactoryService.class;
    }

    /**
     * Optional service to redirect to for component instantiation. The control
     * on the service should follow the same shape as the default
     * {@link ComponentFactoryService#NEW_INSTANCE_INFO}, accepting a
     * {@link ComponentType} and returning the component instance wrapped in a
     * {@link PReference}.
     *
     * @return optional service redirect
     */
    public default Optional<Redirect> componentRedirect() {
        var service = getFactoryService();
        if (service == null || ComponentFactoryService.class.equals(service)) {
            return Optional.empty();
        } else {
            return Optional.of(new Redirect(service, ComponentFactoryService.NEW_INSTANCE));
        }
    }

    /**
     * Optional service to redirect to for root instantiation. The control on
     * the service should follow the same shape as the default
     * {@link RootFactoryService#NEW_ROOT_INSTANCE_INFO}, accepting a
     * {@link ComponentType} and returning the root instance wrapped in a
     * {@link PReference}.
     *
     * @return optional service redirect
     */
    public default Optional<Redirect> rootRedirect() {
        var service = getRootFactoryService();
        if (service == null || RootFactoryService.class.equals(service)) {
            return Optional.empty();
        } else {
            return Optional.of(new Redirect(service, RootFactoryService.NEW_ROOT_INSTANCE));
        }
    }

    @Deprecated
    public static abstract class MetaData<T> {

        private final Supplier<Lookup> redirect;

        public MetaData() {
            this.redirect = null;
        }

        private MetaData(Supplier<Lookup> redirect) {
            this.redirect = redirect;
        }

        public boolean isDeprecated() {
            return false;
        }

        public Optional<ComponentType> findReplacement() {
            return Optional.empty();
        }

        public Lookup getLookup() {
            return redirect != null ? redirect.get() : Lookup.EMPTY;
        }

    }

    /**
     * A factory service redirect. See {@link #componentRedirect()} and
     * {@link #rootRedirect()}.
     */
    public static final class Redirect {

        private final Class<? extends Service> service;
        private final String control;

        /**
         * Construct a redirect to the provided service and control.
         *
         * @param service service type to redirect to
         * @param control control on service to redirect to
         */
        public Redirect(Class<? extends Service> service, String control) {
            this.service = Objects.requireNonNull(service);
            this.control = Objects.requireNonNull(control);
        }

        /**
         * Query the service to redirect to.
         *
         * @return service
         */
        public Class<? extends Service> service() {
            return service;
        }

        /**
         * Query the control on the service to redirect to.
         *
         * @return control id
         */
        public String control() {
            return control;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Objects.hashCode(this.service);
            hash = 97 * hash + Objects.hashCode(this.control);
            return hash;
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
            final Redirect other = (Redirect) obj;
            if (!Objects.equals(this.control, other.control)) {
                return false;
            }
            return Objects.equals(this.service, other.service);
        }

    }
}
