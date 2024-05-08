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

import org.praxislive.core.Value;

/**
 * Storage for a value, to be used with {@link Namespace}. Variable
 * implementations might be read-only, only settable at certain times, or
 * validate their values.
 *
 */
public interface Variable {

    /**
     * Set the value of this variable. A variable may not be settable
     * (read-only) or may me only settable at certain times. A variable might
     * validate its value, eg. a particular type, range, etc.
     *
     * @param value new value, not null
     * @throws UnsupportedOperationException if the value cannot be set
     * @throws IllegalArgumentException if the value is not valid
     */
    public void setValue(Value value);

    /**
     * Get the current value of the variable.
     *
     * @return current value
     */
    public Value getValue();

}
