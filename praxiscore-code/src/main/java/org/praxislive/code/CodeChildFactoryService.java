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

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;

/**
 *
 */
public class CodeChildFactoryService implements Service {
    
    public static final String NEW_CHILD_INSTANCE = "new-child-instance";
    
    public static final ControlInfo NEW_CHILD_INSTANCE_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(PReference.info(Task.class)),
                    List.of(PReference.info(Result.class)),
                    PMap.EMPTY);

    @Override
    public Stream<String> controls() {
        return Stream.of(NEW_CHILD_INSTANCE);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        return switch (control) {
            case NEW_CHILD_INSTANCE -> NEW_CHILD_INSTANCE_INFO;
            default -> throw new IllegalArgumentException();
        };
    }
    
    public static record Task(ComponentType componentType,
        Class<? extends CodeDelegate> baseDelegate,
        LogLevel logLevel) {
        
        public Task(ComponentType componentType,
                Class<? extends CodeDelegate> baseDelegate,
                LogLevel logLevel) {
            this.componentType = Objects.requireNonNull(componentType);
            this.baseDelegate = Objects.requireNonNull(baseDelegate);
            this.logLevel = Objects.requireNonNull(logLevel);
        }
        
    }
    
    public static record Result(CodeComponent<?> component, LogBuilder log) {
        
        public Result(CodeComponent<?> component, LogBuilder log) {
            this.component = Objects.requireNonNull(component);
            this.log = Objects.requireNonNull(log);
        }
        
    }
    
}
