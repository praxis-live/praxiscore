/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith.
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
 *
 */

package org.praxislive.launcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PError;

class LogServiceImpl extends AbstractRoot implements RootHub.ServiceProvider {
    
    private final LogLevel logLevel;
    
    LogServiceImpl(LogLevel logLevel) {
        this.logLevel = Objects.requireNonNull(logLevel);
    }

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(LogService.class);
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        if (call.isRequest()) {
            try {
                processLog(call);
                if (call.isReplyRequired()) {
                    router.route(call.reply());
                }
            } catch (Exception ex) {
                router.route(call.error(PError.of(ex)));
            }
        }
    }
    
    private void processLog(Call call) throws Exception {
        var src = call.from().component();
        var args = call.args();
        for (int i = 1; i < args.size(); i += 2) {
            var level = LogLevel.valueOf(args.get(i - 1).toString());
            if (!logLevel.isLoggable(level)) {
                continue;
            }
            var arg = args.get(i);
            var sw = new StringWriter();
            var pw = new PrintWriter(sw);
            pw.append(level.name()).append(" : ").append(src.toString()).println();
            if (arg instanceof PError) {
                var err = (PError) arg;
                pw.append(err.exceptionType().getSimpleName()).append(" - ");
                pw.append(err.message()).append("\n");
                err.exception().ifPresent(ex -> ex.printStackTrace(pw));
            } else {
                pw.append(arg.toString()).println();
            }
            pw.flush();
            System.err.println(sw.toString());
        }
        System.err.flush();
    }

}
