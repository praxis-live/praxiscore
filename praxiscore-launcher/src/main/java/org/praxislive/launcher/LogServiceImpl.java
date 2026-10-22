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
 *
 */
package org.praxislive.launcher;

import java.util.List;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PError;

class LogServiceImpl extends AbstractRoot implements RootHub.ServiceProvider {

    private final LogProcessor logProcessor;

    LogServiceImpl(LogLevel logLevel) {
        this.logProcessor = new LogProcessor();
        this.logProcessor.setMinimumLevel(logLevel);
    }

    @Override
    public List<Class<? extends Service>> services() {
        return List.of(LogService.class);
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        if (call.isRequest()) {
            try {
                logProcessor.processLog(call);
                if (call.isReplyRequired()) {
                    router.route(call.reply());
                }
            } catch (Exception ex) {
                router.route(call.error(PError.of(ex)));
            }
        }
    }

    private class LogProcessor extends AbstractLogProcessor {

        @Override
        protected void flush() {
            System.err.println();
            System.err.flush();
        }

        @Override
        protected void write(ComponentAddress src, LogLevel level, String text) {
            System.err.println(text);
        }

    }

}
