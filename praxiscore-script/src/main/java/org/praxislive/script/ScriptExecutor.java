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
package org.praxislive.script;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.types.PError;

import static java.lang.System.Logger.Level;

import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.types.PString;

/**
 *
 */
class ScriptExecutor {

    private static final System.Logger LOG = System.getLogger(ScriptExecutor.class.getName());

    private final List<StackFrame> stack;
    private final Queue<Call> queue;
    private final Env env;
    private final Namespace rootNS;
    private final ComponentAddress context;

    private Namespace namespace;

    ScriptExecutor(Env env,
            Namespace rootNS,
            ComponentAddress context) {
        this.env = new EnvWrapper(env);
        this.rootNS = rootNS;
        this.context = context;
        stack = new LinkedList<>();
        queue = new LinkedList<>();
        namespace = createNamespace();
    }

    private Namespace createNamespace() {
        Namespace ns = rootNS.createChild();
        ns.createConstant(Env.CONTEXT, context);
        return ns;
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
        namespace = createNamespace();
    }

    public void processScriptCall(Call call) {
        LOG.log(Level.TRACE, () -> "processScriptCall - received :\n" + call);
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
            LOG.log(Level.TRACE, () -> "Processing stack : " + current.getClass()
                    + "\n  Stack Size : " + stack.size());

            // if incomplete do round of processing
            if (current.getState() == StackFrame.State.Incomplete) {
                StackFrame child = current.process(env);
                if (child != null) {
                    LOG.log(Level.TRACE, () -> "Pushing to stack" + child.getClass());
                    stack.add(0, child);
                    continue;
                }
            }

            // now check state again and pop if necessary
            StackFrame.State state = current.getState();
            if (state == StackFrame.State.Incomplete) {
                return;
            } else {
                List<Value> args = current.result();
                LOG.log(Level.TRACE, () -> "Stack frame complete : " + current.getClass()
                        + "\n  Result : " + args + "\n  Stack Size : " + stack.size());
                stack.remove(0);
                if (!stack.isEmpty()) {
                    LOG.log(Level.TRACE, "Posting result up stack");
                    stack.get(0).postResponse(state, args);
                } else {
                    Call call = queue.poll();
                    if (state == StackFrame.State.OK) {
                        LOG.log(Level.TRACE, "Sending OK return call");
                        call = call.reply(args);
                    } else {
                        LOG.log(Level.TRACE, "Sending Error return call");
                        Variable error = namespace.getOrCreateVariable(Env.ERROR, PString.EMPTY);
                        if (!args.isEmpty()) {
                            error.setValue(args.getFirst());
                        }
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
                String script = args.get(0).toString();
                ScriptStackFrame stackFrame = ScriptStackFrame
                        .forScript(namespace, script)
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

    private class EnvWrapper implements Env {

        private final Env delegate;

        private EnvWrapper(Env delegate) {
            this.delegate = delegate;
        }

        @Override
        public Lookup getLookup() {
            return delegate.getLookup();
        }

        @Override
        public long getTime() {
            return delegate.getTime();
        }

        @Override
        public PacketRouter getPacketRouter() {
            return delegate.getPacketRouter();
        }

        @Override
        public ControlAddress getAddress() {
            return delegate.getAddress();
        }

    }

}
