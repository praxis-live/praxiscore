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
 * A Namespace offers storage of {@link Variable} and {@link Command} by name.
 * Namespaces exist in a hierarchy. Variables and Commands added to this
 * namespace usually shadow those from parent namespaces, and are usually
 * visible to child namespaces.
 * <p>
 * A Namespace is passed in from the script executor to
 * {@link Command#createStackFrame(org.praxislive.script.Namespace, java.util.List)}.
 */
public interface Namespace {

    /**
     * Get the Variable with the given ID, or {@code null} if it doesn't exist
     * in this or a parent namespace.
     *
     * @param id variable ID
     * @return named variable, or null if none exists
     */
    public Variable getVariable(String id);

    /**
     * Add a Variable with the given ID to this Namespace.
     *
     * @param id variable ID
     * @param var variable to add
     * @throws IllegalArgumentException if a variable with that ID already
     * exists in this namespace
     */
    public void addVariable(String id, Variable var);

    /**
     * Get the Command with the given ID, or {@code null} if it doesn't exist in
     * this or a parent namespace.
     *
     * @param id command ID
     * @return named command, or null if none exists
     */
    public Command getCommand(String id);

    /**
     * Add a Command with the given ID to this Namespace.
     *
     * @param id command ID
     * @param cmd command to add
     * @throws IllegalArgumentException if a command with that ID already exists
     * in this namespace
     */
    public void addCommand(String id, Command cmd);

    /**
     * Create a child Namespace of this Namespace.
     *
     * @return child namespace
     */
    public Namespace createChild();

    /**
     * Create a variable in this namespace with the initial value given.
     * <p>
     * The default implementation of this method creates a new instance of a
     * variable implementation, and calls
     * {@link #addVariable(java.lang.String, org.praxislive.script.Variable)} to
     * register it.
     *
     * @param id variable ID
     * @param value initial value
     * @return created variable
     * @throws IllegalArgumentException if a variable with that name already
     * exists in this namespace
     */
    public default Variable createVariable(String id, Value value) {
        Variable v = new VariableImpl(value);
        addVariable(id, v);
        return v;
    }

    /**
     * Get the variable with the given ID from this namespace or a parent
     * namespace, creating and initializing a variable with the provided default
     * value if none exists.
     * <p>
     * The default implementation of this method calls
     * {@link #getVariable(java.lang.String)} to find a registered variable, and
     * if that method returns {@link null} delegates to
     * {@link #createVariable(java.lang.String, org.praxislive.core.Value)}.
     *
     * @param id variable ID
     * @param defaultValue default initial value
     * @return created variable
     */
    public default Variable getOrCreateVariable(String id, Value defaultValue) {
        Variable v = getVariable(id);
        if (v == null) {
            return createVariable(id, defaultValue);
        } else {
            return v;
        }
    }

    /**
     * Create a constant in this namespace with the initial value given. The
     * constant is guaranteed to always return {@code value} from
     * {@link Variable#getValue()}, and to always throw
     * {@link UnsupportedOperationException} on any call to
     * {@link Variable#setValue(org.praxislive.core.Value)}.
     * <p>
     * The default implementation of this method creates a new instance of a
     * constant variable implementation, and calls
     * {@link #addVariable(java.lang.String, org.praxislive.script.Variable)} to
     * register it.
     *
     * @param id constant name
     * @param value constant value
     * @return created constant
     * @throws IllegalArgumentException if a variable with that name already
     * exists in this namespace
     */
    public default Variable createConstant(String id, Value value) {
        Variable c = new ConstantImpl(value);
        addVariable(id, c);
        return c;
    }

}
