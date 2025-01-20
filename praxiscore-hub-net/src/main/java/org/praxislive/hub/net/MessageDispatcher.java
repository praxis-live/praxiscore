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
package org.praxislive.hub.net;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Protocol;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.types.PError;

import static java.lang.System.Logger.Level;

/**
 *
 *
 */
abstract class MessageDispatcher {

    private final static System.Logger LOG = System.getLogger(MessageDispatcher.class.getName());

    private static record SentCallInfo(Call localCall, long localCallTime) {

    }

    private static record ReceivedMessageInfo(Message message, SocketAddress sender) {

    }

    final static String SYS_PREFIX = "/_sys";

    private final Map<Integer, SentCallInfo> sentCalls;
    private final Map<Integer, ReceivedMessageInfo> receivedMessages;

    MessageDispatcher() {
        sentCalls = new LinkedHashMap<>();
        receivedMessages = new HashMap<>();
    }

    abstract void dispatchMessage(SocketAddress remote, Message msg) throws Exception;

    abstract void dispatchCall(Call call);

    abstract String getRemoteSysPrefix();

    abstract SocketAddress getPrimaryRemoteAddress();

    abstract long getTime();

    abstract ComponentAddress findService(Class<? extends Service> service)
            throws ServiceUnavailableException;

    void handleMessage(SocketAddress sender, Message msg) {
        try {
            if (msg instanceof Message.Send sendMsg) {
                handleSendMessage(sender, sendMsg);
            } else if (msg instanceof Message.Service serviceMsg) {
                handleServiceMessage(sender, serviceMsg);
            } else if (msg instanceof Message.Reply replyMsg) {
                handleReplyMessage(sender, replyMsg);
            } else if (msg instanceof Message.Error errorMsg) {
                handleErrorMessage(sender, errorMsg);
            }
        } catch (Exception e) {
            if (msg instanceof Message.Send || msg instanceof Message.Service) {
                try {
                    dispatchMessage(sender, new Message.Error(msg.matchID(),
                            List.of(PError.of(e))));
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Unable to dispatch error message", ex);
                }
            } else {
                LOG.log(Level.WARNING, "Unable to handle message", e);
            }
        }
    }

    void handleCall(Call call) {
        if (call.isRequest()) {
            handleInvokeCall(call);
        } else {
            handleResponseCall(call);
        }
    }

    void handleServiceCall(Call call, String serviceName, String serviceControl) {
        handleServiceCallImpl(call, serviceName, serviceControl);
    }

    void purge(long time, TimeUnit unit) {
        long ago = unit.toNanos(time);
        long now = getTime();
        Iterator<SentCallInfo> itr = sentCalls.values().iterator();
        while (itr.hasNext()) {
            SentCallInfo info = itr.next();
            if ((now - info.localCallTime()) < ago) {
                LOG.log(Level.TRACE, "No calls to purge");
                break;
            }
            itr.remove();
            LOG.log(Level.TRACE, "Purging call\n{0}", info.localCall());
            Call err = info.localCall().error(PError.of("Timeout"));
            dispatchCall(err);
        }
    }

    private void handleSendMessage(SocketAddress sender, Message.Send msg) throws Exception {
        var to = msg.to();
        var from = msg.from();
        String fromString = from.toString();
        if (fromString.startsWith(SYS_PREFIX)) {
            fromString = getRemoteSysPrefix() + fromString;
            from = ControlAddress.parse(fromString);
        }
        Call call = Call.create(to, from, getTime(), msg.args());
        dispatchCall(call);
        receivedMessages.put(call.matchID(), new ReceivedMessageInfo(msg, sender));
    }

    private void handleServiceMessage(SocketAddress sender, Message.Service msg) throws Exception {
        Class<? extends Service> service = Protocol.Type.fromName(msg.service())
                .map(Protocol.Type::asClass)
                .filter(Service.class::isAssignableFrom)
                .map(cls -> (Class<? extends Service>) cls)
                .orElseThrow(ServiceUnavailableException::new);
        var serviceAddress = findService(service);
        var to = ControlAddress.of(serviceAddress, msg.control());
        var from = msg.from();
        String fromString = from.toString();
        if (fromString.startsWith(SYS_PREFIX)) {
            fromString = getRemoteSysPrefix() + fromString;
            from = ControlAddress.parse(fromString);
        }
        Call call = Call.create(to, from, getTime(), msg.args());
        dispatchCall(call);
        receivedMessages.put(call.matchID(), new ReceivedMessageInfo(msg, sender));
    }

    private void handleReplyMessage(SocketAddress sender, Message.Reply msg) throws Exception {
        SentCallInfo info = sentCalls.remove(msg.matchID());
        if (info == null) {
            LOG.log(Level.DEBUG, "Unexpected message response\n{0}", msg);
            return;
        }
        Call call = info.localCall().reply(msg.args());
        dispatchCall(call);
    }

    private void handleErrorMessage(SocketAddress sender, Message.Error msg) throws Exception {
        SentCallInfo info = sentCalls.remove(msg.matchID());
        if (info == null) {
            LOG.log(Level.DEBUG, "Unexpected message response\n{0}", msg);
            return;
        }
        Call call = info.localCall().error(msg.args());
        dispatchCall(call);
    }

    private void handleInvokeCall(Call call) {
        var to = call.to();
        var toString = to.toString();
        if (toString.startsWith(getRemoteSysPrefix())) {
            toString = toString.substring(getRemoteSysPrefix().length());
            to = ControlAddress.of(toString);
        }
        try {
            dispatchMessage(getPrimaryRemoteAddress(), new Message.Send(
                    call.matchID(),
                    to,
                    call.from(),
                    call.args()
            ));
            sentCalls.put(call.matchID(), new SentCallInfo(call, getTime()));
        } catch (Exception ex) {
            dispatchCall(call.error(PError.of(ex)));
        }
    }

    private void handleServiceCallImpl(Call call, String serviceName, String serviceControl) {
        try {
            dispatchMessage(getPrimaryRemoteAddress(), new Message.Service(
                    call.matchID(),
                    serviceName,
                    serviceControl,
                    call.from(),
                    call.args()
            ));
            sentCalls.put(call.matchID(), new SentCallInfo(call, getTime()));
        } catch (Exception ex) {
            dispatchCall(call.error(PError.of(ex)));
        }
    }

    private void handleResponseCall(Call call) {
        var info = receivedMessages.remove(call.matchID());
        if (info == null) {
            LOG.log(Level.DEBUG, "Unexpected call response\n{0}", call);
            return;
        }
        Message msg = call.isError() ? new Message.Error(info.message().matchID(), call.args())
                : new Message.Reply(info.message().matchID(), call.args());
        try {
            dispatchMessage(info.sender(), msg);
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Unable to send response", ex);
        }
    }

}
