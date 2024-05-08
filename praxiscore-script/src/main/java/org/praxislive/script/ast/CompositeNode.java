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
package org.praxislive.script.ast;

import java.util.List;
import org.praxislive.core.Value;
import org.praxislive.script.Namespace;

/**
 *
 *
 */
abstract class CompositeNode extends Node {

    private final List<Node> children;

    private int active;
    private Namespace namespace;

    public CompositeNode(List<? extends Node> children) {
        this.children = List.copyOf(children);
    }

    @Override
    public void init(Namespace namespace) {
        if (namespace == null) {
            throw new NullPointerException();
        }
        if (this.namespace != null) {
            throw new IllegalStateException();
        }
        this.namespace = namespace;
        active = 0;
        children.forEach(child -> child.init(namespace));
    }

    @Override
    public boolean isDone() {
        if (namespace == null) {
            throw new IllegalStateException();
        }
        if (active < 0) {
            return isThisDone();
        } else {
            while (active < children.size()) {
                if (!children.get(active).isDone()) {
                    return false;
                }
                active++;
            }
            active = -1;
            return isThisDone();
        }
    }

    protected abstract boolean isThisDone();

    @Override
    public void writeNextCommand(List<Value> args)
            throws Exception {
        if (namespace == null) {
            throw new IllegalStateException();
        }
        if (active >= 0) {
            children.get(active).writeNextCommand(args);
        } else {
            writeThisNextCommand(args);
        }
    }

    protected abstract void writeThisNextCommand(List<Value> args)
            throws Exception;

    @Override
    public void postResponse(List<Value> args)
            throws Exception {
        if (namespace == null) {
            throw new IllegalStateException();
        }
        if (active >= 0) {
            children.get(active).postResponse(args);
        } else {
            postThisResponse(args);
        }
    }

    protected abstract void postThisResponse(List<Value> args)
            throws Exception;

    @Override
    public void reset() {
        for (Node child : children) {
            child.reset();
        }
        namespace = null;
    }

    protected List<Node> getChildren() {
        return children;
    }

    protected void skipActive() {
        if (active < 0) {
            return;
        }
        active++;
        if (active >= children.size()) {
            active = -1;
        }
    }

}
