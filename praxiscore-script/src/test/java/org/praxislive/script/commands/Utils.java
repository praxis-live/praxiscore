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

import java.util.HashMap;
import java.util.Map;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.script.Command;
import org.praxislive.script.Env;
import org.praxislive.script.Namespace;
import org.praxislive.script.Variable;

/**
 *
 */
class Utils {

    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");

    private Utils() {
    }

    static Env env() {
        return new EmptyEnv();
    }

    static Namespace namespace() {
        return new NS();
    }

    static void logTest(String testName) {
        if (VERBOSE) {
            System.out.println();
            System.out.println(testName);
            System.out.println("==================");
        }
    }

    static void logResult(String description, Object value) {
        if (VERBOSE) {
            System.out.println(description);
            System.out.println(value);
        }
    }

    private static class EmptyEnv implements Env {

        @Override
        public ControlAddress getAddress() {
            return ControlAddress.of("/dev.null");
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

        @Override
        public PacketRouter getPacketRouter() {
            return p -> {
            };
        }

        @Override
        public long getTime() {
            return System.nanoTime();
        }

    }

    private static class NS implements Namespace {

        private final NS parent;
        private final Map<String, Variable> variables;
        private final Map<String, Command> commands;

        private NS() {
            this(null);
        }

        private NS(NS parent) {
            this.parent = parent;
            variables = new HashMap<>();
            commands = Map.of();
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
            return commands.get(id);
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
