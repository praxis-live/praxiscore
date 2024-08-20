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
package org.praxislive.core.code;

import java.util.List;
import org.praxislive.code.CodeFactory;
import org.praxislive.code.CodeUtils;

/**
 * Data code component utility functions. Data components may not be designed to
 * be real-time safe, and may support IO, etc.
 */
public class DataCode {

    private final static List<String> DEFAULT_IMPORTS = CodeUtils.defaultImports();

    private final static CodeFactory.Base<DataCodeDelegate> BASE
            = CodeFactory.base(DataCodeDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new DataCodeDelegate.Context(new DataCodeDelegate.Connector(task, delegate)));

    private final static CodeFactory.Base<DataRootContainerDelegate> ROOT_CONTAINER_BASE
            = CodeFactory.rootContainerBase(DataRootContainerDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new DataRootContainerDelegate.Context(
                            new DataRootContainerDelegate.Connector(task, delegate)));

    private DataCode() {
    }

    /**
     * Access {@link CodeFactory.Base} for {@link DataCodeDelegate}.
     *
     * @return code factory base for CoreCodeDelegate.
     */
    public static CodeFactory.Base<DataCodeDelegate> base() {
        return BASE;
    }

    /**
     * Access {@link CodeFactory.Base} for {@link DataRootContainerDelegate}.
     *
     * @return code factory base for CoreRootContainerDelegate.
     */
    public static CodeFactory.Base<DataRootContainerDelegate> rootContainerBase() {
        return ROOT_CONTAINER_BASE;
    }

}
