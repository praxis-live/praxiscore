/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Neil C Smith.
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

package org.praxislive.script.impl;

import java.util.List;
import org.praxislive.core.Call;
import org.praxislive.core.Value;
import org.praxislive.core.types.PReference;
import org.praxislive.script.Env;
import org.praxislive.script.ExecutionException;
import org.praxislive.script.InlineCommand;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;

/**
 *
 */
public abstract class AbstractInlineCommand implements InlineCommand {



    @Override
    public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws ExecutionException {
        return new InlineStackFrame(namespace, args);
    }

    private class InlineStackFrame implements StackFrame {

        private final Namespace namespace;
        private final List<Value> args;
        private State state;
        private List<Value> result;

        private InlineStackFrame(Namespace namespace, List<Value> args) {
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
                    result = AbstractInlineCommand.this.process(env, namespace, args);
                    state = State.OK;
                } catch (ExecutionException ex) {
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
