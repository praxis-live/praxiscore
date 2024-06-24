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
import org.praxislive.core.types.PResource;

import static org.praxislive.project.ProjectModel.BUILD_LEVEL_SWITCH;
import static org.praxislive.project.ProjectModel.INCLUDE_CMD;
import static org.praxislive.project.ProjectModel.RUN_LEVEL_SWITCH;

/**
 *
 */
class ProjectWriter {

    private final ProjectModel model;
    private final URI context;

    private ProjectWriter(ProjectModel model) {
        this.model = model;
        this.context = model.context().orElse(null);
    }

    private void doWrite(Appendable target) throws IOException {
        for (ProjectElement e : model.setupElements()) {
            writeElement(target, e);
        }
        target.append("\n# ").append(BUILD_LEVEL_SWITCH).append('\n');
        for (ProjectElement e : model.buildElements()) {
            writeElement(target, e);
        }
        target.append("\n# ").append(RUN_LEVEL_SWITCH).append('\n');
        for (ProjectElement e : model.runElements()) {
            writeElement(target, e);
        }
    }

    private void writeElement(Appendable target, ProjectElement element)
            throws IOException {
        if (element instanceof ProjectElement.File fileElement) {
            writeFileElement(target, fileElement);
        } else if (element instanceof ProjectElement.Line lineElement) {
            writeLineElement(target, lineElement);
        }
    }

    private void writeFileElement(Appendable target, ProjectElement.File fileElement)
            throws IOException {
        target.append(INCLUDE_CMD).append(' ');
        PResource resource = PResource.of(fileElement.file());
        if (context != null) {
            target.append(SyntaxUtils.valueToToken(context, resource));
        } else {
            target.append(SyntaxUtils.valueToToken(resource));
        }
        target.append('\n');
    }

    private void writeLineElement(Appendable target, ProjectElement.Line lineElement)
            throws IOException {
        target.append(lineElement.line()).append('\n');
    }

    static void write(ProjectModel model, Appendable target) throws IOException {
        ProjectWriter writer = new ProjectWriter(model);
        writer.doWrite(target);
    }
    
}
