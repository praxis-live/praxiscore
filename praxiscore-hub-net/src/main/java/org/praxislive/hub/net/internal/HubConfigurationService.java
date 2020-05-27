/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
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
package org.praxislive.hub.net.internal;

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PMap;

/**
 *
 */
public class HubConfigurationService implements Service {
    
    public static final String HUB_CONFIGURE = "hub-configure";
    public static final ControlInfo HUB_CONFIGURE_INFO =
            ControlInfo.createFunctionInfo(List.of(PMap.info()),
                    List.of(),
                    PMap.EMPTY);

    @Override
    public Stream<String> controls() {
        return Stream.of(HUB_CONFIGURE);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        if (HUB_CONFIGURE.equals(control)) {
            return HUB_CONFIGURE_INFO;
        }
        throw new IllegalArgumentException();
    }
    
}
