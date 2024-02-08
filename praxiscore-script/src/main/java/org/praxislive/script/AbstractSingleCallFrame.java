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
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;

/**
 * An abstract {@link StackFrame} for commands that need to make a stack frame
 * that makes a single call and processes its response.
 * <p>
 * Subclasses should implement
 * {@link #createCall(org.praxislive.script.Env, java.util.List)} to create the
 * call when required. Subclasses may additionally override
 * {@link #processResult(java.util.List)} if the need to alter the return values
 * from the call.
 */
public abstract class AbstractSingleCallFrame implements StackFrame {

    private Namespace namespace;
    private List<Value> args;
    private State state;
    private Call call;
    private List<Value> result;

    protected AbstractSingleCallFrame(Namespace namespace, List<Value> args) {
        if (namespace == null || args == null) {
            throw new NullPointerException();
        }
        this.namespace = namespace;
        this.args = args;
        state = State.Incomplete;
    }

    @Override
    public final State getState() {
        return state;
    }

    public final Namespace getNamespace() {
        return namespace;
    }

    @Override
    public final StackFrame process(Env env) {
        if (state == State.Incomplete && call == null) {
            try {
                call = createCall(env, args);
                if (call == null || !(call.isReplyRequired())) {
                    throw new IllegalStateException("Invalid call");
                }
                env.getPacketRouter().route(call);
            } catch (Exception ex) {
                result = List.of(PReference.of(ex));
                state = State.Error;
            }
        }
        return null;
    }

    @Override
    public final void postResponse(Call response) {
        if (call != null && response.matchID() == call.matchID()) {
            call = null;
            result = response.args();
            if (response.isReply()) {
                try {
                    result = processResult(result);
                    state = State.OK;
                } catch (Exception ex) {
                    result = List.of(PError.of(ex));
                    state = State.Error;
                }
            } else {
                state = State.Error;
            }
        }
    }

    @Override
    public final void postResponse(State state, List<Value> args) {
        throw new IllegalStateException();
    }

    @Override
    public final List<Value> result() {
        if (result == null) {
            throw new IllegalStateException();
        }
        return result;
    }

    /**
     * Create the Call. The call must use {@link Env#getAddress()} as the from
     * address, and require a response.
     *
     * @param env environment for address, time, etc.
     * @param args command arguments
     * @return call
     * @throws Exception on error
     */
    protected abstract Call createCall(Env env, List<Value> args) throws Exception;

    /**
     * Process the result from the call on a successful response. Unless this
     * method is overridden the result of the stack frame will be the result of
     * the call.
     *
     * @param result successful result from call
     * @return processed result
     */
    protected List<Value> processResult(List<Value> result) {
        return result;
    }

}
