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

import java.util.stream.Stream;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.Service;

/**
 * A factory service for code root components. Provides the same API as
 * {@link RootFactoryService} for use as a redirect with
 * {@link ComponentFactory#rootRedirect()}.
 *
 */
public final class CodeRootFactoryService implements Service {

    /**
     * Control ID of the new root instance control.
     */
    public final static String NEW_ROOT_INSTANCE
            = RootFactoryService.NEW_ROOT_INSTANCE;

    /**
     * ControlInfo of the new root instance control.
     */
    public final static ControlInfo NEW_ROOT_INSTANCE_INFO
            = RootFactoryService.NEW_ROOT_INSTANCE_INFO;

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
