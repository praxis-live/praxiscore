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

import java.io.File;
import java.util.List;
import java.util.Map;
import org.praxislive.core.Value;
import org.praxislive.core.types.PString;
import org.praxislive.core.types.PResource;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;

/**
 *
 */
public class ResourceCmds implements CommandInstaller {

    private final static ResourceCmds instance = new ResourceCmds();

    private final static Command LOAD = new LoadCmd();

    private ResourceCmds() {
    }

    @Override
    public void install(Map<String, Command> commands) {
        commands.put("load", LOAD);
    }

    public static ResourceCmds getInstance() {
        return instance;
    }

    private static class LoadCmd implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new Exception();
            }
            try {
                File f = new File(PResource.from(args.get(0)).orElseThrow().value());
                String s = Utils.loadStringFromFile(f);
                return List.of(PString.of(s));
            } catch (Exception ex) {
                throw new Exception(ex);
            }
        }

    }
}
