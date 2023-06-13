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

import java.util.List;
import java.util.Map;

/**
 * A registry of all the components available in the local system along with
 * their metadata. Implementations usually to be provided in the {@link RootHub}
 * lookup.
 * <p>
 * The registry may change over time, eg. by the addition of libraries.
 * Implementations should cache the query result, so that object identity can be
 * used to check for changes.
 * <p>
 * The contents of the registry will reflect the contents of all available
 * {@link ComponentFactory}. The component data from the registry result will
 * include all component data from the ComponentFactory, as well as the
 * ComponentFactory itself and any other implementation specific data.
 *
 */
public interface ComponentRegistry {

    /**
     * Query the components available on the local system. The same result will
     * be returned unless the data has changed, so object identity can be used
     * to check for changes.
     *
     * @return component information
     */
    public Result query();

    /**
     * Component results to be returned from {@link #query()}.
     */
    public static final class Result {

        private final List<ComponentType> componentTypes;
        private final List<ComponentType> rootTypes;
        private final Map<ComponentType, Lookup> componentData;
        private final Map<ComponentType, Lookup> rootData;

        /**
         * Construct a Result object. The data will be copied from the provided
         * maps, which should not contain null keys or values. Use of ordered
         * maps is recommended, and the order will be reflected in the lists of
         * component and root types.
         *
         * @param components map of component types and metadata
         * @param roots map of root types and metadata
         */
        public Result(Map<ComponentType, Lookup> components,
                Map<ComponentType, Lookup> roots) {
            this.componentTypes = List.copyOf(components.keySet());
            this.componentData = Map.copyOf(components);
            this.rootTypes = List.copyOf(roots.keySet());
            this.rootData = Map.copyOf(roots);
        }

        /**
         * List of component types available on the local system. The returned
         * list is immutable.
         *
         * @return component types
         */
        public List<ComponentType> componentTypes() {
            return componentTypes;
        }

        /**
         * Query the data for the provided component type.
         *
         * @param type component type
         * @return data, or null if not a provided type
         */
        public Lookup componentData(ComponentType type) {
            return componentData.get(type);
        }

        /**
         * List of root types available on the local system. The returned list
         * is immutable.
         *
         * @return root types
         */
        public List<ComponentType> rootTypes() {
            return rootTypes;
        }

        /**
         * Query the data for the provided root type.
         *
         * @param type root type
         * @return data, or null if not a provided type
         */
        public Lookup rootData(ComponentType type) {
            return rootData.get(type);
        }

    }

}
