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
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Value;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.core.services.TaskService;
import org.praxislive.core.types.PReference;

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

    /**
     * Combine this StackFrame with another created from the result of this
     * StackFrame. The returned StackFrame will execute the frames in turn.
     * <p>
     * The default implementation returns a private implementation of a compound
     * stackframe. If this method is called on an existing compound stack frame,
     * then the stage function will be added to that and {@code this} will be
     * returned.
     *
     * @param stage function to create next stack frame from result
     * @return compound stackframe
     */
    public default StackFrame andThen(Function<List<Value>, StackFrame> stage) {
        Objects.requireNonNull(stage);
        if (this instanceof CompoundStackFrame csf) {
            csf.addStage(stage);
            return this;
        } else {
            return new CompoundStackFrame(this, stage);
        }
    }

    /**
     * Map the result of this StackFrame with the provided mapping function
     * before returning a result or using
     * {@link #andThen(java.util.function.Function)}.
     * <p>
     * The default implementation calls
     * {@link #andThen(java.util.function.Function)} with a function that
     * creates a private implementation of a mapping StackFrame.
     *
     * @param mapper map value list
     * @return mapping stackframe
     */
    public default StackFrame andThenMap(UnaryOperator<List<Value>> mapper) {
        Objects.requireNonNull(mapper);
        return andThen(args -> new CompoundStackFrame.SupplierStackFrame(
                () -> mapper.apply(args))
        );
    }

    /**
     * Create a StackFrame that catches any error produced by this StackFrame
     * and defers to the StackFrame created by the provided function. The input
     * to the function will be the error result of this StackFrame.
     *
     * @param errorStage function to create stack frame on error
     * @return created stackframe
     */
    public default StackFrame onError(Function<List<Value>, StackFrame> errorStage) {
        Objects.requireNonNull(errorStage);
        return new CompoundStackFrame.OnFailStackFrame(this, errorStage);
    }

    /**
     * Create a StackFrame that executes the provided task asynchronously in the
     * default {@link TaskService} and returns the result.
     *
     * @param task task to execute
     * @return stackframe
     */
    public static StackFrame async(TaskService.Task task) {
        return serviceCall(TaskService.class, TaskService.SUBMIT, PReference.of(task));
    }

    /**
     * Create a StackFrame that makes a call to the provided control and returns
     * the result.
     *
     * @param to control address
     * @param arg single argument
     * @return stackframe
     */
    public static StackFrame call(ControlAddress to, Value arg) {
        return call(to, List.of(arg));
    }

    /**
     * Create a StackFrame that makes a call to the provided control and returns
     * the result.
     *
     * @param to control address
     * @param args arguments
     * @return stackframe
     */
    public static StackFrame call(ControlAddress to, List<Value> args) {
        return new AbstractSingleCallFrame(args) {
            @Override
            protected Call createCall(Env env, List<Value> args) throws Exception {
                return Call.create(to, env.getAddress(), env.getTime(), args);
            }
        };
    }

    /**
     * Create a StackFrame that returns an empty result.
     *
     * @return stackframe
     */
    public static StackFrame empty() {
        return new CompoundStackFrame.SupplierStackFrame(() -> List.of());
    }

    /**
     * Create a StackFrame that makes a call to the provided {@link Service} and
     * returns the result. The first implementation of the service found in the
     * Env lookup will be used.
     *
     * @param service type of service
     * @param control id of control on service
     * @param arg single argument
     * @return stackframe
     * @throws ServiceUnavailableException if no implementation of the service
     * is found
     *
     */
    public static StackFrame serviceCall(Class<? extends Service> service,
            String control, Value arg) {
        return serviceCall(service, control, List.of(arg));
    }

    /**
     * Create a StackFrame that makes a call to the provided {@link Service} and
     * returns the result. The first implementation of the service found in the
     * Env lookup will be used.
     *
     * @param service type of service
     * @param control id of control on service
     * @param args arguments
     * @return stackframe
     * @throws ServiceUnavailableException if no implementation of the service
     * is found
     */
    public static StackFrame serviceCall(Class<? extends Service> service,
            String control, List<Value> args) {
        return new AbstractSingleCallFrame(args) {
            @Override
            protected Call createCall(Env env, List<Value> args) throws Exception {
                ControlAddress to = ControlAddress.of(
                        env.getLookup().find(Services.class)
                                .flatMap(sm -> sm.locate(service))
                                .orElseThrow(ServiceUnavailableException::new),
                        control
                );
                return Call.create(to, env.getAddress(), env.getTime(), args);
            }
        };
    }

}
