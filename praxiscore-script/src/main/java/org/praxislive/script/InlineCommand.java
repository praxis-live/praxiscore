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

import java.util.List;
import org.praxislive.core.Call;
import org.praxislive.core.Value;
import org.praxislive.core.types.PReference;

/**
 * Simple subtype of {@link Command} that can be executed and produce a result
 * immediately (without child stack frames or making calls).
 */
public interface InlineCommand extends Command {

    /**
     * Execute the command with the given environment, namespace and arguments.
     *
     * @param context current environment
     * @param namespace current namespace
     * @param args arguments
     * @return result
     * @throws Exception on error
     */
    public List<Value> process(Env context, Namespace namespace,
            List<Value> args)
            throws Exception;

    /**
     * Create a StackFrame to execute the command with the provided Namespace
     * and arguments.
     * <p>
     * The default implementation of this method returns an
     * {@link InlineStackFrame} with this command, and provided namespace and
     * arguments. Implementations can override to further validate the context
     * or delegate to another command.
     *
     *
     * @param namespace current namespace
     * @param args arguments
     * @return stack frame to execute command with provided arguments
     * @throws Exception if stack frame cannot be created
     */
    @Override
    public default InlineStackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
        return new InlineStackFrame(this, namespace, args);
    }

    /**
     * A default implementation of StackFrame for use by InlineCommand
     * implementations.
     */
    public static final class InlineStackFrame implements StackFrame {

        private final InlineCommand command;
        private final Namespace namespace;
        private final List<Value> args;
        private State state;
        private List<Value> result;

        InlineStackFrame(InlineCommand command, Namespace namespace, List<Value> args) {
            this.command = command;
            this.namespace = namespace;
            this.args = args;
            state = State.Incomplete;
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public StackFrame process(Env env) {
            if (state == State.Incomplete) {
                try {
                    result = command.process(env, namespace, args);
                    state = State.OK;
                } catch (Exception ex) {
                    result = List.of(PReference.of(ex));
                    state = State.Error;
                }
            }
            return null;
        }

        @Override
        public void postResponse(Call call) {
            throw new IllegalStateException();
        }

        @Override
        public void postResponse(State state, List<Value> args) {
            throw new IllegalStateException();
        }

        @Override
        public List<Value> result() {
            if (result == null) {
                throw new IllegalStateException();
            }
            return result;
        }

    }
}
