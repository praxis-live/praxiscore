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
import org.praxislive.core.Value;

/**
 * A script command. The script executor will look up commands by name in the
 * current {@link Namespace}. Each execution of the command will cause a call
 * {@link #createStackFrame(org.praxislive.script.Namespace, java.util.List)}.
 */
public interface Command {

    /**
     * Create a StackFrame to execute the command with the provided Namespace
     * and arguments.
     *
     * @param namespace current namespace
     * @param args arguments
     * @return stack frame to execute command with provided arguments
     * @throws ExecutionException if stack frame cannot be created
     */
    public StackFrame createStackFrame(Namespace namespace, List<Value> args)
            throws ExecutionException;

}
