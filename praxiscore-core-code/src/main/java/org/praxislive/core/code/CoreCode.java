/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2023 Neil C Smith.
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
 * Code code utility functions.
 */
public class CoreCode {

    private final static List<String> DEFAULT_IMPORTS
            = List.of(CodeUtils.defaultImports());

    private final static CodeFactory.Base<CoreCodeDelegate> BASE
            = CodeFactory.base(CoreCodeDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new CoreCodeContext(new CoreCodeConnector(task, delegate)));

    private final static CodeFactory.Base<CoreContainerDelegate> CONTAINER_BASE
            = CodeFactory.containerBase(CoreContainerDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new CoreContainerCodeContext(new CoreContainerCodeConnector(task, delegate)));
    
    private final static CodeFactory.Base<CoreRootContainerDelegate> ROOT_CONTAINER_BASE
            = CodeFactory.rootContainerBase(CoreRootContainerDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new CoreRootContainerCodeContext(new CoreRootContainerCodeConnector(task, delegate)));

    private CoreCode() {
    }

    /**
     * Access {@link CodeFactory.Base} for {@link CoreContainerDelegate}.
     *
     * @return code factory base for CoreContainerDelegate.
     */
    public static CodeFactory.Base<CoreCodeDelegate> base() {
        return BASE;
    }
    
    /**
     * Access {@link CodeFactory.Base} for {@link CoreContainerDelegate}.
     *
     * @return code factory base for CoreContainerDelegate.
     */
    public static CodeFactory.Base<CoreContainerDelegate> containerBase() {
        return CONTAINER_BASE;
    }
    
    /**
     * Access {@link CodeFactory.Base} for {@link CoreRootContainerDelegate}.
     *
     * @return code factory base for CoreRootContainerDelegate.
     */
    public static CodeFactory.Base<CoreRootContainerDelegate> rootContainerBase() {
        return ROOT_CONTAINER_BASE;
    }

}
