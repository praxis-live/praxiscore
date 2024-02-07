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

import java.util.Map;

/**
 * Service provider interface for other modules to provide commands.
 * <p>
 * Implementations should be registered for {@link ServiceLoader} to load.
 */
public interface CommandInstaller {

    /**
     * Called on all registered command installers during initialization of a
     * script executor. The implementation should add commands to the provided
     * map. The String key is the name used to look up the command in the
     * {@link Namespace}. A command might be registered under multiple names.
     *
     * @param commands map to install commands to
     */
    public void install(Map<String, Command> commands);

}
