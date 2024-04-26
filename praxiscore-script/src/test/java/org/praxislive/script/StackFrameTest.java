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
package org.praxislive.script;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.Packet;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.Services;
import org.praxislive.core.services.TaskService;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;
import org.praxislive.script.StackFrame.State;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class StackFrameTest {

    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");
//    private static final int TIMEOUT = Integer.getInteger("praxis.test.timeout", 10000);

    public StackFrameTest() {
    }

    @Test
    public void testCallStackFrame() {
        logTest("testCallStackFrame");
        var env = new EnvImpl();
        var to = "/test.control";
        var frame = StackFrame.call(ControlAddress.of(to), PString.of("FOO"));
        assertEquals(State.Incomplete, frame.getState());
        frame.process(env);
        assertEquals(State.Incomplete, frame.getState());
        var call = env.poll();
        logCall("Received call", call);
        assertEquals(to, call.to().toString());
        assertEquals(env.getTime(), call.time());
        assertEquals(EnvImpl.ADDRESS, call.from());
        assertEquals("FOO", call.args().get(0).toString());
        frame.postResponse(call.reply(PString.of("BAR")));
        assertEquals(State.OK, frame.getState());
        assertEquals("BAR", frame.result().get(0).toString());
    }

    @Test
    public void testCallAndThenMapStackFrame() {
        logTest("testCallAndThenMapStackFrame");
        var env = new EnvImpl();
        var to = "/test.control";
        var frame = StackFrame.call(ControlAddress.of(to), PString.of("FOO"))
                .andThenMap(args -> {
                    if ("BAR".equals(args.get(0).toString())) {
                        return List.of(PString.of("BAZ"));
                    } else {
                        return List.of(PString.of("ERROR"));
                    }
                });
        assertEquals(State.Incomplete, frame.getState());
        frame.process(env);
        assertEquals(State.Incomplete, frame.getState());
        var call = env.poll();
        logCall("Received call", call);
        assertEquals(to, call.to().toString());
        assertEquals(env.getTime(), call.time());
        assertEquals(EnvImpl.ADDRESS, call.from());
        assertEquals("FOO", call.args().get(0).toString());
        frame.postResponse(call.reply(PString.of("BAR")));
        assertEquals(State.Incomplete, frame.getState());
        frame.process(env);
        assertEquals(State.OK, frame.getState());
        assertEquals("BAZ", frame.result().get(0).toString());
    }

    @Test
    public void testAsyncStackFrame() throws Exception {
        logTest("testAsyncStackFrame");
        var env = new EnvImpl();
        var frame = StackFrame.async(() -> PNumber.of(42));
        assertEquals(State.Incomplete, frame.getState());
        frame.process(env);
        assertEquals(State.Incomplete, frame.getState());
        var call = env.poll();
        logCall("Received task", call);
        assertEquals(EnvImpl.SERVICE, call.to().component());
        assertEquals(TaskService.SUBMIT, call.to().controlID());
        TaskService.Task task = PReference.from(call.args().get(0))
                .flatMap(r -> r.as(TaskService.Task.class))
                .orElseThrow();
        frame.postResponse(call.reply(task.execute()));
        assertEquals(State.OK, frame.getState());
        assertEquals(42, PNumber.from(frame.result().get(0)).orElseThrow().toIntValue());
    }

    private static void logTest(String testName) {
        if (VERBOSE) {
            System.out.println("");
            System.out.println("");
            System.out.println(testName);
            System.out.println("====================");
        }
    }

    private static void logCall(String msg, Call call) {
        if (VERBOSE) {
            System.out.println(msg);
            System.out.println(call);
        }
    }

    private static class EnvImpl implements Env {

        private static final ControlAddress ADDRESS = ControlAddress.of("/stack.eval");
        private static final ComponentAddress SERVICE = ComponentAddress.of("/service");

        private final Queue<Packet> queue;
        private final Lookup lookup;

        private EnvImpl() {
            this.queue = new LinkedList<>();
            this.lookup = Lookup.of(new Services() {
                @Override
                public Optional<ComponentAddress> locate(Class<? extends Service> service) {
                    return Optional.of(SERVICE);
                }

                @Override
                public Stream<ComponentAddress> locateAll(Class<? extends Service> service) {
                    return locate(service).stream();
                }
            });
        }

        @Override
        public ControlAddress getAddress() {
            return ADDRESS;
        }

        @Override
        public Lookup getLookup() {
            return lookup;
        }

        @Override
        public PacketRouter getPacketRouter() {
            return queue::add;
        }

        @Override
        public long getTime() {
            return 12345;
        }

        public Call poll() {
            Packet p = queue.poll();
            if (p instanceof Call c) {
                return c;
            } else {
                return null;
            }
        }

    }

}
