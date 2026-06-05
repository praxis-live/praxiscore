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
package org.praxislive.core;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PArray;

/**
 * A model API for proxying and interacting with components running inside a
 * {@link RootHub}. Implementations may run internally or externally to the
 * modelled hub using a common interface.
 */
public interface HubProxy {

    /**
     * Get a component proxy for the provided address. The proxied component
     * does not have to exist when the proxy is created. Component proxies are
     * cached by the implementation, and a single instance will be returned for
     * each distinct address until the HubProxy is cleared or disposed.
     *
     * @param address component address
     * @return component proxy
     */
    public Component component(ComponentAddress address);

    /**
     * Get a component proxy for the provided address. The proxied component
     * does not have to exist when the proxy is created. Component proxies are
     * cached by the implementation, and a single instance will be returned for
     * each distinct address until the HubProxy is cleared or disposed.
     * <p>
     * @implNote The default implementation of this method calls
     * {@link #component(org.praxislive.core.ComponentAddress)} with the result
     * of {@link ComponentAddress#of(java.lang.String)}.
     *
     * @param address component address
     * @return component proxy
     */
    public default Component component(String address) {
        return component(ComponentAddress.of(address));
    }

    /**
     * Get a root proxy for the provided root ID. The proxied root does not have
     * to exist when the proxy is created. Root proxies are cached by the
     * implementation, and a single instance will be returned for each distinct
     * address until the HubProxy is cleared or disposed.
     * <p>
     * The hub implementation will ensure that this method and a call to
     * {@link #component(org.praxislive.core.ComponentAddress)} with the same
     * root address will return identical instances.
     *
     * @param id root ID
     * @return root proxy
     */
    public Root root(String id);

    /**
     * Get all the cached component proxies created by this hub proxy. The
     * returned set is an immutable snapshot.
     *
     * @return component proxies
     */
    public Set<Component> proxies();

    /**
     * Get a list of root IDs in the proxied hub. The returned list is an
     * immutable snapshot.
     *
     * @return root IDs
     */
    public List<String> roots();

    /**
     * Stop all component proxies syncing. Equivalent to calling
     * {@link Component#unsync()} on all proxy components from this hub proxy.
     *
     * @return this for chaining
     */
    public HubProxy unsyncAll();

    /**
     * Dispose of all proxy components created by this hub proxy. This will
     * clear the cache. New proxies should be obtained from
     * {@link #component(org.praxislive.core.ComponentAddress)} or
     * {@link #root(java.lang.String)} if required.
     *
     * @return this for chaining
     */
    public HubProxy clear();

    /**
     * Dispose of this hub proxy. No component proxy or function on this proxy
     * should be used after disposal.
     * <p>
     * @implNote The default implementation of this method just calls
     * {@link #clear()}.
     */
    public default void dispose() {
        clear();
    }

    /**
     * Evaluate the provided Pcl script within the proxied hub and return a
     * future of the result. This is an optional behaviour. The default
     * implementation returns a failed future wrapping
     * {@link UnsupportedOperationException}.
     *
     * @param script Pcl script to evaluate
     * @return future result of script
     */
    public default Future<List<Value>> eval(String script) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    /**
     * A proxy for a {@link org.praxislive.core.Component} in the hub. Obtain
     * from the hub proxy using
     * {@link #component(org.praxislive.core.ComponentAddress)}.
     * <p>
     * A component can sync by polling the real component for property values.
     * For efficiency, only sync properties when the values are required.
     */
    public static interface Component {

        /**
         * The hub proxy that this component belongs to.
         *
         * @return hub proxy
         */
        public HubProxy hub();

        /**
         * Set a property value on the proxied component.
         *
         * @param id property control ID
         * @param value new property value
         * @return this for chaining
         */
        public Component set(String id, Value value);

        /**
         * Set a property value on the proxied component.
         * <p>
         * @implNote the default implementation converts the value using
         * {@link Value#ofObject(java.lang.Object)} and then calls
         * {@link #set(java.lang.String, org.praxislive.core.Value)}.
         *
         * @param id property control ID
         * @param value new property value
         * @return this for chaining
         */
        public default Component set(String id, Object value) {
            return set(id, Value.ofObject(value));
        }

        /**
         * Get a property value on the proxied component. If the component is
         * not syncing, the value may not be available or may be stale. This
         * method returns an immediate result and does not directly poll the
         * component.
         *
         * @param id property control ID
         * @return optional property value
         */
        public Optional<Value> get(String id);

        /**
         * Get the address of the proxied component.
         *
         * @return proxied address
         */
        public ComponentAddress address();

        /**
         * Get the ID of the proxied component.
         * <p>
         * @implNote the default method returns
         * {@link ComponentAddress#componentID()} on the {@link #address()}.
         * @return
         */
        public default String id() {
            return address().componentID();
        }

        /**
         * Get the {@link ComponentInfo} for the proxied component. If the
         * component doesn't exist or the proxy isn't syncing, the info might
         * not exist or be stale. Use {@link #validate()} to directly poll the
         * component.
         *
         * @return optional component info
         */
        public Optional<ComponentInfo> info();

        /**
         * Start the proxy polling the component to sync properties. The rate of
         * polling is undefined.
         *
         * @return this for chaining
         */
        public Component sync();

        /**
         * Start the proxy polling the component to sync the provided
         * properties. This method may be more efficient if only a selection of
         * property values are required.
         * <p>
         * @implNote the default implementation just calls {@link #sync()}
         *
         * @param properties set of properties to sync
         * @return this for chaining
         */
        public default Component sync(Set<String> properties) {
            return sync();
        }

        /**
         * Stop the proxy polling the component.
         *
         * @return this for chaining
         */
        public Component unsync();

        /**
         * Validate the proxy by polling the component for its latest info. The
         * future will return an empty optional when the component does not
         * exist or cannot be proxied.
         *
         * @return future info
         */
        public Future<Optional<ComponentInfo>> validate();

        /**
         * Check whether the proxy is valid.
         * <p>
         * @implNote the default implementation checks whether {@link #info()}
         * is present
         *
         * @return true if valid
         */
        public default boolean isValid() {
            return info().isPresent();
        }

        /**
         * Check whether the proxied component is a container. If the component
         * isn't valid or the info is stale, the result might not be accurate.
         * <p>
         * @implNote the default implementation checks if the info exists and
         * has the {@link ContainerProtocol}.
         *
         * @return true if container
         */
        public default boolean isContainer() {
            return info()
                    .map(info -> info.hasProtocol(ContainerProtocol.class))
                    .orElse(false);
        }

        /**
         * Get the proxy for the parent of the proxied component.
         * <p>
         * @implNote the default implementation calls
         * {@link ComponentAddress#parent()} on the component address and
         * returns the proxy for that address from the hub proxy.
         * @return parent proxy
         */
        public default Component parent() {
            return hub().component(address().parent());
        }

        /**
         * Get the proxy for a descendent of the proxied component by resolving
         * the provided path against the component address. A direct child can
         * be obtained by passing in its ID.
         * <p>
         * @implNote the default implementation calls
         * {@link ComponentAddress#resolve(java.lang.String)} on the component
         * address and returns the proxy for that address from the hub proxy.
         *
         * @param path child ID or relative path to descendent
         * @return descendent component proxy
         */
        public default Component resolve(String path) {
            return hub().component(address().resolve(path));
        }

        /**
         * Get a list of the IDs of the proxied component's children. The list
         * is an immutable snapshot. If the proxy is not syncing then this
         * result might be stale.
         * <p>
         * @implNote the default implementation checks whether the proxy is a
         * container, and if so returns a list extracted from the
         * {@link ContainerProtocol#CHILDREN} property.
         *
         * @return child IDs or empty list
         */
        public default List<String> children() {
            if (isContainer()) {
                return get(ContainerProtocol.CHILDREN)
                        .flatMap(PArray::from)
                        .map(a -> a.asListOf(String.class))
                        .orElseGet(() -> List.of());
            } else {
                return List.of();
            }
        }

        /**
         * Call a control on the proxied component.
         * <p>
         * @implNote the default implementation of this method creates a
         * one-line script based on the component address and passes it to
         * {@link #eval(java.lang.String)}.
         *
         * @param control ID of control to call
         * @param args call arguments
         * @return future result of the call
         */
        public default Future<List<Value>> call(String control, List<Value> args) {
            ControlAddress ctrlAd = ControlAddress.of(address(), control);
            if (args.isEmpty()) {
                return hub().eval(ctrlAd.toString());
            } else {
                return hub().eval(ctrlAd + " " + PArray.of(args));
            }
        }

        /**
         * Call a control on the proxied component.
         * <p>
         * @implNote the default implementation of this method creates a list of
         * values from the args using {@link Value#ofObject(java.lang.Object)}
         * and then calls {@link #call(java.lang.String, java.util.List)}.
         *
         * @param control ID of control to call
         * @param args call arguments
         * @return future result of the call
         */
        public default Future<List<Value>> call(String control, Object... args) {
            return call(control, Stream.of(args).map(Value::ofObject).toList());
        }

        /**
         * Dispose the proxy. After disposal the proxy should not be used. A
         * subsequent call to
         * {@link #component(org.praxislive.core.ComponentAddress)} with this
         * proxy's address will return a new proxy.
         */
        public void dispose();

    }

    /**
     * A proxy for a {@link org.praxislive.core.Root} in the hub. Obtain from
     * the hub proxy using {@link #root(java.lang.String)}.
     */
    public static interface Root extends Component {

        /**
         * The parent of a Root is always {@code null}.
         *
         * @return null
         */
        @Override
        public default Component parent() {
            return null;
        }

        /**
         * Convenience method to call {@link StartableProtocol#START} on the
         * root.
         *
         * @return result of call
         */
        public default Future<?> start() {
            return call(StartableProtocol.START, List.of());
        }

        /**
         * Convenience method to call {@link StartableProtocol#STOP} on the
         * root.
         *
         * @return result of call
         */
        public default Future<?> stop() {
            return call(StartableProtocol.STOP, List.of());
        }

    }

}
