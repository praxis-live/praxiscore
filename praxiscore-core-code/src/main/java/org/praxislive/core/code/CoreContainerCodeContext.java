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
 *
 */
package org.praxislive.core.code;

import org.praxislive.code.CodeContainer;
import org.praxislive.code.CodeContext;
import org.praxislive.core.ExecutionContext;
import org.praxislive.core.services.LogLevel;

/**
 * A {@link CodeContext} for core containers.
 */
class CoreContainerCodeContext extends CodeContainer.Context<CoreContainerDelegate> {

    CoreContainerCodeContext(CoreContainerCodeConnector connector) {
        super(connector);
    }

    @Override
    protected void onInit() {
        try {
            getDelegate().init();
        } catch (Exception e) {
            getLog().log(LogLevel.ERROR, e, "Exception thrown during init()");
        }
    }

    @Override
    protected void onStart() {
        try {
            getDelegate().starting();
        } catch (Exception e) {
            getLog().log(LogLevel.ERROR, e, "Exception thrown during starting()");
        }
    }

    @Override
    protected void onStop() {
        try {
            getDelegate().stopping();
        } catch (Exception e) {
            getLog().log(LogLevel.ERROR, e, "Exception thrown during stopping()");
        }
    }

    @Override
    protected void tick(ExecutionContext source) {
        try {
            getDelegate().update();
        } catch (Exception e) {
            getLog().log(LogLevel.ERROR, e, "Exception thrown during update()");
        }
    }

}
