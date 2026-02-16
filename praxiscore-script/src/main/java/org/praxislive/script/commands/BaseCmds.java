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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.praxislive.core.Value;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.Variable;

import static java.lang.System.Logger.Level;

/**
 *
 */
class BaseCmds {

    private static final System.Logger LOG = System.getLogger(BaseCmds.class.getName());

    private static final Constant CONSTANT = new Constant();
    private static final Set SET = new Set();
    private static final Var VAR = new Var();
    private static final Echo ECHO = new Echo();
    private static final Print PRINT = new Print();

    private BaseCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.put("constant", CONSTANT);
        commands.put("set", SET);
        commands.put("var", VAR);
        commands.put("echo", ECHO);
        commands.put("print", PRINT);
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
                LOG.log(Level.TRACE, () -> "SET COMMAND : Adding variable " + varName + " to namespace " + namespace);
                namespace.createVariable(varName, val);
            }
            return List.of(val);

        }
    }

    private static class Constant implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 2) {
                throw new Exception();
            }
            String varName = args.get(0).toString();
            Value val = args.get(1);
            namespace.createConstant(varName, val);
            return List.of(val);

        }
    }

    private static class Var implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 2) {
                throw new Exception();
            }
            String varName = args.get(0).toString();
            Value val = args.get(1);
            namespace.createVariable(varName, val);
            return List.of(val);

        }
    }

    private static class Echo implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.isEmpty()) {
                return List.of(PString.EMPTY);
            } else if (args.size() == 1) {
                return List.of(args.get(0));
            } else {
                return List.of(PString.of(
                        args.stream()
                                .map(Value::toString)
                                .collect(Collectors.joining())));
            }
        }

    }

    private static class Print implements InlineCommand {

        @Override
        public List<Value> process(Env context, Namespace namespace, List<Value> args) throws Exception {
            if (args.size() != 1) {
                throw new Exception();
            }
            return List.of(PString.of(args.get(0).print()));
        }

    }

}
