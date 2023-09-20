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
package org.praxislive.hub;

import java.lang.System.Logger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.ComponentFactoryProvider;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;

import static java.lang.System.Logger.Level;

/**
 *
 */
class ComponentRegistry {

    private final static Logger LOG
            = System.getLogger(ComponentRegistry.class.getName());
    private final Map<ComponentType, ComponentFactory> componentCache;
    private final Map<ComponentType, ComponentFactory> rootCache;

    private ComponentRegistry(Map<ComponentType, ComponentFactory> componentCache,
            Map<ComponentType, ComponentFactory> rootCache) {
        this.componentCache = componentCache;
        this.rootCache = rootCache;
    }

    ComponentType[] getComponentTypes() {
        Set<ComponentType> keys = componentCache.keySet();
        return keys.toArray(new ComponentType[keys.size()]);
    }

    ComponentType[] getRootComponentTypes() {
        Set<ComponentType> keys = rootCache.keySet();
        return keys.toArray(new ComponentType[keys.size()]);
    }

    ComponentFactory getComponentFactory(ComponentType type) {
        return componentCache.get(type);
    }

    ComponentFactory getRootComponentFactory(ComponentType type) {
        return rootCache.get(type);
    }

    org.praxislive.core.ComponentRegistry.Result createRegistryResult() {
        Map<ComponentType, Lookup> components = componentCache.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> Lookup.of(e.getValue().componentData(e.getKey()), e.getValue()),
                        (v1, v2) -> v2,
                        LinkedHashMap::new));
        Map<ComponentType, Lookup> roots = rootCache.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> Lookup.of(e.getValue().rootData(e.getKey()), e.getValue()),
                        (v1, v2) -> v2,
                        LinkedHashMap::new));
        return new org.praxislive.core.ComponentRegistry.Result(components, roots);
    }

    static ComponentRegistry getInstance() {
        Map<ComponentType, ComponentFactory> componentCache
                = new LinkedHashMap<>();
        Map<ComponentType, ComponentFactory> rootCache
                = new LinkedHashMap<>();

//        ComponentFactoryProvider[] providers =
//                Lookup.SYSTEM.findAll(ComponentFactoryProvider.class)
//                .toArray(ComponentFactoryProvider[]::new);
//        for (ComponentFactoryProvider provider : providers) {
//            ComponentFactory factory = provider.getFactory();
//            logger.log(Level.INFO, "Adding components from : {0}", factory.getClass());
//            for (ComponentType type : factory.componentTypes()) {
//                componentCache.putIfAbsent(type, factory);
//            }
//            for (ComponentType type : factory.rootTypes()) {
//                rootCache.putIfAbsent(type, factory);
//            }
//        }
        Lookup.SYSTEM.findAll(ComponentFactoryProvider.class)
                .map(ComponentFactoryProvider::getFactory)
                .forEachOrdered(factory -> {
                    LOG.log(Level.DEBUG, "Adding components from : {0}", factory.getClass());
                    factory.componentTypes().forEachOrdered(type -> {
                        LOG.log(Level.DEBUG, "Adding component type : {0}", type);
                        componentCache.put(type, factory);
                    });
                    factory.rootTypes().forEachOrdered(type -> {
                        LOG.log(Level.DEBUG, "Adding root type : {0}", type);
                        rootCache.put(type, factory);
                    });
                });

        return new ComponentRegistry(componentCache, rootCache);
    }
}
