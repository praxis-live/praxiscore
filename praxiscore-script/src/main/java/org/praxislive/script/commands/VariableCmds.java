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

import java.util.List;
import java.util.Map;
import org.praxislive.core.Value;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.Variable;

import static java.lang.System.Logger.Level;

/**
 *
 */
public class VariableCmds implements CommandInstaller {

    private static final VariableCmds instance = new VariableCmds();
    private static final Command SET = new Set();
    private final static System.Logger log = System.getLogger(VariableCmds.class.getName());

    private VariableCmds() {
    }

    @Override
    public void install(Map<String, Command> commands) {
        commands.put("set", SET);
    }

    public static VariableCmds getInstance() {
        return instance;
    }

    private static class Set implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 2) {
                throw new Exception();
            }
            String varName = args.get(0).toString();
            Value val = args.get(1);
            Variable var = namespace.getVariable(varName);
            if (var != null) {
                var.setValue(val);
            } else {
                log.log(Level.TRACE, () -> "SET COMMAND : Adding variable " + varName + " to namespace " + namespace);
                namespace.createVariable(varName, val);
            }
            return List.of(val);

        }
    }
}
