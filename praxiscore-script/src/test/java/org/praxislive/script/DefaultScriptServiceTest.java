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
package org.praxislive.script;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.praxislive.core.Call;
import org.praxislive.core.Clock;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.Packet;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PError;

import static org.junit.jupiter.api.Assertions.*;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 *
 */
public class DefaultScriptServiceTest {

    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");
    private static final int TIMEOUT = Integer.getInteger("praxis.test.timeout", 10000);

    public DefaultScriptServiceTest() {
    }

    @Test
    public void testInlineScript() throws Exception {
        logTest("testInlineScript");
        String script = """
                        set V1 "One"
                        set V2 "Two"
                        set V3 $V1
                        set RET [array $V1 $V2 $V3]
                        """;
        var root = new DefaultScriptService();
        try (var hub = new RootHubImpl("script", root)) {
            hub.start();
            hub.send("/script.eval", "/hub.result", script);
            var result = hub.poll();
            logCall("Result received", result);
            assertTrue(result.isReply());
            assertEquals(1, result.args().size());
            var expected = Stream.of("One", "Two", "One")
                    .map(Value::ofObject)
                    .collect(PArray.collector());
            assertEquals(expected, result.args().get(0));
        }

    }

    @Test
    public void testAtScript() throws Exception {
        logTest("testAtScript");
        String script = """
                        .foo
                        @ /bar {
                            set V 42
                            @ ./baz test:component {
                                .value $V
                            }
                        }
                        """;
        var root = new DefaultScriptService();
        try (var hub = new RootHubImpl("script", root)) {
            hub.start();
            hub.send("/script.eval", "/hub.result", script);
            Call call = hub.poll();
            logCall("Call to /hub.foo received", call);
            assertTrue(call.isRequest());
            assertEquals("/hub.foo", call.to().toString());
            assertTrue(call.args().isEmpty());
            hub.dispatch(call.reply());

            call = hub.poll();
            logCall("Call to /bar.add-child received", call);
            assertTrue(call.isRequest());
            assertEquals("/bar.add-child", call.to().toString());
            hub.dispatch(call.reply());

            call = hub.poll();
            logCall("Call to /bar/baz.value received", call);
            assertTrue(call.isRequest());
            assertEquals("/bar/baz.value", call.to().toString());
            assertEquals(1, call.args().size());
            assertEquals("42", call.args().get(0).toString());
            hub.dispatch(call.reply());

            call = hub.poll();
            logCall("Result received", call);

            assertTrue(call.isReply());

        }

    }

    @Test
    public void testEvalInline() throws Exception {
        logTest("testEvalInline");
        String script = """
                        eval --inline {
                            var X 42
                            /hub.value $X
                        }
                        set X [echo $X $X]
                        /hub.value $X
                        eval {
                            var Y 84
                            /hub.value $Y 
                        }
                        /hub.value $Y
                        """;
        var root = new DefaultScriptService();
        try (var hub = new RootHubImpl("script", root)) {
            hub.start();
            hub.send("/script.eval", "/hub.result", script);

            for (String expected : new String[]{"42", "4242", "84"}) {
                Call call = hub.poll();
                logCall("Value received", call);
                assertEquals("/hub.value", call.to().toString());
                assertEquals(expected, call.args().get(0).toString());
                hub.dispatch(call.reply());
            }

            Call call = hub.poll();
            logCall("Expected execution failure", call);
            assertTrue(call.isError());

        }
    }

    @Test
    public void testEvalTrapErrors() throws Exception {
        logTest("testEvalTrapErrors");
        String script = """
                            eval --trap-errors {
                                set X 42
                                /hub.value "FOO"
                            }
                            """;
        var root = new DefaultScriptService();
        try (var hub = new RootHubImpl("script", root)) {
            hub.start();
            hub.send("/script.eval", "/hub.result", script);
            Call call = hub.poll();
            logCall("Value received", call);
            assertEquals("/hub.value", call.to().toString());
            assertEquals("FOO", call.args().get(0).toString());
            hub.dispatch(call.reply());
            call = hub.poll();
            logCall("Error result received", call);
            assertTrue(call.isError());
        }

    }

    @Test
    public void testEvalTrapErrorsNested() throws Exception {
        logTest("testEvalTrapErrorsNested");
        String script = """
                        set allowed [array "@"]
                        eval --trap-errors --allowed-commands $allowed {
                            @ /bar {
                                set V 42
                                @ ./baz test:component {
                                    .error
                                    .value "FOO"
                                }
                            }
                            set Y [set X]
                        }
                        """;
        var root = new DefaultScriptService();
        try (var hub = new RootHubImpl("script", root)) {
            hub.start();
            hub.send("/script.eval", "/hub.result", script);
            Call call = hub.poll();
            logCall("Call to /bar.add-child received", call);
            assertTrue(call.isRequest());
            assertEquals("/bar.add-child", call.to().toString());
            hub.dispatch(call.reply());

            call = hub.poll();
            logCall("Call to /bar/baz.error received", call);
            assertTrue(call.isRequest());
            assertEquals("/bar/baz.error", call.to().toString());
            hub.dispatch(call.error(PError.of("BAZ ERROR")));

            call = hub.poll();
            logCall("Call to /bar/baz.value received", call);
            assertTrue(call.isRequest());
            assertEquals("/bar/baz.value", call.to().toString());
            assertEquals(1, call.args().size());
            assertEquals("FOO", call.args().get(0).toString());
            hub.dispatch(call.reply());

            call = hub.poll();
            logCall("Result received", call);

            assertTrue(call.isError());
            String errors = call.args().get(0).toString();
            if (VERBOSE) {
                System.out.println("");
                System.out.println("Completed with errors");
                System.out.println("=====================");
                System.out.println(errors);
            }
            assertTrue(errors.contains("set"));
            assertTrue(errors.contains("BAZ ERROR"));
        }

    }

    @Test
    public void testTryCatch() throws Exception {
        logTest("testTryCatch");
        String script = """
                        set v1 "hello"
                        try {
                            set v1 "FAIL"
                            echo [array "" [fail]]
                        } catch {
                            set v1 "hello world"
                        }
                        set v2 [try {map key data} catch {map FAIL FAIL}]
                        set v3 [try {/bar.value} catch {echo FAIL}]
                        set v4 [try {/bar.fail} catch {map}]
                        array $v1 $v2 $v3 $v4
                        """;
        var root = new DefaultScriptService();
        try (var hub = new RootHubImpl("script", root)) {
            hub.start();
            hub.send("/script.eval", "/hub.result", script);

            Call call = hub.poll();
            logCall("Call to /bar.value received", call);
            assertEquals("/bar.value", call.to().toString());
            hub.dispatch(call.reply(PString.of("FOO")));

            call = hub.poll();
            logCall("Call to /bar.fail received", call);
            assertEquals("/bar.fail", call.to().toString());
            hub.dispatch(call.error(PError.of("FAIL")));

            call = hub.poll();
            logCall("Result received", call);
            assertTrue(call.isReply());
            assertEquals(1, call.args().size());
            PArray result = PArray.from(call.args().getFirst()).orElse(PArray.EMPTY);
            assertEquals(4, result.size());
            assertEquals("hello world", result.get(0).toString());
            assertEquals(PMap.of("key", "data"), result.get(1));
            assertEquals("FOO", result.get(2).toString());
            assertEquals(PMap.EMPTY, result.get(3));
        }
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

    private static class RootHubImpl implements RootHub, AutoCloseable {

        private final String rootID;
        private final Root root;
        private final BlockingQueue<Packet> queue;

        private Root.Controller ctrl;

        private RootHubImpl(String rootID, Root root) {
            this.rootID = rootID;
            this.root = root;
            this.queue = new LinkedBlockingQueue<>();
        }

        @Override
        public boolean dispatch(Packet packet) {
            if (rootID.equals(packet.rootID())) {
                return ctrl.submitPacket(packet);
            } else {
                return queue.offer(packet);
            }
        }

        @Override
        public Clock getClock() {
            return System::nanoTime;
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

        public void send(String to, String from, Object arg) {
            send(to, from, List.of(Value.ofObject(arg)));
        }

        public void send(String to, String from, List<Value> args) {
            dispatch(Call.create(ControlAddress.of(to),
                    ControlAddress.of(from),
                    getClock().getTime(),
                    args
            ));
        }

        public Call poll() throws InterruptedException, TimeoutException {
            Packet p = queue.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (p instanceof Call c) {
                return c;
            }
            throw new TimeoutException("Call poll timed out");
        }

        public void start() {
            ctrl = root.initialize(rootID, this);
            ctrl.start();
        }

        @Override
        public void close() {
            ctrl.shutdown();
            try {
                ctrl.awaitTermination(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

    }

}
