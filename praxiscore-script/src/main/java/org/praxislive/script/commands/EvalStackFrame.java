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

import java.util.LinkedList;
import java.util.List;
import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.types.PError;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;
import org.praxislive.script.ast.RootNode;

import static java.lang.System.Logger.Level;

/**
 *
 */
public class EvalStackFrame implements StackFrame {

    private static final System.Logger log = System.getLogger(EvalStackFrame.class.getName());

    private Namespace namespace;
    private RootNode rootNode;
    private State state;
    private Call pending;
    private List<Value> result;
    private List<Value> argList;
    private boolean doProcess;

    public EvalStackFrame(Namespace namespace, RootNode rootNode) {
        this.namespace = namespace;
        this.rootNode = rootNode;
        this.state = State.Incomplete;
        this.argList = new LinkedList<>();
        rootNode.reset();
        rootNode.init(namespace);
        doProcess = true;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public StackFrame process(Env context) {
        if (state != State.Incomplete) {
            throw new IllegalStateException();
        }
        if (!doProcess) {
            return null;
        }
        try {
            if (rootNode.isDone()) {
                processResultFromNode();
                return null;
            } else {
                return processNextCommand(context);
            }
        } catch (Exception ex) {
            result = List.of(PError.of(ex));
            state = State.Error;
            return null;
        } finally {
            doProcess = false;
        }

    }

    @Override
    public void postResponse(Call call) {
        if (pending != null && pending.matchID() == call.matchID()) {
            pending = null;
            if (call.isReply()) {
                log.log(Level.TRACE, () -> "EvalStackFrame - Received valid Return call : \n" + call);
                postResponse(call.args());
            } else {
                log.log(Level.TRACE, () -> "EvalStackFrame - Received valid Error call : \n" + call);
                this.state = State.Error;
                this.result = call.args();
            }
            doProcess = true;
        } else {
            log.log(Level.TRACE, () -> "EvalStackFrame - Received invalid call : \n" + call);
        }

    }

    @Override
    public void postResponse(State state, List<Value> args) {
        if (this.state != State.Incomplete) {
            throw new IllegalStateException();
        }
        switch (state) {
            case Incomplete:
                throw new IllegalArgumentException();
            case OK:
                postResponse(args);
                break;
            default:
                this.state = state;
                this.result = args;
        }
        doProcess = true;
    }

    @Override
    public List<Value> result() {
        if (state == State.Incomplete) {
            throw new IllegalStateException();
        }
        if (result == null) {
            return List.of();
        } else {
            return result;
        }
    }

    private void postResponse(List<Value> args) {
        try {
            argList.clear();
            argList.addAll(args);
            rootNode.postResponse(argList);
        } catch (Exception ex) {
            state = State.Error;//@TODO proper error reporting
        }
    }

    private void processResultFromNode() throws Exception {
        argList.clear();
        rootNode.writeResult(argList);
        result = List.copyOf(argList);
        state = State.OK;

    }

    private StackFrame processNextCommand(Env context)
            throws Exception {

        argList.clear();
        rootNode.writeNextCommand(argList);
        if (argList.size() < 1) {
            throw new Exception();
        }
        Value cmdArg = argList.get(0);
        if (cmdArg instanceof ControlAddress) {
            routeCall(context, argList);
            return null;
        }
        String cmdStr = cmdArg.toString();
        if (cmdStr.isEmpty()) {
            throw new Exception();
        }
        Command cmd = namespace.getCommand(cmdStr);
        if (cmd != null) {
            argList.remove(0);
            return cmd.createStackFrame(namespace, List.copyOf(argList));
        }
        if (cmdStr.charAt(0) == '/' && cmdStr.lastIndexOf('.') > -1) {
            routeCall(context, argList);
            return null;
        }

        throw new Exception();

    }

    private void routeCall(Env context, List<Value> argList)
            throws Exception {
        ControlAddress ad = ControlAddress.from(argList.get(0))
                .orElseThrow(Exception::new);
        argList.remove(0);
        Call call = Call.create(ad, context.getAddress(), context.getTime(), List.copyOf(argList));
        log.log(Level.TRACE, () -> "Sending Call" + call);
        pending = call;
        context.getPacketRouter().route(call);
    }

}
