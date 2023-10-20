/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2023 Neil C Smith.
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

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;

/**
 * A property control for shared code classes. A SharedCodeProperty can be added
 * to a container (eg. Root). The {@link SharedCodeContext} should be added to
 * the container's lookup where it can be found and used by contained code
 * components.
 */
public class SharedCodeProperty implements Control {

    public final static ControlInfo INFO
            = Info.control(c -> c.property()
            .input(PMap.class)
            .defaultValue(PMap.EMPTY)
            .property("shared-code", true));

    private final SharedCodeContext context;
    private final Lookup.Provider lookupContext;
    private final Consumer<LogBuilder> logHandler;

    private List<Value> value;
    private long latest;
    private Call activeCall;
    private Call taskCall;

    /**
     * Create a shared code property.
     *
     * @param lookupContext lookup provider, eg. the container
     * @param logHandler a consumer for logs
     */
    public SharedCodeProperty(Lookup.Provider lookupContext, Consumer<LogBuilder> logHandler) {
        this.lookupContext = Objects.requireNonNull(lookupContext);
        this.logHandler = Objects.requireNonNull(logHandler);
        context = new SharedCodeContext(this);
        value = List.of(PMap.EMPTY);
    }

    @Override
    public void call(Call call, PacketRouter router) throws Exception {
        if (call.isRequest()) {
            processInvoke(call, router);
        } else if (call.isReply()) {
            processReturn(call, router);
        } else {
            processError(call, router);
        }
    }

    /**
     * The shared code context, which should be added to the container's lookup.
     *
     * @return shared code context
     */
    public SharedCodeContext getSharedCodeContext() {
        return context;
    }

    Value getValue() {
        return value.get(0);
    }

    private void processInvoke(Call call, PacketRouter router) throws Exception {
        List<Value> args = call.args();
        long time = call.time();
        if (!args.isEmpty() && isLatest(time)) {
            ControlAddress service = getServiceAddress(lookupContext.getLookup());
            SharedCodeService.Task task = createTask(
                    PMap.from(args.get(0)).orElseThrow(IllegalArgumentException::new));
            taskCall = Call.create(service, call.to(), time, PReference.of(task));
            router.route(taskCall);
            setLatest(time);
            if (activeCall != null) {
                router.route(activeCall.reply(activeCall.args()));
            }
            activeCall = call;
        } else {
            router.route(call.reply(value));
        }

    }

    private void processReturn(Call call, PacketRouter router) {
        try {
            if (taskCall == null || taskCall.matchID() != call.matchID()) {
                return;
            }
            taskCall = null;
            SharedCodeService.Result result = PReference.from(call.args().get(0))
                    .flatMap(r -> r.as(SharedCodeService.Result.class))
                    .orElseThrow(IllegalArgumentException::new);
            context.update(result);
            logHandler.accept(result.getLog());
            value = activeCall.args();
            router.route(activeCall.reply(value));
            activeCall = null;
        } catch (Exception ex) {
            router.route(activeCall.error(PError.of(ex)));
            activeCall = null;
        }
    }

    private void processError(Call call, PacketRouter router) throws Exception {
        if (taskCall == null || taskCall.matchID() != call.matchID()) {
            return;
        }
        router.route(activeCall.error(call.args()));
        activeCall = null;
        var args = call.args();
        if (!args.isEmpty()) {
            var err = PError.from(args.get(0))
                    .orElseGet(() -> PError.of(args.get(0).toString()));
            var log = new LogBuilder(LogLevel.ERROR);
            log.log(LogLevel.ERROR, err);
            logHandler.accept(log);
        }
    }

    private ControlAddress getServiceAddress(Lookup lookup)
            throws ServiceUnavailableException {
        return ControlAddress.of(lookup.find(Services.class)
                .flatMap(s -> s.locate(SharedCodeService.class))
                .orElseThrow(ServiceUnavailableException::new),
                SharedCodeService.NEW_SHARED);
    }

    private SharedCodeService.Task createTask(PMap sources) {
        return new SharedCodeService.Task(
                sources,
                context.createDependentTasks(),
                LogLevel.WARNING);
    }

    private void setLatest(long time) {
        latest = time == 0 ? -1 : time;
    }

    private boolean isLatest(long time) {
        return latest == 0 || (time - latest) >= 0;
    }

}
