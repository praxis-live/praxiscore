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
package org.praxislive.code;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.praxislive.base.AbstractAsyncControl;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.OrderedMap;
import org.praxislive.core.Value;
import org.praxislive.core.VetoException;
import org.praxislive.core.services.ComponentFactoryService;
import org.praxislive.core.types.PReference;

/**
 *
 */
final class CodeContainerSupport {

    private CodeContainerSupport() {
    }

    static Predicate<ComponentType> globTypeFilter(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' ->
                    regex.append(".*");
                case '?' ->
                    regex.append('.');
                case '|' ->
                    regex.append('|');
                case '_' ->
                    regex.append('_');
                case '-' ->
                    regex.append("\\-");
                case ':' ->
                    regex.append(':');
                default -> {
                    if (Character.isJavaIdentifierPart(c)) {
                        regex.append(c);
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            }
        }
        Pattern pattern = Pattern.compile(regex.toString());
        return type -> pattern.matcher(type.toString()).matches();
    }

    static TypesInfo defaultRootTypesInfo() {
        return TypesInfo.DEFAULT_ROOT_INFO;
    }

    static TypesInfo defaultContainerTypesInfo() {
        return TypesInfo.DEFAULT_CONTAINER_INFO;
    }

    static TypesInfo analyseMethod(Method method, boolean isRoot) {
        var typesInfoAnn = method.getAnnotation(ContainerDelegateAPI.SupportedTypes.class);
        if (typesInfoAnn != null) {
            String filter = typesInfoAnn.filter();
            if (isRoot && filter.isBlank()) {
                filter = "core:*";
            }
            boolean system = typesInfoAnn.system();
            OrderedMap<ComponentType, Class<? extends CodeDelegate>> custom;
            if (typesInfoAnn.custom().length == 0) {
                custom = OrderedMap.of();
            } else {
                Map<ComponentType, Class<? extends CodeDelegate>> customMap
                        = new LinkedHashMap<>();
                for (var customAnn : typesInfoAnn.custom()) {
                    customMap.put(ComponentType.of(customAnn.type()), customAnn.base());
                }
                custom = OrderedMap.copyOf(customMap);
            }
            return new TypesInfo(filter, system, custom);
        }
        return null;
    }

    static final class TypesInfo {

        private static final TypesInfo DEFAULT_ROOT_INFO = new TypesInfo("core:*", true, OrderedMap.of());
        private static final TypesInfo DEFAULT_CONTAINER_INFO = new TypesInfo("", true, OrderedMap.of());

        private final String filterDefinition;
        private final Predicate<ComponentType> filter;
        private final boolean includeSystem;
        private final OrderedMap<ComponentType, Class<? extends CodeDelegate>> customTypes;

        private TypesInfo(String filterDefinition,
                boolean includeSystem,
                OrderedMap<ComponentType, Class<? extends CodeDelegate>> customTypes) {
            this.filterDefinition = filterDefinition.strip();
            this.filter = filterDefinition.isBlank() || "*".equals(this.filterDefinition)
                    ? t -> true : globTypeFilter(filterDefinition);
            this.includeSystem = includeSystem;
            this.customTypes = customTypes;
        }

        String filterDefinition() {
            return filterDefinition;
        }

        Predicate<ComponentType> filter() {
            return filter;
        }

        OrderedMap<ComponentType, Class<? extends CodeDelegate>> customTypes() {
            return customTypes;
        }

        boolean includeSystem() {
            return includeSystem;
        }

    }

    static final class ChildControl extends AbstractAsyncControl {

        private final CodeComponent<?> component;
        private final AddChildLink childLink;
        private final RecordChildTypeLink recordLink;
        private final LinkedHashMap<String, ComponentType> customChildren;

        private TypesInfo typesInfo;

        ChildControl(CodeComponent<?> component,
                AddChildLink childLink,
                RecordChildTypeLink recordLink) {
            this.component = Objects.requireNonNull(component);
            this.childLink = Objects.requireNonNull(childLink);
            this.recordLink = Objects.requireNonNull(recordLink);
            this.customChildren = new LinkedHashMap<>();
            typesInfo = defaultContainerTypesInfo();
        }

        @Override
        protected Call processInvoke(Call call) throws Exception {
            List<Value> args = call.args();
            if (args.size() < 2) {
                throw new IllegalArgumentException("Invalid arguments");
            }
            String id = args.get(0).toString();
            if (!ComponentAddress.isValidID(id)) {
                throw new IllegalArgumentException("Invalid Component ID");
            }
            ComponentType type = ComponentType.from(args.get(1))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid component type"));
            if (typesInfo.customTypes().containsKey(type)) {
                Class<? extends CodeDelegate> baseDelegate = typesInfo.customTypes().get(type);
                ControlAddress to = ControlAddress.of(
                        component.findService(CodeChildFactoryService.class),
                        CodeChildFactoryService.NEW_CHILD_INSTANCE
                );
                CodeChildFactoryService.Task task
                        = new CodeChildFactoryService.Task(type, baseDelegate,
                                component.getCodeContext().getLogLevel());
                customChildren.put(id, type);
                return Call.create(to, call.to(), call.time(), PReference.of(task));
            } else {
                ControlAddress to = ControlAddress.of(
                        component.findService(ComponentFactoryService.class),
                        ComponentFactoryService.NEW_INSTANCE);
                return Call.create(to, call.to(), call.time(), args.get(1));
            }

        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            List<Value> args = call.args();
            if (args.size() < 1) {
                throw new IllegalArgumentException("Invalid response");
            }
            Component child;
            boolean checkShared = false;
            if (CodeChildFactoryService.NEW_CHILD_INSTANCE.equals(call.from().controlID())) {
                CodeChildFactoryService.Result result = PReference.from(args.get(0))
                        .flatMap(r -> r.as(CodeChildFactoryService.Result.class))
                        .orElseThrow();
                if (!result.log().isEmpty()) {
                    component.getCodeContext().log(result.log());
                }
                child = result.component();
                checkShared = true;
            } else {
                child = PReference.from(args.get(0))
                        .flatMap(r -> r.as(Component.class))
                        .orElseThrow();
            }
            Call active = getActiveCall();
            String id = active.args().get(0).toString();
            ComponentType type = ComponentType.from(active.args().get(1)).orElse(null);
            childLink.addChild(id, child);
            recordLink.recordChildType(child, type);
            if (checkShared && child instanceof CodeComponent<?> cc) {
                checkAndRegisterShared(cc);
            }
            return active.reply();
        }

        @Override
        protected Call processError(Call call) throws Exception {
            if (CodeChildFactoryService.NEW_CHILD_INSTANCE.equals(call.from().controlID())) {
                ComponentType.from(getActiveCall().args().get(1))
                        .ifPresent(customChildren::remove);
            }
            return super.processError(call);
        }

        void notifyChildRemoved(String child) {
            customChildren.remove(child);
        }

        void install(TypesInfo typesInfo) {
            this.typesInfo = typesInfo;
        }

        boolean isCompatible(TypesInfo typesInfo) {
            if (customChildren.isEmpty()) {
                return true;
            }
            var currentCustomTypes = this.typesInfo.customTypes();
            var proposedCustomTypes = typesInfo.customTypes();

            Set<ComponentType> removedOrChanged = new HashSet<>();
            currentCustomTypes.forEach((type, cls) -> {
                var proposedCls = proposedCustomTypes.get(type);
                if (proposedCls == null || !cls.getName().equals(proposedCls.getName())) {
                    removedOrChanged.add(type);
                }
            });
            for (ComponentType type : removedOrChanged) {
                if (customChildren.values().contains(type)) {
                    return false;
                }
            }
            return true;

        }

        boolean supportedSystemType(ComponentType type) {
            return typesInfo.includeSystem() && typesInfo.filter().test(type);
        }

        List<ComponentType> additionalTypes() {
            return typesInfo.customTypes.keys();
        }

        void checkAndRegisterShared(CodeComponent<?> child) {
            Control control = child.getControl("code");
            if (control instanceof CodeProperty<?> code) {
                ControlAddress ad = ControlAddress.of(child.getAddress(), "code");
                code.checkSharedContext(ad);
            }
        }

    }

    @FunctionalInterface
    static interface AddChildLink {

        void addChild(String id, Component child) throws VetoException;

    }

    @FunctionalInterface
    static interface RecordChildTypeLink {

        void recordChildType(Component child, ComponentType type);

    }

}
