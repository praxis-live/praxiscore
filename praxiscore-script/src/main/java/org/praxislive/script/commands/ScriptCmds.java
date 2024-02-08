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
import org.praxislive.core.syntax.InvalidSyntaxException;
import org.praxislive.core.types.PResource;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;
import org.praxislive.script.ast.RootNode;
import org.praxislive.script.ast.ScriptParser;

/**
 *
 */
public class ScriptCmds implements CommandInstaller {

    private final static ScriptCmds instance = new ScriptCmds();
    public final static Command EVAL = new Eval();
    public final static Command INLINE_EVAL = new InlineEval();
    public final static Command INCLUDE = new Include();

    private ScriptCmds() {
    }

    @Override
    public void install(Map<String, Command> commands) {
        commands.put("eval", EVAL);
        commands.put("include", INCLUDE);
    }

    public static ScriptCmds getInstance() {
        return instance;
    }

    private static class Eval implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args)
                throws Exception {
            if (args.size() != 1) {
                throw new Exception();
            }
            String script = args.get(0).toString();
            try {
                RootNode astRoot = ScriptParser.getInstance().parse(script);
                return new EvalStackFrame(namespace.createChild(), astRoot);
            } catch (InvalidSyntaxException ex) {
                throw new Exception(ex);
            }
        }
    }

    private static class InlineEval implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args)
                throws Exception {
            if (args.size() != 1) {
                throw new Exception();
            }
            String script = args.get(0).toString();
            try {
                RootNode astRoot = ScriptParser.getInstance().parse(script);
                return new EvalStackFrame(namespace, astRoot);
            } catch (InvalidSyntaxException ex) {
                throw new Exception(ex);
            }
        }
    }

    private static class Include implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            // @TODO - should load in background - call to
            if (args.size() != 1) {
                throw new Exception();
            }
            try {
                PResource res = PResource.from(args.get(0)).orElseThrow();
                File file = new File(res.value());
                String script = Utils.loadStringFromFile(file);
                RootNode astRoot = ScriptParser.getInstance().parse(script);
                return new EvalStackFrame(namespace.createChild(), astRoot);
            } catch (Exception ex) {
                throw new Exception(ex);
            }

        }

    }
}
