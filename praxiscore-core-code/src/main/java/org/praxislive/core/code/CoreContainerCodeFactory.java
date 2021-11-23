/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith.
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
import org.praxislive.core.ComponentType;

public class CoreContainerCodeFactory extends CodeContainer.Factory<CoreContainerDelegate> {

    private final static CoreContainerBodyContext CBC = new CoreContainerBodyContext();

    public CoreContainerCodeFactory(ComponentType type,
            Class<? extends CoreContainerDelegate> baseClass,
            String sourceTemplate) {
        super(CBC, type, baseClass, sourceTemplate);
    }

    @Override
    public CodeContainer.FactoryTask<CoreContainerDelegate> task() {
        return new CoreContextCreator();
    }

    private class CoreContextCreator extends CodeContainer.FactoryTask<CoreContainerDelegate> {

        private CoreContextCreator() {
            super(CoreContainerCodeFactory.this);
        }

        @Override
        protected CodeContext<CoreContainerDelegate> createCodeContext(CoreContainerDelegate delegate) {
            return new CoreContainerCodeContext(new CoreContainerCodeConnector(this, delegate));
        }

    }

}
