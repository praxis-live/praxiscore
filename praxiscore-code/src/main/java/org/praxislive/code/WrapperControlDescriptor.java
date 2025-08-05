/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.praxislive.core.Control;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.TreeWriter;

/**
 *
 */
final class WrapperControlDescriptor extends ControlDescriptor<WrapperControlDescriptor> {

    private final Function<CodeContext<?>, Control> attacher;
    private final BiConsumer<CodeContext<?>, TreeWriter> writer;
    private final ControlInfo info;

    private CodeContext<?> context;
    private Control control;

    WrapperControlDescriptor(String id,
            ControlInfo info,
            int index,
            Function<CodeContext<?>, Control> attacher) {
        this(id, info, index, attacher, null);
    }
    
    WrapperControlDescriptor(String id,
            ControlInfo info,
            int index,
            Function<CodeContext<?>, Control> attacher,
            BiConsumer<CodeContext<?>, TreeWriter> writer) {
        super(WrapperControlDescriptor.class, id, Category.Internal, index);
        this.info = info;
        this.attacher = Objects.requireNonNull(attacher);
        this.writer = writer;
    }

    @Override
    public void attach(CodeContext<?> context, WrapperControlDescriptor previous) {
        this.context = context;
        this.control = attacher.apply(context);
    }

    @Override
    public Control control() {
        return control;
    }

    @Override
    public ControlInfo controlInfo() {
        return info;
    }

    @Override
    public void write(TreeWriter treewriter) {
        if (writer != null) {
            writer.accept(context, treewriter);
        }
    }

}
