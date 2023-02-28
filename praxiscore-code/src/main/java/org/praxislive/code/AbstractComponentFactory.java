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
import org.praxislive.core.Component;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;
import org.praxislive.core.Root;
import org.praxislive.core.services.ComponentFactoryService;
import org.praxislive.core.services.RootFactoryService;

/**
 *
 */
public class AbstractComponentFactory implements ComponentFactory {

    private final Map<ComponentType, MetaData> componentMap;
    private final Map<ComponentType, RootMetaData> rootMap;

    protected AbstractComponentFactory() {
        componentMap = new LinkedHashMap<>();
        rootMap = new LinkedHashMap<>(1);
    }

    @Override
    public Stream<ComponentType> componentTypes() {
        return componentMap.keySet().stream();
    }

    @Override
    public Stream<ComponentType> rootTypes() {
        return rootMap.keySet().stream();
    }

    @Override
    public ComponentFactory.MetaData<? extends Component> getMetaData(ComponentType type) {
        return componentMap.get(type);
    }

    @Override
    public ComponentFactory.MetaData<? extends Root> getRootMetaData(ComponentType type) {
        return rootMap.get(type);
    }

    @Override
    public Class<? extends ComponentFactoryService> getFactoryService() {
        return CodeComponentFactoryService.class;
    }

    @Override
    public Class<? extends RootFactoryService> getRootFactoryService() {
        return CodeRootFactoryService.class;
    }
    
    protected void add(Data info) {
        componentMap.put(info.factory.componentType(), info.toMetaData());
    }
    
    protected void add(CodeFactory<?> factory) {
        componentMap.put(factory.componentType(), new MetaData(factory));
    }
    
    protected void addRoot(CodeFactory<? extends CodeRootDelegate> factory) {
        rootMap.put(factory.componentType(), new RootMetaData(factory));
    }

    protected Data data(CodeFactory<?> factory) {
        return new Data(factory);
    }

    protected String source(String location) {
        return CodeUtils.load(getClass(), location);
    }

    private static class MetaData extends ComponentFactory.MetaData<Component> {

        private final boolean deprecated;
        private final ComponentType replacement;
        private final Lookup lookup;

        private MetaData(
                boolean deprecated,
                ComponentType replacement,
                Lookup lookup) {
            this.deprecated = deprecated;
            this.replacement = replacement;
            this.lookup = lookup;
        }
        
        private MetaData(CodeFactory<?> codeFactory) {
            this.lookup = Lookup.of(codeFactory);
            this.replacement = null;
            this.deprecated = false;
        }

        @Override
        public boolean isDeprecated() {
            return deprecated;
        }

        @Override
        public Optional<ComponentType> findReplacement() {
            return Optional.ofNullable(replacement);
        }

        @Override
        public Lookup getLookup() {
            return lookup;
        }

    }

    private static class RootMetaData extends ComponentFactory.MetaData<Root> {

        private final Lookup lookup;

        private RootMetaData(CodeFactory<? extends CodeRootDelegate> codeFactory) {
            this.lookup = Lookup.of(codeFactory);
        }

        @Override
        public Lookup getLookup() {
            return lookup;
        }

    }
    
    public static class Data {

        private final CodeFactory<?> factory;
        private final List<Object> lookupList;
        private boolean deprecated;
        private ComponentType replacement;

        private Data(CodeFactory<?> factory) {
            this.factory = factory;
            lookupList = new ArrayList<>();
            lookupList.add(factory);
        }

        public Data deprecated() {
            deprecated = true;
            return this;
        }

        public Data replacement(String type) {
            replacement = ComponentType.of(type);
            deprecated = true;
            return this;
        }

        public Data add(Object obj) {
            lookupList.add(obj);
            return this;
        }

        private MetaData toMetaData() {
            return new MetaData(deprecated, replacement,
                    Lookup.of(lookupList.toArray()));
        }
    }
}
