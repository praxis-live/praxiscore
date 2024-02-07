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
     * @throws ExecutionException on error
     */
    public List<Value> process(Env context, Namespace namespace,
            List<Value> args)
            throws ExecutionException;

}
