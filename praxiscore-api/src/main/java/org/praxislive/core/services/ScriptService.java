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
package org.praxislive.core.services;

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.types.PString;

/**
 *
 */
public class ScriptService implements Service {

    public final static String EVAL = "eval";
    public final static String CLEAR = "clear";

    public final static ControlInfo EVAL_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(PString.info()),
                    List.of(),
                    null);
    public final static ControlInfo CLEAR_INFO
            = ControlInfo.createFunctionInfo(
                    List.of(),
                    List.of(),
                    null);

    @Override
    public Stream<String> controls() {
        return Stream.of(EVAL, CLEAR);
    }

    @Override
    public ControlInfo getControlInfo(String control) {
        if (EVAL.equals(control)) {
            return EVAL_INFO;
        }
        if (CLEAR.equals(control)) {
            return CLEAR_INFO;
        }
        throw new IllegalArgumentException();
    }
}
