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
package org.praxislive.script;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.types.PError;
import org.praxislive.script.commands.CoreCommandsInstaller;

import static java.lang.System.Logger.Level;

/**
 *
 */
class ScriptExecutor {

    private static final System.Logger log = System.getLogger(ScriptExecutor.class.getName());

    private final List<StackFrame> stack;
    private final Queue<Call> queue;
    private final Env env;
    private final Map<String, Command> commandMap;
    private final Namespace rootNS;

    ScriptExecutor(Env context, final ComponentAddress ctxt) {
        this.env = context;
        stack = new LinkedList<>();
        queue = new LinkedList<>();
        commandMap = buildCommandMap();
        rootNS = new NS();
        rootNS.addVariable(Env.CONTEXT, new ConstantImpl(ctxt));
    }

    private Map<String, Command> buildCommandMap() {
        Map<String, Command> map = new HashMap<>();
        CommandInstaller installer = new CoreCommandsInstaller();
        installer.install(map);
        Lookup.SYSTEM.findAll(CommandInstaller.class).forEach(cmds -> cmds.install(map));
        return map;
    }

    public void queueEvalCall(Call call) {
        queue.offer(call);
        if (stack.isEmpty()) {
            checkAndStartEval();
        }
    }

    public void flushEvalQueue() {
        // flush stack
        stack.clear();
        while (!queue.isEmpty()) {
            Call call = queue.poll();
            env.getPacketRouter().route(call.error(PError.of("")));
        }

    }

    public void processScriptCall(Call call) {
        log.log(Level.TRACE, () -> "processScriptCall - received :\n" + call);
        if (!stack.isEmpty()) {
            stack.get(0).postResponse(call);
            processStack();
        }
        if (stack.isEmpty()) {
            checkAndStartEval();
        }
    }

    private void processStack() {
        while (!stack.isEmpty()) {
            StackFrame current = stack.get(0);
            log.log(Level.TRACE, () -> "Processing stack : " + current.getClass()
                    + "\n  Stack Size : " + stack.size());

            // if incomplete do round of processing
            if (current.getState() == StackFrame.State.Incomplete) {
                StackFrame child = current.process(env);
                if (child != null) {
                    log.log(Level.TRACE, () -> "Pushing to stack" + child.getClass());
                    stack.add(0, child);
                    continue;
                }
            }

            // now check state again and pop if necessary
            StackFrame.State state = current.getState();
            if (state == StackFrame.State.Incomplete) {
                return;
            } else {
                var args = current.result();
                log.log(Level.TRACE, () -> "Stack frame complete : " + current.getClass()
                        + "\n  Result : " + args + "\n  Stack Size : " + stack.size());
                stack.remove(0);
                if (!stack.isEmpty()) {
                    log.log(Level.TRACE, "Posting result up stack");
                    stack.get(0).postResponse(state, args);
                } else {
                    Call call = queue.poll();
                    if (state == StackFrame.State.OK) {
                        log.log(Level.TRACE, "Sending OK return call");
                        call = call.reply(args);
                    } else {
                        log.log(Level.TRACE, "Sending Error return call");
                        call = call.error(args);
                    }
                    env.getPacketRouter().route(call);
                }
            }
        }
    }

    private void checkAndStartEval() {
        while (!queue.isEmpty()) {
            Call call = queue.peek();
            var args = call.args();
            try {
                var script = args.get(0).toString();
                var stackFrame = ScriptStackFrame.forScript(rootNS, script)
                        .inline()
                        .build();
                stack.add(0, stackFrame);
                processStack();
                break;
            } catch (Exception ex) {
                queue.poll();
                env.getPacketRouter().route(
                        call.error(PError.of(ex)));
            }
        }
    }

    private class NS implements Namespace {

        private NS parent;
        private Map<String, Variable> variables;

        private NS() {
            this(null);
        }

        private NS(NS parent) {
            this.parent = parent;
            variables = new HashMap<>();
        }

        @Override
        public Variable getVariable(String id) {
            Variable var = variables.get(id);
            if (var == null && parent != null) {
                return parent.getVariable(id);
            } else {
                return var;
            }
        }

        @Override
        public void addVariable(String id, Variable var) {
            if (variables.containsKey(id)) {
                throw new IllegalArgumentException();
            }
            variables.put(id, var);
        }

        @Override
        public Command getCommand(String id) {
            return commandMap.get(id);
        }

        @Override
        public void addCommand(String id, Command cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Namespace createChild() {
            return new NS(this);
        }
    }
}
