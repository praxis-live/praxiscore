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

/**
 * A StackFrame used within the script executor pointing to the currently
 * executing command. A StackFrame is created for each execution of a Command
 * using
 * {@link Command#createStackFrame(org.praxislive.script.Namespace, java.util.List)}.
 * <p>
 * A StackFrame should always start off in {@link State#Incomplete}. The script
 * executor will call {@link #process(org.praxislive.script.Env)}. During
 * processing the StackFrame may evaluate a result, make one or more Calls, or
 * create a child StackFrame (eg. from evaluation of another command).
 * <p>
 * If a Call has been made, the state should remain incomplete. Any returning
 * call will be passed into {@link #postResponse(org.praxislive.core.Call)}.
 * <p>
 * If a child StackFrame has been returned, the result of its processing will be
 * passed into
 * {@link #postResponse(org.praxislive.script.StackFrame.State, java.util.List)}.
 * <p>
 * Once a response has been posted, the script executor will check if the
 * StackFrame is still marked incomplete. If it is still incomplete, the
 * executor will call {@link #process(org.praxislive.script.Env)} again. If it
 * has any other state, the state and result will be posted up to the parent
 * StackFrame if there is one, or returned as the script result.
 */
public interface StackFrame {

    /**
     * Possible states of a StackFrame. All StackFrames start in an incomplete
     * state.
     */
    public static enum State {

        /**
         * Incomplete and requires processing. All StackFrames begin in this
         * state.
         */
        Incomplete,
        /**
         * Processing finished successfully, and the {@link #result()} is
         * available.
         */
        OK,
        /**
         * Processing finished with an error.
         */
        Error,
        /**
         * Special state to control stack unwinding.
         *
         */
        Break,
        /**
         * Special state to control stack unwinding.
         */
        Continue
    };

    /**
     * Get the current state of this StackFrame.
     *
     * @return current state
     */
    public State getState();

    /**
     * Process the StackFrame. After processing, the StackFrame should have made
     * one or more Calls, returned a child StackFrame, or moved out of the
     * Incomplete state.
     * <p>
     * Process may be called multiple times if the state is still incomplete
     * after this method returns and a response has been posted.
     *
     * @param env processing environment
     * @return child StackFrame or null
     */
    public StackFrame process(Env env);

    /**
     * Used by the script executor to post the result of a Call. The StackFrame
     * should validate the match ID of the response call against any pending
     * calls before processing the call state or arguments.
     * <p>
     * If the state is still incomplete after a response is posted,
     * {@link #process(org.praxislive.script.Env)} will be called again.
     *
     * @param call response call
     * @throws IllegalStateException if the state is not incomplete or a call
     * response is not expected
     */
    public void postResponse(Call call);

    /**
     * Used by the script executor to post the result of a child StackFrame
     * returned by {@link #process(org.praxislive.script.Env)}.
     * <p>
     * If the state is still incomplete after a response is posted,
     * {@link #process(org.praxislive.script.Env)} will be called again.
     *
     * @param state the completion state of the child stack frame
     * @param args the result of the child stack frame
     * @throws IllegalStateException if the state is not incomplete or a child
     * stack frame result is not expected
     */
    public void postResponse(State state, List<Value> args);

    /**
     * Access the result of this StackFrame.
     *
     * @return result
     * @throws IllegalStateException if the state is incomplete
     */
    public List<Value> result();

}
