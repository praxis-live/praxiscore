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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.syntax.InvalidSyntaxException;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PString;
import org.praxislive.script.ast.RootNode;
import org.praxislive.script.ast.ScriptParser;

import static java.lang.System.Logger.Level;

/**
 * A stackframe implementation that supports parsing and running of Pcl scripts.
 */
public final class ScriptStackFrame implements StackFrame {

    private static final String TRAP = "_TRAP";

    private static final System.Logger log = System.getLogger(ScriptStackFrame.class.getName());

    private final Namespace namespace;
    private final RootNode rootNode;
    private final boolean trapErrors;
    private final List<Value> scratchList;

    private State state;
    private String activeCommand;
    private Call pending;
    private List<Value> result;
    private boolean doProcess;

    private ScriptStackFrame(Namespace namespace,
            RootNode rootNode,
            boolean trapErrors) {
        this.namespace = namespace;
        this.rootNode = rootNode;
        this.state = State.Incomplete;
        this.trapErrors = trapErrors;
        this.scratchList = new ArrayList<>();
        rootNode.reset();
        if (trapErrors) {
            namespace.createVariable(TRAP, PArray.EMPTY);
        }
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
        while (!rootNode.isDone() && state == State.Incomplete) {
            try {
                return processNextCommand(context);
            } catch (Exception ex) {
                postError(List.of(PError.of(ex)));
            }
        }
        if (rootNode.isDone() && state == State.Incomplete) {

            try {
                processResultFromNode();
            } catch (Exception ex) {
                result = List.of(PError.of(ex));
                state = State.Error;
            }
        }
        return null;
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
                postError(call.args());
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
            case Incomplete ->
                throw new IllegalArgumentException();
            case OK ->
                postResponse(args);
            case Error ->
                postError(args);
            default -> {
                this.state = state;
                this.result = List.copyOf(args);
            }
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
            scratchList.clear();
            scratchList.addAll(args);
            rootNode.postResponse(scratchList);
        } catch (Exception ex) {
            state = State.Error;//@TODO proper error reporting
        }
    }

    private void postError(List<Value> args) {
        Variable trap = namespace.getVariable(TRAP);
        if (trap != null) {
            rootNode.skipCurrentLine();
            PArray existing = PArray.from(trap.getValue()).orElse(PArray.EMPTY);
            Value response = args.isEmpty() ? null : args.getFirst();
            trap.setValue(addErrorToTrap(existing, response));
        } else {
            result = List.copyOf(args);
            state = State.Error;
        }
    }

    private PArray addErrorToTrap(PArray trap, Value response) {
        String msg;
        if (response == null) {
            msg = activeCommand + " : Error";
        } else {
            msg = PError.from(response)
                    .map(err -> activeCommand + " : " + err.exceptionType().getSimpleName()
                    + " : " + err.message())
                    .orElse(activeCommand + " : Error : " + response);
        }
        return Stream.concat(trap.stream(), Stream.of(PString.of(msg)))
                .collect(PArray.collector());
    }

    private void processResultFromNode() throws Exception {
        scratchList.clear();
        Variable trap = namespace.getVariable(TRAP);
        if (trapErrors && trap != null && !trap.getValue().isEmpty()) {
            String errors = PArray.from(trap.getValue())
                    .orElse(PArray.EMPTY)
                    .asListOf(String.class)
                    .stream()
                    .collect(Collectors.joining("\n"));
            result = List.of(PString.of(errors));
            state = State.Error;
        } else {
            rootNode.writeResult(scratchList);
            result = List.copyOf(scratchList);
            state = State.OK;
        }
    }

    private StackFrame processNextCommand(Env context)
            throws Exception {

        scratchList.clear();
        rootNode.writeNextCommand(scratchList);
        if (scratchList.size() < 1) {
            throw new Exception();
        }
        Value cmdArg = scratchList.get(0);
        activeCommand = cmdArg.toString();
        if (cmdArg instanceof ControlAddress) {
            routeCall(context, scratchList);
            return null;
        }
        String cmdStr = cmdArg.toString();
        if (cmdStr.isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }
        Command cmd = namespace.getCommand(cmdStr);
        if (cmd != null) {
            scratchList.remove(0);
            return cmd.createStackFrame(namespace, List.copyOf(scratchList));
        }
        if (cmdStr.charAt(0) == '/' && cmdStr.lastIndexOf('.') > -1) {
            routeCall(context, scratchList);
            return null;
        }

        throw new IllegalArgumentException("Command not found");

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

    /**
     * Create a {@link ScriptStackFrame.Builder} for the provided namespace and
     * script. By default the script will be evaluated in a dedicated child
     * namespace. Neither the builder or the stack frame are reusable.
     *
     * @param namespace namespace to run script in
     * @param script script to parse and run
     * @return builder
     * @throws InvalidSyntaxException if the script cannot be parsed
     */
    public static Builder forScript(Namespace namespace, String script) {
        RootNode root = ScriptParser.getInstance().parse(script);
        return new Builder(namespace, root);
    }

    /**
     * A builder for {@link ScriptStackFrame}.
     *
     * @see #forScript(org.praxislive.script.Namespace, java.lang.String)
     */
    public static class Builder {

        private final Namespace namespace;
        private final RootNode root;

        private boolean inline;
        private List<String> allowedCommands;
        private boolean trapErrors;
        private List<Consumer<Namespace>> namespaceProcessors;

        private Builder(Namespace namespace, RootNode root) {
            this.namespace = namespace;
            this.root = root;
        }

        /**
         * Run the script directly in the provided namespace rather than a
         * child.
         *
         * @return this for chaining
         */
        public Builder inline() {
            if (trapErrors) {
                throw new IllegalStateException("Inline and trap errors cannot be used together");
            }
            this.inline = true;
            return this;
        }

        /**
         * Trap errors. Error messages will be aggregated and script execution
         * will attempt to continue. If no allowed commands have been specified,
         * an empty list of allowed commands will be set.
         *
         * @return this for chaining
         */
        public Builder trapErrors() {
            if (inline) {
                throw new IllegalStateException("Inline and trap errors cannot be used together");
            }
            this.trapErrors = true;
            if (this.allowedCommands == null) {
                this.allowedCommands = List.of();
            }
            return this;
        }

        /**
         * Specify a list of allowed commands to filter those available from the
         * provided namespace.
         *
         * @param commands list of allowed commands
         * @return this for chaining
         */
        public Builder allowedCommands(List<String> commands) {
            this.allowedCommands = List.copyOf(commands);
            return this;
        }

        /**
         * Create a constant with the given name and value in the script
         * namespace.
         *
         * @param id constant name
         * @param value constant value
         * @return this for chaining
         */
        public Builder createConstant(String id, Value value) {
            addNamespaceProcessor(ns -> ns.createConstant(id, value));
            return this;
        }

        /**
         * Create a variable with the given name and value in the script
         * namespace.
         *
         * @param id variable name
         * @param value variable value
         * @return this for chaining
         */
        public Builder createVariable(String id, Value value) {
            addNamespaceProcessor(ns -> ns.createVariable(id, value));
            return this;
        }

        private void addNamespaceProcessor(Consumer<Namespace> processor) {
            if (namespaceProcessors == null) {
                namespaceProcessors = new ArrayList<>();
            }
            namespaceProcessors.add(processor);
        }

        /**
         * Build the ScriptStackFrame.
         *
         * @return script stackframe
         */
        public ScriptStackFrame build() {
            Namespace ns;
            if (inline) {
                ns = namespace;
            } else {
                ns = namespace.createChild();
            }
            if (namespaceProcessors != null) {
                Namespace nsp = ns;
                namespaceProcessors.forEach(p -> p.accept(nsp));
            }
            if (allowedCommands != null) {
                ns = new FilteredNamespace(ns, allowedCommands);
            }
            return new ScriptStackFrame(ns, root, trapErrors);
        }

    }

    private static class FilteredNamespace implements Namespace {

        private final Namespace delegate;
        private final List<String> allowed;

        private FilteredNamespace(Namespace delegate, List<String> allowed) {
            this.delegate = Objects.requireNonNull(delegate);
            this.allowed = List.copyOf(allowed);
        }

        @Override
        public void addCommand(String id, Command cmd) {
            if (allowed.contains(id)) {
                delegate.addCommand(id, cmd);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void addVariable(String id, Variable var) {
            delegate.addVariable(id, var);
        }

        @Override
        public Namespace createChild() {
            return new FilteredNamespace(delegate.createChild(), allowed);
        }

        @Override
        public Command getCommand(String id) {
            if (allowed.contains(id)) {
                return delegate.getCommand(id);
            } else {
                return null;
            }
        }

        @Override
        public Variable getVariable(String id) {
            return delegate.getVariable(id);
        }

    }

}
