/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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
import java.util.ResourceBundle;
import org.praxislive.core.Value;
import org.praxislive.core.services.LogLevel;
import org.praxislive.script.*;

/**
 *
 */
public class HubNetCommands implements CommandInstaller {

    private static final ResourceBundle MESSAGES
            = ResourceBundle.getBundle(HubNetCommands.class.getPackageName() + ".Messages");

    private final static Map<String, Command> COMMANDS = Map.of(
            "hub", new ConfigurationCommand(false),
            "hub-configure", new ConfigurationCommand(true)
    );

    @Override
    public void install(Map<String, Command> commands) {
        commands.putAll(COMMANDS);
    }

    private final static class ConfigurationCommand implements Command {

        private final boolean deprecated;

        private ConfigurationCommand(boolean deprecated) {
            this.deprecated = deprecated;
        }

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            StackFrame serviceCall = StackFrame.serviceCall(
                    HubConfigurationService.class,
                    HubConfigurationService.HUB_CONFIGURE, args);
            if (deprecated) {
                return StackFrame.log(LogLevel.WARNING,
                        MESSAGES.getString("hub-configure.deprecation"))
                        .andThen(v -> serviceCall);
            } else {
                return serviceCall;
            }
        }

        @Override
        public String description() {
            return MESSAGES.getString("hub.description");
        }

    }

}
