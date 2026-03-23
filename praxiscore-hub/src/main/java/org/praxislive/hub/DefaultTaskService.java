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
package org.praxislive.hub;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.Settings;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.TaskService;
import org.praxislive.core.services.TaskService.Task;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;

/**
 *
 */
class DefaultTaskService extends AbstractRoot implements RootHub.ServiceProvider {

    private static final String KEY_SYSTEM_THREADS = "hub.tasks.systemthreads";

    private final boolean systemThreads;
    private final ExecutorService threadService;

    public DefaultTaskService() {
        systemThreads = Settings.getBoolean(KEY_SYSTEM_THREADS, false);
        if (systemThreads) {
            threadService = Executors.newCachedThreadPool((Runnable r) -> {
                Thread thr = new Thread(r);
                thr.setPriority(Thread.MIN_PRIORITY);
                return thr;
            });
        } else {
            threadService = Executors.newVirtualThreadPerTaskExecutor();
        }
    }

    @Override
    protected void activating() {
        setRunning();
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        if (call.isRequest()) {
            try {
                submitTask(getRootHub(), call);
            } catch (Exception ex) {
                router.route(call.error(PError.of(ex)));
            }
        }
    }

    @Override
    public List<Class<? extends Service>> services() {
        return Collections.singletonList(TaskService.class);
    }

    @Override
    protected void terminating() {
        threadService.shutdownNow();
    }

    private void submitTask(RootHub hub, Call call) throws Exception {
        Task task = PReference.from(call.args().getFirst())
                .flatMap(ref -> ref.as(Task.class))
                .orElseThrow(() -> new IllegalArgumentException("No task found"));
        threadService.execute(() -> {
            try {
                Value value = task.execute();
                hub.dispatch(call.reply(value));
            } catch (Throwable t) {
                PError error = PError.of(t instanceof Exception ex ? ex : new RuntimeException(t));
                hub.dispatch(call.error(error));
            }
        });

    }

}
