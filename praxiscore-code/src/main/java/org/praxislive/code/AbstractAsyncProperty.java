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
 */
package org.praxislive.code;

import org.praxislive.core.Value;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.TaskService;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;
import org.praxislive.core.services.LogLevel;

/**
 *
 * @param <V>
 */
public abstract class AbstractAsyncProperty<V> implements Control {
    
    private final Class<V> valueType;
    
    private CodeContext<?> context;
    private Call activeCall;
    private Call taskCall;
    private Value key;
    private Value portKey;
    private V value;
    private boolean latestSet;
    private long latest;

    
    protected AbstractAsyncProperty(Value initialKey, Class<V> valueType, V value) {
        this.valueType = valueType;
        this.key = initialKey;
        this.value = value;
    }
    
    protected void attach(CodeContext<?> context) {
        if (context == null) {
            throw new NullPointerException();
        }
        this.context = context;
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

    private void processInvoke(Call call, PacketRouter router) throws Exception {
        var args = call.args();
        long time = call.time();
        if (args.size() > 0 && isLatest(time)) {
            TaskService.Task task = createTask(args.get(0));
            // no exception so valid args
            if (task == null) {
                nullify(time);
            } else {
                startTask(task, router, time);
            }
            // managed to start task ok
            setLatest(time);
            if (activeCall != null) {
                respond(activeCall, activeCall.args().get(0), router);
                activeCall = null;
            }
            if (task == null) {
                key = args.get(0);
                respond(call, key, router);
            } else {
                activeCall = call;
            }
        } else {
            respond(call, key, router);
        }
    }

    private void processReturn(Call call, PacketRouter router) throws Exception {
        if (taskCall == null || taskCall.matchID() != call.matchID()) {
            //LOG.warning("Unexpected Call received\n" + call.toString());
            return;
        }
        taskCall = null;
        castAndSetValue(call.args().get(0));
        if (activeCall != null) {
            key = activeCall.args().get(0);
            respond(activeCall, key, router);
            activeCall = null;
        } else if (portKey != null) {
            key = portKey;
            portKey = null;
        }
        valueChanged(call.time());
    }

    private void processError(Call call, PacketRouter router) throws Exception {
        if (taskCall == null || taskCall.matchID() != call.matchID()) {
            //LOG.warning("Unexpected Call received\n" + call.toString());
            return;
        }
        if (activeCall != null) {
            router.route(activeCall.error(call.args()));
            activeCall = null;
        }
        var args = call.args();
        PError err;
        if (args.size() > 0) {
            err = PError.from(args.get(0)).orElse(PError.of(args.get(0).toString()));
        } else {
            err = PError.of("");
        }
        taskError(latest, err);
    }

    private void respond(Call call, Value arg, PacketRouter router) {

        if (call.isReplyRequired()) {
            if (router == null) {
                router = getLookup().find(PacketRouter.class)
                        .orElseThrow(IllegalStateException::new);
            }
            router.route(call.reply(arg));
            
        }
    }

    protected void portInvoke(long time, Value key) {
        if (isLatest(time)) {
            try {
                TaskService.Task task = createTask(key);
                if (task == null) {
                    this.key = key;
                    nullify(time);
                } else {
                    startTask(task, null, time);
                    portKey = key;
                }
                setLatest(time);
                if (activeCall != null) {
                    respond(activeCall, activeCall.args().get(0), null);
                    activeCall = null;
                }
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex, "Invalid signal sent to port");
            }

        }

    }

    private void castAndSetValue(Value result) {
        if (valueType.isInstance(result)) {
            value = valueType.cast(result);
            return;
        }
        value = PReference.from(result)
                .flatMap(r -> r.as(valueType))
                .orElse(null);
    }
    
    private Lookup getLookup() {
         return context.getLookup();
    }

    private ControlAddress getTaskSubmitAddress() throws ServiceUnavailableException {
        ComponentAddress service = context.locateService(TaskService.class)
                .orElseThrow(ServiceUnavailableException::new);
        return ControlAddress.of(service, TaskService.SUBMIT);
    }

    private void setLatest(long time) {
        latestSet = true;
        latest = time;
    }

    private boolean isLatest(long time) {
        if (latestSet) {
            return (time - latest) >= 0;
        } else {
            return true;
        }

    }

    protected Value getKey() {
        return key;
    }

    protected V getValue() {
        return value;
    }

    private void nullify(long time) {
        taskCall = null;
        portKey = null;
        value = null;
        valueChanged(time);
    }

    private void startTask(TaskService.Task task, PacketRouter router, long time)
            throws ServiceUnavailableException {
        ControlAddress to = getTaskSubmitAddress();
        if (router == null) {
            router = getLookup().find(PacketRouter.class)
                    .orElseThrow(() -> new IllegalStateException("No PacketRouter found"));
        }
        taskCall = Call.create(to, context.getAddress(this), time, PReference.of(task));
        router.route(taskCall);
    }

    protected abstract TaskService.Task createTask(Value key)
            throws Exception;

    protected void valueChanged(long time) {
    }

    protected void taskError(long time, PError error) {
    }

}
