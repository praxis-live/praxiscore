/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith.
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.services.LogLevel;

/**
 *
 */
public class SharedCodeContext {

    private final SharedCodeProperty property;
    private final Map<ControlAddress, CodeProperty<?>> dependents;

    private ClassLoader sharedClasses;

    SharedCodeContext(SharedCodeProperty property) {
        this.property = property;
        this.dependents = new LinkedHashMap<>();
    }

    ClassLoader getSharedClassLoader() {
        return sharedClasses;
    }

    boolean checkDependency(ControlAddress address, CodeProperty<?> property) {
        Class<?> cls = property.getDelegateClass();
        if (cls == null || cls.getClassLoader().getParent() != sharedClasses) {
            dependents.remove(address);
            return false;
        } else {
            dependents.put(address, property);
            // @TODO invalidate / retry active call in shared code property?
            return true;
        }
    }

    void clearDependency(CodeProperty<?> property) {
        dependents.values().removeIf(v -> v == property);
    }

    Map<ControlAddress, SharedCodeService.DependentTask<?>> createDependentTasks() {
        return dependents.entrySet().stream()
                .map(e -> Map.entry(e.getKey(),
                        e.getValue().createSharedCodeReloadTask()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    void update(SharedCodeService.Result result) throws Exception {
        if (!result.getDependents().keySet().containsAll(dependents.keySet())) {
            throw new IllegalStateException("Dependency missing from reload");
        }
        sharedClasses = result.getSharedClasses();
        result.getDependents().forEach((ad, dep) -> {
            try {
                CodeProperty<?> code = dependents.get(ad);
                if (code != null) {
                    if (code.getDelegateClass() != dep.getExisting()) {
                        result.getLog().log(LogLevel.WARNING,
                                "Component code was changed during shared code compilation");
                    }
                    code.installContext(ad, dep.getContext());
                }
            } catch (Exception ex) {
                // Just log - can't stop halfway through
                result.getLog().log(LogLevel.ERROR, ex);
            } catch (Throwable t) {
                // possible linkage or method errors
                result.getLog().log(LogLevel.ERROR, new IllegalStateException(t));
            }
        });
    }

}
