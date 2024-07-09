/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2024 Neil C Smith.
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
import java.util.Optional;
import org.praxislive.code.CodeComponentFactoryService;
import org.praxislive.code.CodeDelegate;
import org.praxislive.code.CodeFactory;
import org.praxislive.code.CodeRootFactoryService;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.ComponentFactoryProvider;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;
import org.praxislive.core.OrderedMap;

/**
 *
 */
class ComponentRegistry {

    private final OrderedMap<ComponentType, ComponentFactory> componentCache;
    private final OrderedMap<Class<? extends CodeDelegate>, CodeFactory.Base<CodeDelegate>> baseCache;

    private ComponentRegistry(OrderedMap<ComponentType, ComponentFactory> componentCache,
            OrderedMap<Class<? extends CodeDelegate>, CodeFactory.Base<CodeDelegate>> baseCache) {
        this.componentCache = componentCache;
        this.baseCache = baseCache;
    }

    ComponentFactory getComponentFactory(ComponentType type) {
        return componentCache.get(type);
    }

    CodeFactory.Base<CodeDelegate> findSuitableBase(Class<? extends CodeDelegate> cls) {
        Class<?> c = cls;
        while (c != CodeDelegate.class && c != null) {
            CodeFactory.Base<CodeDelegate> base = baseCache.get(c);
            if (base != null) {
                return base;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    static ComponentRegistry getInstance() {
        Map<ComponentType, ComponentFactory> components
                = new LinkedHashMap<>();
        Map<Class<? extends CodeDelegate>, CodeFactory.Base<CodeDelegate>> bases
                = new LinkedHashMap<>();

        Lookup.SYSTEM.findAll(ComponentFactoryProvider.class)
                .map(ComponentFactoryProvider::getFactory)
                .filter(factory
                        -> factory.componentRedirect()
                        .filter(r -> r.service() == CodeComponentFactoryService.class)
                        .isPresent())
                .forEachOrdered(factory -> {
                    factory.componentTypes().forEachOrdered(type -> {
                        components.put(type, factory);
                        CodeFactory.Base<CodeDelegate> base = findBase(factory, type);
                        if (base != null) {
                            bases.putIfAbsent(base.baseClass(), base);
                        }
                    });
                }
                );

        Lookup.SYSTEM.findAll(ComponentFactoryProvider.class)
                .map(ComponentFactoryProvider::getFactory)
                .filter(factory
                        -> factory.rootRedirect()
                        .filter(r -> r.service() == CodeRootFactoryService.class)
                        .isPresent())
                .forEachOrdered(factory -> {
                    factory.rootTypes().forEachOrdered(type -> {
                        components.put(type, factory);
                        CodeFactory.Base<CodeDelegate> base = findBase(factory, type);
                        if (base != null) {
                            bases.putIfAbsent(base.baseClass(), base);
                        }
                    });
                }
                );

        return new ComponentRegistry(OrderedMap.copyOf(components), OrderedMap.copyOf(bases));
    }

    private static CodeFactory.Base<CodeDelegate> findBase(ComponentFactory factory, ComponentType type) {
        return Optional.ofNullable(factory.componentData(type))
                .flatMap(data -> data.find(CodeFactory.class))
                .map(CodeFactory::lookup)
                .flatMap(data -> data.find(CodeFactory.Base.class))
                .orElse(null);
    }

}
