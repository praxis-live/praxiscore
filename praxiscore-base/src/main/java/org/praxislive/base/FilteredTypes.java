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
package org.praxislive.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentRegistry;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Container;
import org.praxislive.core.protocols.SupportedTypes;

/**
 * An implementation of {@link SupportedTypes} that can be included in the
 * lookup of a container. The implementation automatically searches the parent
 * lookup for other SupportedTypes, or otherwise a {@link ComponentRegistry}.
 * <p>
 * Results can be subject to further filtering, or additional types can be
 * included.
 * <p>
 * The result is cached. The FilteredTypes must be {@link #reset()} on hierarchy
 * changes, and in any circumstances where the behaviour of the filter or
 * additional types supplier changes.
 */
public class FilteredTypes implements SupportedTypes {

    private final Container context;
    private final Predicate<ComponentType> filter;
    private final Supplier<List<ComponentType>> additional;
    private final boolean includeParentAdditional;

    private Result result;
    private List<ComponentType> addedTypes;
    private SupportedTypes delegate;
    private Result delegateResult;

    private FilteredTypes(Container context,
            Predicate<ComponentType> filter,
            Supplier<List<ComponentType>> additional,
            boolean includeParentAdditional) {
        this.context = context;
        this.filter = filter;
        this.additional = additional;
        this.includeParentAdditional = includeParentAdditional;
    }

    @Override
    public Result query() {
        if (result == null) {
            delegate = Optional.ofNullable(context.getParent())
                    .flatMap(p -> p.getLookup().find(SupportedTypes.class))
                    .filter(d -> d != this)
                    .orElseGet(() -> new BaseDelegate(this));
            delegateResult = delegate.query();
            result = calculateResult(delegate, delegateResult);
        } else {
            var delRes = delegate.query();
            if (delRes != delegateResult) {
                delegateResult = delRes;
                result = calculateResult(delegate, delegateResult);
            }
        }
        return result;
    }

    /**
     * Reset and cause the result to be recalculated on the next call to
     * {@link #query()}. The FilteredTypes should be reset on hierarchy changes
     * of the container, and in any circumstances where the behaviour of the
     * filter or additional types supplier changes.
     */
    public void reset() {
        result = null;
        addedTypes = null;
        delegate = null;
        delegateResult = null;
    }

    private Result calculateResult(SupportedTypes delegate, Result delegateResult) {
        if (filter == null && additional == null) {
            return delegateResult;
        }
        List<ComponentType> list = new ArrayList<>(delegateResult.types());
        if (!includeParentAdditional && delegate instanceof FilteredTypes ft) {
            list.removeAll(ft.addedTypes());
        }
        if (filter != null) {
            list.removeIf(Predicate.not(filter));
        }
        if (additional != null) {
            addedTypes = List.copyOf(additional.get());
            list.addAll(addedTypes);
        }
        return new Result(list);
    }

    private List<ComponentType> addedTypes() {
        return addedTypes == null ? List.of() : addedTypes;
    }

    /**
     * Create a FilteredTypes for the provided context. If no
     * {@link SupportedTypes} is found in the parent lookup, then the
     * implementation will attempt to filter the {@link ComponentRegistry}
     * result according to the root type.
     *
     * @param context container this will be added to
     * @return instance
     */
    public static FilteredTypes create(Container context) {
        return create(context, null, null, true);
    }

    /**
     * Create a FilteredTypes for the provided context, additionally filtering
     * the available types from the parent by the passed in filter.
     * <p>
     * If the filter is null then a default filter will be used according to
     * root type - see {@link #create(org.praxislive.core.Container)}.
     *
     * @param context container this will be added to
     * @param filter filtering to apply to parent result
     * @return instance
     */
    public static FilteredTypes create(Container context,
            Predicate<ComponentType> filter) {
        return create(context, filter, null, true);
    }

    /**
     * Create a FilteredTypes for the provided context, additionally filtering
     * the available types from the parent by the passed in filter, and adding
     * in types from the supplied list.
     * <p>
     * If the filter is null then a default filter will be used according to
     * root type - see {@link #create(org.praxislive.core.Container)}.
     *
     * @param context container this will be added to
     * @param filter filtering to apply to parent result
     * @param additional supplier of a list of additional types
     * @return instance
     */
    public static FilteredTypes create(Container context,
            Predicate<ComponentType> filter,
            Supplier<List<ComponentType>> additional) {
        return create(context, filter, additional, true);
    }

    /**
     * Create a FilteredTypes for the provided context, additionally filtering
     * the available types from the parent by the passed in filter, and adding
     * in types from the supplied list.
     * <p>
     * The boolean flag allows to filter out additional types added by the
     * parent, if the parent is also using an instance of FilteredTypes.
     * <p>
     * If the filter is null then a default filter will be used according to
     * root type - see {@link #create(org.praxislive.core.Container)}.
     *
     * @param context container this will be added to
     * @param filter filtering to apply to parent result
     * @param additional supplier of a list of additional types
     * @param includeParentAdditional whether to include additional types from
     * the parent
     * @return instance
     */
    public static FilteredTypes create(Container context,
            Predicate<ComponentType> filter,
            Supplier<List<ComponentType>> additional,
            boolean includeParentAdditional) {
        Objects.requireNonNull(context);
        return new FilteredTypes(context, filter, additional, includeParentAdditional);
    }

    private static class BaseDelegate implements SupportedTypes {

        private final ComponentRegistry registry;
        private final Predicate<ComponentType> baseFilter;

        private ComponentRegistry.Result registryResult;
        private Result result;

        private BaseDelegate(FilteredTypes filteredTypes) {
            this.registry = filteredTypes.context.getLookup()
                    .find(ComponentRegistry.class).orElse(null);
            if (filteredTypes.filter != null) {
                baseFilter = null;
            } else {
                baseFilter = createBaseFilter(filteredTypes.context);
            }
        }

        @Override
        public Result query() {
            if (registry == null) {
                if (result == null) {
                    result = new Result(List.of());
                }
            } else {
                var regResult = registry.query();
                if (result == null || regResult != registryResult) {
                    registryResult = regResult;
                    if (baseFilter == null) {
                        result = new Result(regResult.componentTypes());
                    } else {
                        result = new Result(regResult.componentTypes().stream()
                                .filter(baseFilter)
                                .collect(Collectors.toList()));
                    }
                }
            }
            return result;
        }

        private static Predicate<ComponentType> createBaseFilter(Container context) {
            var core = "core:";
            var category = findRootCategory(context);
            if (category != null) {
                var match = category + ":";
                return type -> type.toString().startsWith(core)
                        || type.toString().startsWith(match);
            } else {
                return type -> false;
            }
        }

        private static String findRootCategory(Container context) {
            var c = context;
            var p = c.getParent();
            while (p != null) {
                c = p;
                p = c.getParent();
            }
            var type = c.getInfo().properties().getString(ComponentInfo.KEY_COMPONENT_TYPE, "");
            if (type.startsWith("root:")) {
                return type.substring(5);
            } else {
                return null;
            }
        }

    }

}
