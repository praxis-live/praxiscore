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
package org.praxislive.core.services;

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Root;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;

/**
 * A {@link Service} for creating new root instances. The implementation of this
 * service will discover all available {@link ComponentFactory} and either
 * create an instance of the component via
 * {@link ComponentFactory#createRoot(org.praxislive.core.ComponentType)} or
 * delegate creation to the correct {@link ComponentFactory#rootRedirect()}.
 */
public class RootFactoryService implements Service {

    /**
     * Control ID of the new root instance control.
     */
    public final static String NEW_ROOT_INSTANCE = "new-root-instance";
    
    /**
     * ControlInfo of the new root instance control.
     */
    public final static ControlInfo NEW_ROOT_INSTANCE_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(ComponentType.info()),
                    List.of(PReference.info(Root.class)),
                    PMap.EMPTY);

    @Override
    public Stream<String> controls() {
        return Stream.of(NEW_ROOT_INSTANCE);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        if (NEW_ROOT_INSTANCE.equals(control)) {
            return NEW_ROOT_INSTANCE_INFO;
        }
        throw new IllegalArgumentException();
    }
}
