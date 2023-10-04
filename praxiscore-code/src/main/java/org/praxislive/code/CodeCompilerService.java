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

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PMap;

/**
 *
 */
public class CodeCompilerService implements Service {

    public static final String COMPILE = "compile";
    public static final ControlInfo COMPILE_INFO = 
            ControlInfo.createFunctionInfo(
                    List.of(PMap.info()),
                    List.of(PMap.info()),
                    PMap.EMPTY);

    public static final ComponentInfo API_INFO = Info.component(cmp -> cmp
            .protocol(CodeCompilerService.class)
            .control(COMPILE, COMPILE_INFO)
    );
    
    public static final String KEY_SOURCES = "sources";
    
    public static final String KEY_SHARED_CLASSES = "shared-classes";
    
    public static final String KEY_LOG_LEVEL = "log-level";
    
    // response keys
    public static final String KEY_CLASSES = "classes";
    
    public static final String KEY_LOG = "log";
    
    @Override
    public Stream<String> controls() {
        return Stream.of(COMPILE);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        if (COMPILE.equals(control)) {
            return COMPILE_INFO;
        }
        throw new IllegalArgumentException();
    }
}
