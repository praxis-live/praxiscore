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

import org.praxislive.code.CodeRootContainerDelegate;
import org.praxislive.code.DefaultDelegateAPI;

/**
 *
 *
 */
public class CoreRootContainerDelegate extends CodeRootContainerDelegate
        implements DefaultDelegateAPI {

    /**
     * Hook called whenever the delegate needs to be initialized. Will be called
     * when the root is started, and any time the code is updated. Because this
     * code is called in a running root, the code should be suitable for
     * real-time usage.
     */
    @Override
    public void init() {
    }

    /**
     * Hook called whenever the root is started. This method will be called
     * after {@link #init()}. It is not called on code updates.
     */
    public void starting() {
    }

    /**
     * Hook called on every clock update. This will vary depending on the root
     * the component is installed into - it may correspond to every buffer or
     * frame. If a component reacts solely to input and doesn't need to be
     * called every cycle, do not override this method so that the delegate does
     * not have to be connected to the clock (for efficiency).
     */
    public void update() {
    }

    /**
     * Hook called when the root is stopping.
     */
    public void stopping() {
    }

}
