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

import java.util.List;
import org.praxislive.script.AbstractSingleCallFrame;
import java.util.Map;
import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Env;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;

/**
 *
 */
public class AtCmds implements CommandInstaller {

    private final static AtCmds INSTANCE = new AtCmds();
    private final static At AT = new At();
    private final static NotAt NOT_AT = new NotAt();

    private AtCmds() {
    }

    @Override
    public void install(Map<String, Command> commands) {
        commands.put("@", AT);
        commands.put("!@", NOT_AT);
    }

    public final static AtCmds getInstance() {
        return INSTANCE;
    }

    private static class At implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {

            if (args.size() < 2) {
                throw new Exception();
            }

            try {
                ComponentAddress ctxt = ComponentAddress.from(args.get(0))
                        .orElseThrow(IllegalArgumentException::new);
                if (args.size() == 3) {
                    ComponentType type = ComponentType.from(args.get(1))
                            .orElseThrow(IllegalArgumentException::new);
                    return new AtStackFrame(namespace, ctxt, type, args.get(2));
                } else {
                    Value arg = args.get(1);
                    if (! arg.toString().contains(" ")) {
                        try {
                            ComponentType type = ComponentType.from(arg).get();
                            return new AtStackFrame(namespace, ctxt, type, PString.EMPTY);
                        } catch (Exception ex) {
                            // fall through
                        }
                    }
                    return new AtStackFrame(namespace, ctxt, null, arg);
                }
            } catch (Exception ex) {
                throw new Exception(ex);
            }

        }
    }

    private static class NotAt implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            return new NotAtStackFrame(namespace, args);
        }

    }

    private static class AtStackFrame implements StackFrame {

        private State state;
        private final Namespace namespace;
        private final ComponentAddress ctxt;
        private final ComponentType type;
        private final Value script;
        private int stage;
        private List<Value> result;
        private Call active;

        private AtStackFrame(Namespace namespace, ComponentAddress ctxt,
                ComponentType type, Value script) {
            this.namespace = namespace;
            this.ctxt = ctxt;
            this.type = type;
            this.script = script;
            state = State.Incomplete;
            if (type == null) {
                stage = 2;
            } else {
                stage = 0;
            }
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public StackFrame process(Env env) {
            if (stage == 0) {
                stage++;
                try {

                    ControlAddress to;
                    List<Value> args;
                    int depth = ctxt.depth();
                    if (depth == 1) {
                        to = ControlAddress.of(
                                env.getLookup().find(Services.class)
                                .flatMap(sm -> sm.locate(RootManagerService.class))
                                .orElseThrow(ServiceUnavailableException::new),
                                RootManagerService.ADD_ROOT);
                        args = List.of(PString.of(ctxt.rootID()), type);
                    } else {
                        to = ControlAddress.of(ctxt.parent(),
                                ContainerProtocol.ADD_CHILD);
                        args = List.of(PString.of(ctxt.componentID(depth - 1)), type);
                    }
                    active = Call.create(to, env.getAddress(), env.getTime(), args);
                    env.getPacketRouter().route(active);

                } catch (Exception ex) {
                    state = State.Error;
                    result = List.of(PError.of(ex));
                }
            }
            if (stage == 2) {
                stage++;
                try {
                    Namespace child = namespace.createChild();
                    child.createConstant(Env.CONTEXT, ctxt);
                    return ScriptCmds.INLINE_EVAL.createStackFrame(child, List.of(script));
                } catch (Exception ex) {
                    state = State.Error;
                    result = List.of(PError.of(ex));
                }
            }

            return null;
        }

        @Override
        public void postResponse(Call call) {
            if (active != null && call.matchID() == active.matchID()) {
                active = null;
                if (call.isReply() && stage == 1) {
                    stage++;
                } else {
                    result = call.args();
                    this.state = State.Error;
                }
            }
        }

        @Override
        public void postResponse(State state, List<Value> args) {
            if (state == State.OK) {
//                if (stage == 1) {
//                    stage++;
//                } else
                if (stage == 3) {
                    this.state = State.OK;
                    result = args;
                }
            } else {
                this.state = state;
                result = args;
            }
        }

        @Override
        public List<Value> result() {
            if (result == null) {
                throw new IllegalStateException();
            }
            return result;
        }
    }

    private static class NotAtStackFrame extends AbstractSingleCallFrame {

        private NotAtStackFrame(Namespace ns, List<Value> args) {
            super(ns, args);
        }

        @Override
        protected Call createCall(Env env, List<Value> args) throws Exception {
            ComponentAddress comp = ComponentAddress.from(args.get(0))
                    .orElseThrow(IllegalArgumentException::new);
            if (comp.depth() == 1) {
                return createRootRemovalCall(env, comp.rootID());
            } else {
                return createChildRemovalCall(env, comp);
            }
        }

        private Call createRootRemovalCall(Env env, String id) throws Exception {
            ControlAddress to = ControlAddress.of(
                    env.getLookup().find(Services.class)
                            .flatMap(sm -> sm.locate(RootManagerService.class))
                            .orElseThrow(ServiceUnavailableException::new),
                    RootManagerService.REMOVE_ROOT);
            return Call.create(to, env.getAddress(), env.getTime(), PString.of(id));
        }

        private Call createChildRemovalCall(Env env, ComponentAddress comp) throws Exception {
            ControlAddress to = ControlAddress.of(comp.parent(),
                    ContainerProtocol.REMOVE_CHILD);
            return Call.create(to, env.getAddress(), env.getTime(),
                    PString.of(comp.componentID(comp.depth() - 1)));
        }
    }
}
