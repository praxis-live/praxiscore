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
package org.praxislive.script.commands;

import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;

/**
 * Command installer for core script commands.
 */
public class CoreCommands implements CommandInstaller {

    private static final ResourceBundle MESSAGES
            = ResourceBundle.getBundle(CoreCommands.class.getPackageName() + ".Messages");

    @Override
    public void install(Map<String, Command> commands) {
        BaseCmds.install(commands);
        ArrayCmds.install(commands);
        AtCmds.install(commands);
        ConnectionCmds.install(commands);
        FileCmds.install(commands);
        InfoCmds.install(commands);
        IOCmds.install(commands);
        JavaCmds.install(commands);
        MapCmds.install(commands);
        OpCmds.install(commands);
        ProcessCmds.install(commands);
        ScriptCmds.install(commands);
        StringCmds.install(commands);
        SystemCmds.install(commands);
    }

    static String message(String key) {
        return MESSAGES.getString(key);
    }

    static String message(String key, Object... arguments) {
        return MessageFormat.format(MESSAGES.getString(key), arguments);
    }

}
