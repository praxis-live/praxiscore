/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
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
import java.util.stream.Collectors;
import org.praxislive.core.Call;
import org.praxislive.core.Value;

/**
 *
 */
public interface StackFrame {

    public static enum State {Incomplete, OK, Error, Break, Continue};

    public State getState();

    public StackFrame process(Env env);

    public void postResponse(Call call);

    public void postResponse(State state, List<Value> args);
    
    public List<Value> result();

}
