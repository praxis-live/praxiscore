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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;
import org.praxislive.core.services.ComponentFactoryService;
import org.praxislive.core.services.RootFactoryService;

/**
 *
 */
public class AbstractComponentFactory implements ComponentFactory {

    private static final ComponentFactory.Redirect COMPONENT_REDIRECT
            = new ComponentFactory.Redirect(CodeComponentFactoryService.class,
                    ComponentFactoryService.NEW_INSTANCE);

    private static final ComponentFactory.Redirect ROOT_REDIRECT
            = new ComponentFactory.Redirect(CodeRootFactoryService.class,
                    RootFactoryService.NEW_ROOT_INSTANCE);

    private final Map<ComponentType, Lookup> componentMap;
    private final Map<ComponentType, Lookup> rootMap;

    protected AbstractComponentFactory() {
        componentMap = new LinkedHashMap<>();
        rootMap = new LinkedHashMap<>(1);
    }

    @Override
    public final Stream<ComponentType> componentTypes() {
        return componentMap.keySet().stream();
    }

    @Override
    public final Stream<ComponentType> rootTypes() {
        return rootMap.keySet().stream();
    }

    @Override
    public final Lookup componentData(ComponentType type) {
        return componentMap.get(type);
    }

    @Override
    public final Lookup rootData(ComponentType type) {
        return rootMap.get(type);
    }

    @Override
    public final Optional<Redirect> componentRedirect() {
        return Optional.of(COMPONENT_REDIRECT);
    }

    @Override
    public final Optional<Redirect> rootRedirect() {
        return Optional.of(ROOT_REDIRECT);
    }

    protected void add(Data info) {
        componentMap.put(info.factory.componentType(), info.toLookup());
    }

    protected void add(CodeFactory<?> factory) {
        componentMap.put(factory.componentType(), Lookup.of(factory));
    }

    protected void addRoot(CodeFactory<? extends CodeRootDelegate> factory) {
        rootMap.put(factory.componentType(), Lookup.of(factory));
    }

    protected Data data(CodeFactory<?> factory) {
        return new Data(factory);
    }

    protected String source(String location) {
        return CodeUtils.load(getClass(), location);
    }

    public static class Data {

        private final CodeFactory<?> factory;
        private final List<Object> lookupList;

        private Data(CodeFactory<?> factory) {
            this.factory = factory;
            lookupList = new ArrayList<>();
            lookupList.add(factory);
        }

        public Data deprecated() {
            lookupList.add(new ComponentFactory.Deprecated());
            return this;
        }

        public Data replacement(String type) {
            lookupList.add(new ComponentFactory.Deprecated(ComponentType.of(type)));
            return this;
        }

        public Data add(Object obj) {
            lookupList.add(obj);
            return this;
        }

        private Lookup toLookup() {
            return Lookup.of(lookupList.toArray());
        }
    }
}
