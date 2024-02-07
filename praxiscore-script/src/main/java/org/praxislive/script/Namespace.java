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

/**
 * A Namespace offers storage of {@link Variable} and {@link Command} by name.
 * Namesoaces exist in a hierarchy. Variables and Commands added to this
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
     */
    public void addCommand(String id, Command cmd);

    /**
     * Create a child Namespace of this Namespace.
     *
     * @return child namespace
     */
    public Namespace createChild();

}
