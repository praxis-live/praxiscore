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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.praxislive.project.ModelUtils.validateContext;

/**
 * Model for project scripts. Project scripts are a series of commands or file
 * includes, split into three sections - setup, build and run.
 * <p>
 * The setup section normally consists of commands to configure the hub,
 * libraries, compiler, etc.
 * <p>
 * The build section normally consists of includes for the various root graph
 * files.
 * <p>
 * The run section normally includes lines to start the various roots. Unlike
 * the other two sections, elements in the run section should usually support
 * repeated execution.
 */
public final class ProjectModel {

    static final String INCLUDE_CMD = "include";
    static final String BUILD_LEVEL_SWITCH = "<<<BUILD>>>";
    static final String RUN_LEVEL_SWITCH = "<<<RUN>>>";

    private final List<ProjectElement> setupElements;
    private final List<ProjectElement> buildElements;
    private final List<ProjectElement> runElements;
    private final URI context;

    private ProjectModel(List<ProjectElement> setupElements,
            List<ProjectElement> buildElements,
            List<ProjectElement> runElements,
            URI context) {
        this.setupElements = List.copyOf(setupElements);
        this.buildElements = List.copyOf(buildElements);
        this.runElements = List.copyOf(runElements);
        this.context = context;
    }

    /**
     * Immutable list of the elements in the setup section.
     *
     * @return setup elements
     */
    public List<ProjectElement> setupElements() {
        return setupElements;
    }

    /**
     * Immutable list of the elements in the build section.
     *
     * @return build elements
     */
    public List<ProjectElement> buildElements() {
        return buildElements;
    }

    /**
     * Immutable list of the elements in the run section.
     *
     * @return run elements
     */
    public List<ProjectElement> runElements() {
        return runElements;
    }

    /**
     * Access the optional context (eg. working dir) for resolving relative file
     * values.
     *
     * @return optional context
     */
    public Optional<URI> context() {
        return Optional.ofNullable(context);
    }

    /**
     * Create a new project model with a different context. The context is used
     * to relativize resources when writing. Use {@code null} to create a model
     * without context.
     *
     * @param context new resource context
     * @return new graph model
     */
    public ProjectModel withContext(URI context) {
        return new ProjectModel(setupElements, buildElements, runElements, validateContext(context));
    }

    /**
     * Write the model as a script to the given target.
     *
     * @param target write destination
     * @throws IOException
     */
    public void write(Appendable target) throws IOException {
        ProjectWriter.write(this, target);
    }

    /**
     * Write the project model to a String. This is shorthand for passing in a
     * {@link StringBuilder} to {@link #write(java.lang.Appendable)}.
     * <p>
     * The output of this method is suitable for parsing back into a model, as
     * distinct from the output of {@link #toString()}.
     *
     * @return model as script
     */
    public String writeToString() {
        StringBuilder sb = new StringBuilder();
        try {
            write(sb);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this
                || obj instanceof ProjectModel other
                && Objects.equals(setupElements, other.setupElements)
                && Objects.equals(buildElements, other.buildElements)
                && Objects.equals(runElements, other.runElements)
                && Objects.equals(context, other.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(setupElements, buildElements, runElements, context);
    }

    @Override
    public String toString() {
        return "ProjectModel {\n  Context : " + context + "\n  Project :\n" + writeToString().indent(4) + "\n}";
    }

    /**
     * Create a project model builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parse the given project script into a project model.
     *
     * @param script project script
     * @return model
     * @throws ParseException if script is not a valid project
     */
    public static ProjectModel parse(String script) throws ParseException {
        return ProjectParser.parse(script);
    }

    /**
     * Parse the given project script and context into a project model. Relative
     * includes will be parsed against the provided context.
     *
     * @param context project context
     * @param script project script
     * @return model
     * @throws ParseException if script is not a valid project
     */
    public static ProjectModel parse(URI context, String script) throws ParseException {
        return ProjectParser.parse(validateContext(context), script);
    }

    /**
     * Project model builder.
     */
    public static final class Builder {

        private final List<ProjectElement> setupElements;
        private final List<ProjectElement> buildElements;
        private final List<ProjectElement> runElements;

        private URI context;

        private Builder() {
            setupElements = new ArrayList<>();
            buildElements = new ArrayList<>();
            runElements = new ArrayList<>();
        }

        /**
         * Add a setup element.
         *
         * @param element setup element
         * @return this
         */
        public Builder setupElement(ProjectElement element) {
            setupElements.add(Objects.requireNonNull(element));
            return this;
        }

        /**
         * Add a build element.
         *
         * @param element build element
         * @return this
         */
        public Builder buildElement(ProjectElement element) {
            buildElements.add(Objects.requireNonNull(element));
            return this;
        }

        /**
         * Add a run element.
         *
         * @param element run element
         * @return this
         */
        public Builder runElement(ProjectElement element) {
            runElements.add(Objects.requireNonNull(element));
            return this;
        }

        /**
         * Add a context. If provided, the context must be an absolute URI.
         *
         * @param context project context
         * @return this
         */
        public Builder context(URI context) {
            this.context = validateContext(context);
            return this;
        }

        /**
         * Build the project model.
         *
         * @return created project model
         */
        public ProjectModel build() {
            return new ProjectModel(setupElements, buildElements, runElements, context);
        }

    }

}
