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
package org.praxislive.project;

import java.io.IOException;
import java.net.URI;
import org.praxislive.core.Value;

/**
 *
 */
class GraphWriter {

    private static final String INDENT = "  ";
    private static final String AT = "@";
    private static final String CONNECT = "~";

    private final GraphModel model;
    private final URI context;

    private GraphWriter(GraphModel model) {
        this.model = model;
        this.context = model.context().orElse(null);
    }

    private void doWrite(Appendable target) throws IOException {
        var root = model.root();
        if (root.isSynthetic()) {
            // sub graph
            writeChildren(target, root, 0);
            writeConnections(target, root, 0);
        } else {
            // full graph
            writeComponent(target, root.id(), root, 0);
        }
    }

    private void writeComponent(Appendable sb,
            String id,
            GraphElement.Component cmp,
            int level) throws IOException {
        writeIndent(sb, level);
        sb.append(AT).append(' ');
        if (cmp instanceof GraphElement.Root) {
            sb.append('/').append(id);
        } else {
            sb.append("./").append(id);
        }
        sb.append(' ').append(cmp.type().toString()).append(" {\n");
        writeComments(sb, cmp, level + 1);
        writeProperties(sb, cmp, level + 1);
        writeChildren(sb, cmp, level + 1);
        writeConnections(sb, cmp, level + 1);
        writeIndent(sb, level);
        sb.append("}\n");
    }

    private void writeComments(Appendable sb, GraphElement.Component cmp, int level)
            throws IOException {
        for (GraphElement.Comment comment : cmp.comments()) {
            writeIndent(sb, level);
            sb.append("# ");
            sb.append(SyntaxUtils.escapeCommentText(comment.text()));
            sb.append('\n');
        }
    }

    private void writeProperties(Appendable sb, GraphElement.Component cmp, int level)
            throws IOException {
        for (var entry : cmp.properties().entrySet()) {
            String id = entry.getKey();
            Value value = entry.getValue().value();
            writeIndent(sb, level);
            sb.append('.').append(id).append(' ');
            if (context != null) {
                SyntaxUtils.writeValue(context, value, sb);
            } else {
                SyntaxUtils.writeValue(value, sb);
            }
            sb.append('\n');
        }
    }

    private void writeChildren(Appendable sb, GraphElement.Component cmp, int level)
            throws IOException {
        for (var entry : cmp.children().entrySet()) {
            String id = entry.getKey();
            GraphElement.Component child = entry.getValue();
            writeComponent(sb, id, child, level);
        }
    }

    private void writeConnections(Appendable sb, GraphElement.Component cmp, int level)
            throws IOException {
        for (GraphElement.Connection c : cmp.connections()) {
            writeIndent(sb, level);
            sb.append(CONNECT).append(' ');
            sb.append("./").append(c.sourceComponent()).append('!').append(c.sourcePort()).append(' ');
            sb.append("./").append(c.targetComponent()).append('!').append(c.targetPort()).append('\n');
        }
    }

    private void writeIndent(Appendable sb, int level) throws IOException {
        for (int i = 0; i < level; i++) {
            sb.append(INDENT);
        }
    }

    static void write(GraphModel model, Appendable target) throws IOException {
        GraphWriter writer = new GraphWriter(model);
        writer.doWrite(target);
    }

}
