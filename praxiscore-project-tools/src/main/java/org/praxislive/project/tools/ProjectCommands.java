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

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import org.praxislive.core.OrderedMap;
import org.praxislive.core.Value;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.CommandInstaller;
import org.praxislive.script.Namespace;
import org.praxislive.script.ScriptStackFrame;
import org.praxislive.script.StackFrame;

/**
 * Central installer for project tools commands.
 */
public final class ProjectCommands implements CommandInstaller {

    private static final ResourceBundle MESSAGES
            = ResourceBundle.getBundle(ProjectCommands.class.getPackageName() + ".Messages");

    private static final Map<String, Command> COMMANDS = Map.ofEntries(
            Map.entry("project", new Project()),
            Map.entry("project-build", new Project("build")),
            Map.entry("project-run", new Project("run"))
    );

    private static final Map<String, SubCommand> SUB_COMMANDS = OrderedMap.ofEntries(
            Map.entry("build", new ProjectExecute(false)),
            Map.entry("run", new ProjectExecute(true)),
            Map.entry("save-all", new SaveAll()),
            Map.entry("save-config", new SaveConfig()),
            Map.entry("save-root", new SaveRoot())
    );

    @Override
    public void install(Map<String, Command> commands) {
        commands.putAll(COMMANDS);
    }

    static String message(String key) {
        return MESSAGES.getString(key);
    }

    static String message(String key, Object... arguments) {
        return MessageFormat.format(MESSAGES.getString(key), arguments);
    }

    static StackFrame log(Namespace namespace, LogLevel level, String message) {
        return ScriptStackFrame.forScript(namespace,
                """
                log $LEVEL $MESSAGE
                """)
                .createConstant("LEVEL", PString.of(level))
                .createConstant("MESSAGE", PString.of(message))
                .build().andThen(v -> StackFrame.empty());
    }

    private static class Project implements Command {

        private final String subcommand;

        private Project() {
            this(null);
        }

        private Project(String subcommand) {
            this.subcommand = subcommand;
        }

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            String subCmd = subcommand;
            List<Value> subArgs = args;
            if (subCmd == null) {
                if (args.isEmpty()) {
                    return createCommandChooser(namespace);
                } else {
                    subCmd = args.getFirst().toString();
                    subArgs = args.stream().skip(1).toList();
                }
            }
            Command sub = SUB_COMMANDS.get(subCmd);
            return sub.createStackFrame(namespace, subArgs);
        }

        @Override
        public String description() {
            return message("project.description");
        }

        private StackFrame createCommandChooser(Namespace namespace) throws Exception {
            List<PString> options = SUB_COMMANDS.entrySet().stream()
                    .filter(e -> !e.getValue().isAdvanced())
                    .map(e -> PString.of(e.getKey()))
                    .toList();
            return ScriptStackFrame.forScript(namespace, """
                        user-input-select $MESSAGE $OPTIONS
                        """)
                    .createConstant("MESSAGE", PString.of(message("project.choose")))
                    .createConstant("OPTIONS", PArray.of(options))
                    .build()
                    // catch error from select but not from subcommand
                    .onError(err -> StackFrame.empty())
                    .andThen(res -> {
                        if (!res.isEmpty()) {
                            try {
                                return SUB_COMMANDS.get(res.getFirst().toString())
                                        .createStackFrame(namespace, List.of());
                            } catch (Exception ex) {
                            }
                        }
                        return StackFrame.empty();
                    });
        }

    }

    private static interface SubCommand extends Command {

        default boolean isAdvanced() {
            return false;
        }

        // @TODO allow sub-selection by category in chooser, etc.
        default String category() {
            return "";
        }

    }

    private static abstract class InteractiveCommand implements SubCommand {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            return ProjectData.task(namespace, data -> {
                if (args.isEmpty()) {
                    return userInput(data, namespace)
                            .andThen(res -> {
                                if (!res.isEmpty()) {
                                    return execute(data, namespace,
                                            PMap.from(res.get(0))
                                                    .orElseThrow(IllegalStateException::new));
                                } else {
                                    return StackFrame.empty();
                                }
                            });
                } else if (args.size() == 1) {
                    PMap params = PMap.from(args.get(0)).orElseThrow(IllegalArgumentException::new);
                    return execute(data, namespace, params);
                } else {
                    throw new IllegalArgumentException();
                }

            });
        }

        protected StackFrame userInput(ProjectData data, Namespace namespace) {
            return StackFrame.result(PMap.EMPTY);
        }

        protected abstract StackFrame execute(ProjectData data, Namespace namespace, PMap params);

    }

    private static class ProjectExecute extends InteractiveCommand {

        private final boolean run;

        private ProjectExecute(boolean run) {
            this.run = run;
        }

        @Override
        protected StackFrame userInput(ProjectData data, Namespace namespace) {
            return StackFrame.result(PMap.EMPTY);
        }

        @Override
        protected StackFrame execute(ProjectData data, Namespace namespace, PMap params) {
            return data.execute(namespace, run);
        }

    }

    private static class SaveAll extends InteractiveCommand {

        @Override
        protected StackFrame execute(ProjectData data, Namespace namespace, PMap params) {
            return data.saveAll(namespace)
                    .andThen(v -> {
                        String files = PArray.from(v.getFirst()).orElse(PArray.EMPTY)
                                .stream()
                                .flatMap(res -> PResource.from(res).stream())
                                .map(data::relativePath)
                                .collect(Collectors.joining("\n"));
                        String msg = message("saveall.success", files);
                        return log(namespace, LogLevel.INFO, msg);
                    });
        }

    }

    private static class SaveConfig extends InteractiveCommand {

        @Override
        protected StackFrame execute(ProjectData data, Namespace namespace, PMap params) {
            return data.saveConfig(namespace)
                    .andThen(v -> {
                        String path = PResource.from(v.getFirst())
                                .map(data::relativePath)
                                .orElse("");
                        return log(namespace, LogLevel.INFO,
                                message("saveconfig.success", path));
                    });
        }

    }

    private static class SaveRoot extends InteractiveCommand {

        @Override
        protected StackFrame userInput(ProjectData data, Namespace namespace) {
            return ScriptStackFrame.forScript(namespace,
                    """
                    var roots [roots]
                    if [== 0 [array-size $roots]] {
                        fail $ERROR_NO_ROOTS
                    } else {
                        try {
                            var id [user-input-select $MESSAGE $roots]
                            map id $id
                        }
                    }
                    """)
                    .createConstant("MESSAGE", PString.of(message("saveroot.choose")))
                    .createConstant("ERROR_NO_ROOTS",
                            PString.of(message("saveroot.error-no-roots")))
                    .build();
        }

        @Override
        protected StackFrame execute(ProjectData data, Namespace namespace, PMap params) {
            String id = params.get("id").toString();
            return data.saveRoot(namespace, id)
                    .andThen(v -> {
                        String path = PResource.from(v.getFirst())
                                .map(data::relativePath)
                                .orElse("");
                        return log(namespace, LogLevel.INFO,
                                message("saveroot.success", "/" + id, path));
                    });
        }

    }

}
