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
import java.util.Map;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Value;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Env;
import org.praxislive.script.ExecutionException;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;
import org.praxislive.script.impl.AbstractSingleCallFrame;

/**
 *
 */
public class HubNetCommands implements CommandInstaller {

    private final static ConfigurationCommand HUB_CONFIGURE = new ConfigurationCommand();

    @Override
    public void install(Map<String, Command> commands) {
        commands.put("hub", HUB_CONFIGURE);
        commands.put("hub-configure", HUB_CONFIGURE);
    }

    private final static class ConfigurationCommand implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws ExecutionException {
            return new AbstractSingleCallFrame(namespace, args) {
                @Override
                protected Call createCall(Env env, List<Value> args) throws Exception {
                    ComponentAddress service = env.getLookup().find(Services.class)
                            .flatMap(sm -> sm.locate(HubConfigurationService.class))
                            .orElseThrow(ServiceUnavailableException::new);
                    ControlAddress to = ControlAddress.of(service, HubConfigurationService.HUB_CONFIGURE);
                    return Call.create(to, env.getAddress(), env.getTime(), args);
                }
            };
        }

    }

}
