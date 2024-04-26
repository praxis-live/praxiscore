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

package org.praxislive.script.commands;

import java.util.Map;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;

/**
 *
 * 
 */
public class CoreCommandsInstaller implements CommandInstaller {

    @Override
    public void install(Map<String, Command> commands) {
        BaseCmds.install(commands);
        ArrayCmds.install(commands);
        AtCmds.install(commands);
        ConnectionCmds.install(commands);
        FileCmds.install(commands);
        ScriptCmds.install(commands);
    }

}
