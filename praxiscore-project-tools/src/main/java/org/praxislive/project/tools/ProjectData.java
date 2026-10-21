/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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
package org.praxislive.project.tools;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.SerializableProtocol;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;
import org.praxislive.project.GraphElement;
import org.praxislive.project.GraphModel;
import org.praxislive.project.ParseException;
import org.praxislive.project.ProjectElement;
import org.praxislive.project.ProjectModel;
import org.praxislive.project.SyntaxUtils;
import org.praxislive.script.Namespace;
import org.praxislive.script.ScriptStackFrame;
import org.praxislive.script.ScriptUtils;
import org.praxislive.script.StackFrame;
import org.praxislive.script.Variable;

import static org.praxislive.project.tools.ProjectCommands.message;

class ProjectData {

    static final String ROOT_EXT = ".pxr";
    static final String SUBGRAPH_EXT = ".pxg";
    static final String PROJECT_FILENAME = "project.pxp";
    static final String MIME_PXP = "text/x-praxis-project";
    static final String MIME_PXR = "text/x-praxis-root";

    private static final String PROJECT_CONSTANT = "_PROJECT_DATA";
    private static final String SETUP_HUB = "hub";
    private static final String SETUP_COMPILER = "compiler";
    private static final String SETUP_LIBRARIES = "libraries";

    private static final List<String> ALLOWED_COMMANDS = List.of(
            "@", "~", "file", "array", "map",
            "libraries", "sources", "shared-code-add",
            "include"
    );

    private final Path projectFile;
    private final List<ProjectElement> setupElements;
    private final List<ProjectElement> buildElements;
    private final List<ProjectElement> runElements;
    private final Set<ProjectElement> executedElements;

    private Lookup lookup;
    private boolean active;

    private ProjectData(Path projectFile, ProjectModel model, boolean active) {
        this.projectFile = projectFile;
        this.setupElements = new ArrayList<>(model.setupElements());
        this.buildElements = new ArrayList<>(model.buildElements());
        this.runElements = new ArrayList<>(model.runElements());
        executedElements = new LinkedHashSet<>();
        if (active) {
            this.active = true;
            executedElements.addAll(setupElements);
            executedElements.addAll(buildElements);
        }
        lookup = Lookup.EMPTY;
    }

    StackFrame execute(Namespace namespace, boolean run) {
        active = true;
        List<ProjectElement> elements = new ArrayList<>();
        for (ProjectElement element : setupElements) {
            if (executedElements.add(element)) {
                elements.add(element);
            }
        }
        for (ProjectElement element : buildElements) {
            if (executedElements.add(element)) {
                elements.add(element);
            }
        }
        if (run) {
            elements.addAll(runElements);
        }

        if (elements.isEmpty()) {
            return StackFrame.empty();
        }

        Namespace ns = namespace.createChild();
        StackFrame frame = ScriptStackFrame.forScript(ns,
                "cd " + projectFile.getParent().toUri()).inline().build();
        List<String> errors = new ArrayList<>();

        for (ProjectElement element : elements) {
            switch (element) {
                case ProjectElement.File file -> {
                    frame = frame.andThen(v -> {
                        return fileToStackFrame(ns, file)
                                .onError(errs -> {
                                    errors.add(handleError(element, errs));
                                    return StackFrame.empty();
                                });
                    });
                }
                case ProjectElement.Line line -> {
                    frame = frame.andThen(v -> {
                        return ScriptStackFrame.forScript(ns, line.line()).inline().build()
                                .onError(errs -> {
                                    errors.add(handleError(element, errs));
                                    return StackFrame.empty();
                                });
                    });
                }
            }
        }

        return frame.andThen(v -> {
            if (errors.isEmpty()) {
                return StackFrame.empty();
            } else {
                throw new RuntimeException(errors.stream().collect(Collectors.joining()));
            }
        });

    }

    StackFrame reload(Namespace namespace, boolean reloadAll) {
        Path pxp = projectFile();
        return StackFrame.async(() -> PString.of(Files.readString(pxp)))
                .andThen(v -> {
                    try {
                        ProjectModel model = ProjectModel.parse(pxp.getParent().toUri(),
                                v.getFirst().toString());
                        setupElements.clear();
                        setupElements.addAll(model.setupElements());
                        buildElements.clear();
                        buildElements.addAll(model.buildElements());
                        runElements.clear();
                        runElements.addAll(model.runElements());
                        if (active) {
                            if (reloadAll) {
                                executedElements.clear();
                            } else {
                                executedElements.removeIf(el
                                        -> !setupElements.contains(el)
                                        && !buildElements.contains(el));
                            }
                            return execute(namespace, false);
                        } else {
                            return StackFrame.empty();
                        }
                    } catch (ParseException ex) {
                        throw new IllegalArgumentException(ex);
                    }
                });
    }

    StackFrame reloadRoot(Namespace namespace, String rootID) {
        if (!active) {
            throw new IllegalStateException(message("project.inactive"));
        }
        ProjectElement.File file = executedRootFiles()
                .filter(el -> Objects.equals(rootID,
                        stripExtension(Path.of(el.file()).getFileName().toString(),
                                ROOT_EXT)))
                .findFirst().orElseThrow(IllegalArgumentException::new);
        Namespace ns = namespace.createChild();
        StackFrame frame = ScriptStackFrame.forScript(ns,
                "cd " + projectFile.getParent().toUri()).inline().build();
        return frame.andThen(v -> fileToStackFrame(ns, file));
    }

    StackFrame saveAll(Namespace namespace) {
        StackFrame frame = saveConfig(namespace);
        List<PResource> saved = new ArrayList<>();
        saved.add(PResource.of(projectFile.toUri()));
        List<String> roots = registeredRootIDs().toList();
        for (String rootID : roots) {
            frame = frame.andThen(v -> saveRoot(namespace, rootID))
                    .andThenMap(v -> {
                        PResource.from(v.getFirst()).ifPresent(saved::add);
                        return v;
                    });
        }
        return frame.andThen(v -> StackFrame.result(PArray.of(saved)));
    }

    StackFrame saveConfig(Namespace namespace) {
        if (!active) {
            throw new IllegalStateException(message("project.inactive"));
        }
        return ScriptStackFrame.forScript(namespace,
                """
                map hub [hub] compiler [compiler] libraries [libraries]
                """).build()
                .andThen(v -> {
                    PMap result = PMap.from(v.getFirst()).orElseThrow();
                    replaceSetupElement(ProjectElement.line(
                            PMap.of(SETUP_HUB, result.get("hub")).print()));
                    replaceSetupElement(ProjectElement.line(
                            PMap.of(SETUP_COMPILER, result.get("compiler")).print()));
                    replaceSetupElement(ProjectElement.line(
                            PMap.of(SETUP_LIBRARIES, result.get("libraries")).print()));
                    String fileContents = dataToModel().writeToString();
                    return StackFrame.async(() -> {
                        Files.writeString(projectFile, fileContents);
                        return PString.EMPTY;
                    });
                })
                .andThen(v -> StackFrame.result(PResource.of(projectFile.toUri())));
    }

    StackFrame saveRoot(Namespace namespace, String id) {
        Path dst = registeredRootFiles()
                .map(el -> Path.of(el.file()))
                .filter(p -> Objects.equals(id,
                        stripExtension(p.getFileName().toString(), ROOT_EXT)))
                .findFirst()
                .orElse(projectDir().resolve(id + ROOT_EXT));
        ComponentAddress rootAddress = ComponentAddress.of("/" + id);
        return StackFrame.call(ControlAddress.of(rootAddress,
                SerializableProtocol.SERIALIZE), List.of())
                .andThen(v -> {
                    GraphModel model = GraphModel.fromSerializedRoot(id,
                            PMap.from(v.getFirst()).orElseThrow())
                            .withContext(projectDir().toUri());
                    GraphElement.Property sharedCode = model.root().properties()
                            .get("shared-code");
                    Path sharedCodePath = projectDir().resolve("code").resolve(id);
                    if (sharedCode != null && Files.isDirectory(sharedCodePath)) {
                        model = model.withTransform(r -> {
                            r.transformProperty("shared-code", p
                                    -> GraphElement.property(
                                            GraphElement.command("sources \"code/%s\""
                                                    .formatted(id)),
                                            p.value()));
                        });
                        PString contents = PString.of(model.writeToString());
                        return ScriptStackFrame.forScript(namespace, """
                                sources-write $CODE $PATH
                                """)
                                .createConstant("CODE", sharedCode.value())
                                .createConstant("PATH", PResource.of(sharedCodePath.toUri()))
                                .build()
                                .andThen(vv -> StackFrame.result(List.of(contents)));
                    } else {
                        PString contents = PString.of(model.writeToString());
                        return StackFrame.result(List.of(contents));
                    }
                })
                .andThen(v -> StackFrame.async(() -> {
                    Files.writeString(dst, v.getFirst().toString());
                    return PResource.of(dst.toUri());
                }));
    }

    boolean isActive() {
        return active;
    }

    Path projectDir() {
        return projectFile.getParent();
    }

    Path projectFile() {
        return projectFile;
    }

    Stream<ProjectElement> setupElements() {
        return setupElements.stream();
    }

    Stream<ProjectElement> buildElements() {
        return buildElements.stream();
    }

    Stream<ProjectElement> runElements() {
        return runElements.stream();
    }

    Stream<ProjectElement.File> registeredRootFiles() {
        return buildElements()
                .filter(ProjectElement.File.class::isInstance)
                .map(ProjectElement.File.class::cast)
                .filter(el -> hasExtension(el.file().getPath(), ROOT_EXT));
    }

    Stream<String> registeredRootIDs() {
        return registeredRootFiles()
                .map(el -> Path.of(el.file()))
                .map(p -> stripExtension(p.getFileName().toString(), ROOT_EXT));
    }

    Stream<ProjectElement.File> executedRootFiles() {
        return executedElements.stream()
                .filter(ProjectElement.File.class::isInstance)
                .map(ProjectElement.File.class::cast)
                .filter(el -> hasExtension(el.file().getPath(), ROOT_EXT));
    }

    Stream<String> executedRootIDs() {
        return executedRootFiles()
                .map(el -> Path.of(el.file()))
                .map(p -> stripExtension(p.getFileName().toString(), ROOT_EXT));
    }

    String relativePath(PResource resource) {
        try {
            Path path = Path.of(resource.value());
            return projectDir().relativize(path).toString();
        } catch (Exception ex) {
            return resource.toString();
        }
    }

    private void attach(Lookup lookup) {
        this.lookup = lookup;
    }

    private ProjectModel dataToModel() {
        ProjectModel.Builder builder = ProjectModel.builder();
        builder.context(projectDir().toUri());
        setupElements.forEach(builder::setupElement);
        buildElements.forEach(builder::buildElement);
        runElements.forEach(builder::runElement);
        return builder.build();
    }

    private void replaceSetupElement(ProjectElement.Line line) {
        String command = line.tokens().getFirst().getText();
        List<ProjectElement> matches = setupElements.stream()
                .filter(e -> e instanceof ProjectElement.Line l
                        && command.equals(l.tokens().getFirst().getText()))
                .toList();
        if (matches.size() == 1) {
            setupElements.set(setupElements.indexOf(matches.getFirst()), line);
        } else {
            setupElements.removeAll(matches);
            setupElements.add(line);
        }
    }

    private StackFrame fileToStackFrame(Namespace namespace, ProjectElement.File element) {
        String path = element.file().getPath();
        String script = "include %s".formatted(SyntaxUtils.valueToToken(
                projectFile.getParent().toUri(),
                PResource.of(element.file())));
        if (hasExtension(path, ROOT_EXT)) {
            return ScriptStackFrame.forScript(namespace, script)
                    .trapErrors()
                    .allowedCommands(ALLOWED_COMMANDS)
                    .build();
        } else {
            return ScriptStackFrame.forScript(namespace, script).build();
        }
    }

    private String handleError(ProjectElement element, List<Value> errors) {
        String header = switch (element) {
            case ProjectElement.File file ->
                "Errors : " + file.file().getPath() + "\n\n";
            case ProjectElement.Line line ->
                line.line().lines().limit(5)
                .map(l -> l.indent(4))
                .collect(Collectors.joining("", "Errors :\n", "\n\n"));
        };
        return errors.stream()
                .map(v -> PError.from(v)
                        .map(err -> err.errorType() + "\n" + err.message())
                        .orElse(v.toString()))
                .collect(Collectors.joining("\n", header, "\n------\n\n"));
    }

    static boolean hasExtension(String path, String ext) {
        return path.toLowerCase(Locale.ROOT)
                .endsWith(ext.toLowerCase(Locale.ROOT));
    }

    static String stripExtension(String filename, String ext) {
        if (hasExtension(filename, ext)) {
            return filename.substring(0, filename.length() - ext.length());
        } else {
            return filename;
        }
    }

    static boolean hasCurrentProject(Namespace namespace) {
        if (namespace.getVariable(PROJECT_CONSTANT) != null) {
            return true;
        }
        return namespace.getValue("_FILE", PString.EMPTY)
                .toString().endsWith(PROJECT_FILENAME);
    }

    /**
     * Create a task stackframe with the project data context. The provided
     * function will be called asynchronously, wrapped in a stackframe that
     * finds or creates the project data.
     *
     * @param namespace current namespace
     * @param init stackframe constructor
     * @return compound stackframe
     */
    static StackFrame task(Namespace namespace, Function<ProjectData, StackFrame> init) {
        return findProjectData(namespace)
                .andThen(args -> init.apply(extractProjectData(args)));
    }

    private static StackFrame findProjectData(Namespace namespace) {
        Variable dataVar = namespace.getVariable(PROJECT_CONSTANT);
        if (dataVar != null) {
            return StackFrame.result(List.of(dataVar.getValue()));
        } else {
            URI file = Optional.ofNullable(namespace.getVariable("_FILE"))
                    .map(Variable::getValue)
                    .flatMap(PResource::from)
                    .map(PResource::value)
                    .orElse(null);
            URI pwd = ScriptUtils.currentWorkingDirectory(namespace);
            Namespace globalNS = ScriptUtils.findGlobalNamespace(namespace)
                    .orElseThrow(() -> new IllegalStateException("No global namespace"));
            return StackFrame.async(() -> PReference.of(createProjectData(file, pwd)))
                    .andThen(args -> StackFrame.inline(env -> {
                        ProjectData data = extractProjectData(args);
                        data.attach(env.getLookup());
                        globalNS.createConstant(PROJECT_CONSTANT, args.getFirst());
                        return args;
                    }));
        }
    }

    private static ProjectData createProjectData(URI file, URI pwd) throws Exception {
        Path projectFile;
        boolean active;
        if (file != null && file.getPath().endsWith(PROJECT_FILENAME)) {
            projectFile = Path.of(file);
            active = true;
        } else {
            projectFile = Path.of(pwd).resolve(PROJECT_FILENAME);
            active = false;
        }
        if (!Files.exists(projectFile)) {
            throw new UnsupportedOperationException("No project file found");
        }
        ProjectModel model = ProjectModel.parse(
                projectFile.getParent().toUri(),
                Files.readString(projectFile));
        return new ProjectData(projectFile, model, active);
    }

    private static ProjectData extractProjectData(List<Value> args) {
        return PReference.from(args.getFirst())
                .flatMap(ref -> ref.as(ProjectData.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Project data not found"));
    }

}
