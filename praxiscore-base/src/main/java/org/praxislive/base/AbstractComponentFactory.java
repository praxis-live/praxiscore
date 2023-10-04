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
package org.praxislive.base;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.praxislive.core.Component;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.ComponentInstantiationException;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Lookup;
import org.praxislive.core.Root;

/**
 *
 */
public class AbstractComponentFactory implements ComponentFactory {

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
    public final Component createComponent(ComponentType type) throws ComponentInstantiationException {
        Lookup data = componentMap.get(type);
        if (data == null) {
            throw new IllegalArgumentException();
        }
        try {
            Class<? extends Component> cl = data.findAll(Class.class)
                    .filter(Component.class::isAssignableFrom)
                    .map(cls -> cls.asSubclass(Component.class))
                    .findFirst()
                    .orElseThrow();
            return cl.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new ComponentInstantiationException(ex);
        }
    }

    @Override
    public final Root createRoot(ComponentType type) throws ComponentInstantiationException {
        Lookup data = rootMap.get(type);
        if (data == null) {
            throw new IllegalArgumentException();
        }
        try {
            Class<? extends Root> cl = data.findAll(Class.class)
                    .filter(Root.class::isAssignableFrom)
                    .map(cls -> cls.asSubclass(Root.class))
                    .findFirst()
                    .orElseThrow();
            return cl.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new ComponentInstantiationException(ex);
        }
    }

    @Override
    public final Lookup componentData(ComponentType type) {
        return componentMap.get(type);
    }

    @Override
    public final Lookup rootData(ComponentType type) {
        return rootMap.get(type);
    }

    protected void add(String type, Class<? extends Component> cls) {
        add(type, data(cls));
    }
    
    protected void add(String type, Data<? extends Component> info) {
        componentMap.put(ComponentType.of(type), info.toLookup());
    }

    protected void addRoot(String type, Class<? extends Root> cls) {
        addRoot(type, data(cls));
    }
    
    protected void addRoot(String type, Data<? extends Root> info) {
        rootMap.put(ComponentType.of(type), info.toLookup());
    }
    
    public static <T> Data<T> data(Class<T> cls) {
        return new Data<>(cls);
    }

    public static class Data<T> {

        private final List<Object> lookupList;
        
        private Data(Class<T> cls) {
            lookupList = new ArrayList<>();
            lookupList.add(cls);
        }
        
        public Data<T> deprecated() {
            lookupList.add(new ComponentFactory.Deprecated());
            return this;
        }
        
        public Data<T> replacement(String type) {
            lookupList.add(new ComponentFactory.Deprecated(ComponentType.of(type)));
            return this;
        }
        
        public Data<T> add(Object obj) {
            lookupList.add(obj);
            return this;
        }
        
        private Lookup toLookup() {
            return Lookup.of(lookupList.toArray());
        }
    }
}
