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

    /**
     * Query the data associated with this component type.
     *
     * @param type component type
     * @return lookup of data
     */
    public default Lookup componentData(ComponentType type) {
        return Lookup.EMPTY;
    }

    /**
     * Query the data associated with this root type.
     *
     * @param type root type
     * @return lookup of data
     */
    public default Lookup rootData(ComponentType type) {
        return Lookup.EMPTY;
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
        return Optional.empty();
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
        return Optional.empty();
    }

    /**
     * A factory service redirect. See {@link #componentRedirect()} and
     * {@link #rootRedirect()}.
     */
    public record Redirect(Class<? extends Service> service, String control) {

    }

    /**
     * Mark a component deprecated (with optional replacement type). An instance
     * should be added to the metadata lookup associated with the component
     * type.
     */
    public record Deprecated(Optional<ComponentType> replacement) {

        /**
         * Deprecated without replacement.
         */
        public Deprecated() {
            this(Optional.empty());
        }

        /**
         * Deprecated with replacement type.
         *
         * @param replacement replacement type
         */
        public Deprecated(ComponentType replacement) {
            this(Optional.of(replacement));
        }

    }

}
