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
package org.praxislive.code.services;

import java.util.LinkedHashMap;
import java.util.Map;
import org.praxislive.code.CodeComponentFactoryService;
import org.praxislive.code.CodeRootFactoryService;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.ComponentFactoryProvider;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;

/**
 *
 */
class ComponentRegistry {

    private final Map<ComponentType, ComponentFactory> componentCache;

    private ComponentRegistry(Map<ComponentType, ComponentFactory> componentCache) {
        this.componentCache = componentCache;
    }

    ComponentFactory getComponentFactory(ComponentType type) {
        return componentCache.get(type);
    }

    static ComponentRegistry getInstance() {
        Map<ComponentType, ComponentFactory> componentCache
                = new LinkedHashMap<>();

        Lookup.SYSTEM.findAll(ComponentFactoryProvider.class)
                .map(ComponentFactoryProvider::getFactory)
                .filter(factory
                        -> factory.getFactoryService() == CodeComponentFactoryService.class)
                .forEachOrdered(factory -> {
                    factory.componentTypes().forEachOrdered(type -> {
                        componentCache.put(type, factory);
                    });
                }
                );
        
        Lookup.SYSTEM.findAll(ComponentFactoryProvider.class)
                .map(ComponentFactoryProvider::getFactory)
                .filter(factory
                        -> factory.getRootFactoryService() == CodeRootFactoryService.class)
                .forEachOrdered(factory -> {
                    factory.rootTypes().forEachOrdered(type -> {
                        componentCache.put(type, factory);
                    });
                }
                );

        return new ComponentRegistry(componentCache);
    }
}
