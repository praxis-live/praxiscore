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
package org.praxislive.code;

import org.praxislive.core.Port;
import org.praxislive.core.PortInfo;

/**
 *
 */
public abstract class PortDescriptor {

    public static enum Category {
        In, Action, Out, Property, AuxIn, AuxOut
    }

    private final String id;
    private final Category category;
    private final int index;

    protected PortDescriptor(String id, Category category, int index) {
        this.id = id;
        this.category = category;
        this.index = index;
    }

    public final String getID() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public int getIndex() {
        return index;
    }

    public abstract void attach(CodeContext<?> context, Port previous);

    public abstract Port getPort();

    public abstract PortInfo getInfo();

    public void reset(boolean full) {
    }

    public void stopping() {
    }

//    public void dispose() {
//    }

}
