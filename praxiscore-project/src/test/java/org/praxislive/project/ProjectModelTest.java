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

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class ProjectModelTest {

    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");

    private static final URI PARENT_CONTEXT = URI.create("file:/parent/");

    public ProjectModelTest() {
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("START TEST : " + info.getDisplayName());
        }
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("END TEST : " + info.getDisplayName());
            System.out.println("=====================================");
        }
    }

    @Test
    public void testParseProject() throws ParseException {
        String project = """
                         hub {
                           proxies {
                             all {
                               host localhost
                             }
                           }
                         }
                         compiler {
                           release 21
                         }
                         libraries {
                           pkg:maven/org.praxislive/praxiscore-api
                         }
                         
                         # <<<BUILD>>>
                         include [file "root1.pxr"]
                         include [file "root2.pxr"]
                         
                         # <<<RUN>>>
                         /root1.start
                         /root2.start
                         """;
        ProjectModel model = ProjectModel.parse(PARENT_CONTEXT, project);
        List<ProjectElement> setup = model.setupElements();
        List<ProjectElement> build = model.buildElements();
        List<ProjectElement> run = model.runElements();
        assertEquals(3, setup.size());
        assertEquals(2, build.size());
        assertEquals(2, run.size());
        assertEquals("hub", ((ProjectElement.Line) (setup.get(0))).tokens().get(0).getText());
        assertEquals("compiler", ((ProjectElement.Line) (setup.get(1))).tokens().get(0).getText());
        assertEquals("libraries", ((ProjectElement.Line) (setup.get(2))).tokens().get(0).getText());
        assertEquals(PARENT_CONTEXT + "root1.pxr",
                ((ProjectElement.File) (build.get(0))).file().toString());
        assertEquals(PARENT_CONTEXT + "root2.pxr",
                ((ProjectElement.File) (build.get(1))).file().toString());
        assertEquals("/root1.start", ((ProjectElement.Line) (run.get(0))).tokens().get(0).getText());
        assertEquals("/root2.start", ((ProjectElement.Line) (run.get(1))).tokens().get(0).getText());
    }

    @Test
    public void testWriteProject() throws ParseException {
        String project = """
                         compiler {release 21}
                         
                         # <<<BUILD>>>
                         include [file "root.pxr"]
                         
                         # <<<RUN>>>
                         /root.start
                         """;
        ProjectModel model = ProjectModel.builder()
                .context(PARENT_CONTEXT)
                .setupElement(ProjectElement.line("compiler {release 21}"))
                .buildElement(ProjectElement.file(PARENT_CONTEXT.resolve("root.pxr")))
                .runElement(ProjectElement.line("/root.start"))
                .build();
        String output = model.writeToString();
        if (VERBOSE) {
            System.out.println("Model output");
            System.out.println(output);
        }
        assertEquals(project, output);
        ProjectModel roundtrip = ProjectModel.parse(PARENT_CONTEXT, output);
        assertEquals(model, roundtrip);
    }

}
