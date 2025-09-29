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
package org.praxislive.script.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.praxislive.core.Value;
import org.praxislive.core.types.PString;
import org.praxislive.script.Command;
import org.praxislive.script.Namespace;
import org.praxislive.script.StackFrame;

/**
 *
 */
class ProcessCmds {

    private static final Command EXEC = new ExecCmd();

    private ProcessCmds() {
    }

    static void install(Map<String, Command> commands) {
        commands.put("exec", EXEC);
    }

    private static class ExecCmd implements Command {

        @Override
        public StackFrame createStackFrame(Namespace namespace, List<Value> args) throws Exception {
            List<String> cmd = args.stream().map(Value::toString).toList();
            Path pwd = Path.of(FileCmds.getPWD(namespace));
            return StackFrame.async(() -> {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                Path tmp = Files.createTempFile("coreexec", ".tmp");
                try {
                    pb.directory(pwd.toFile());
                    pb.redirectOutput(tmp.toFile());
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                    Process p = pb.start();
                    int ret = p.waitFor();
                    if (ret == 0) {
                        String output = Files.readString(tmp);
                        return PString.of(output);
                    } else {
                        throw new IOException("Process returned error " + ret);
                    }
                } finally {
                    Files.delete(tmp);
                }
            });
        }

    }

}
