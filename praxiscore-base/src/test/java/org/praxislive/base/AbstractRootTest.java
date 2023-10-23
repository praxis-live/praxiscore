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
package org.praxislive.base;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.praxislive.core.Call;
import org.praxislive.core.Clock;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Lookup;
import org.praxislive.core.Packet;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.types.PString;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class AbstractRootTest {

    public AbstractRootTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testProcessCall() {
        RootImpl root = new RootImpl();
        LinkedBlockingQueue<Packet> responseQueue = new LinkedBlockingQueue<>();
        RootHubImpl hub = new RootHubImpl(root, responseQueue);
        hub.ctrl.start();
        hub.ctrl.submitPacket(Call.create(ControlAddress.of("/test.hello"),
                ControlAddress.of("/hub.world"),
                hub.getClock().getTime() + TimeUnit.MILLISECONDS.toNanos(100)));
        try {
            Call reply = (Call) responseQueue.poll(2, TimeUnit.SECONDS);
            assertEquals("OK", reply.args().get(0).toString());
        } catch (Exception ex) {
            fail();
        }
        hub.ctrl.shutdown();
        try {
            assertTrue(root.latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            fail();
        }
    }

    @Test
    public void testDelegateProcessCall() {
        DelegatingRootImpl root = new DelegatingRootImpl();
        LinkedBlockingQueue<Packet> responseQueue = new LinkedBlockingQueue<>();
        RootHubImpl hub = new RootHubImpl(root, responseQueue);
        hub.ctrl.start();
        hub.ctrl.submitPacket(Call.create(ControlAddress.of("/test.hello"),
                ControlAddress.of("/hub.world"),
                hub.getClock().getTime() + TimeUnit.MILLISECONDS.toNanos(100)));
        try {
            Call reply = (Call) responseQueue.poll(2, TimeUnit.SECONDS);
            assertEquals("OK", reply.args().get(0).toString());
        } catch (Exception ex) {
            fail();
        }
        hub.ctrl.shutdown();
        try {
            assertTrue(root.latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            fail();
        }
    }

    @Test
    public void testCallResponseAfterShutdown() {
        RootImpl root = new RootImpl();
        LinkedBlockingQueue<Packet> responseQueue = new LinkedBlockingQueue<>();
        RootHubImpl hub = new RootHubImpl(root, responseQueue);
        hub.ctrl.start();
        hub.ctrl.submitPacket(Call.create(ControlAddress.of("/test.hello"),
                ControlAddress.of("/hub.world"),
                hub.getClock().getTime() + TimeUnit.SECONDS.toNanos(1)));
        hub.ctrl.shutdown();
        try {
            Call reply = (Call) responseQueue.poll(2, TimeUnit.SECONDS);
            assertTrue(reply.isError());
        } catch (Exception ex) {
            fail();
        }
        try {
            assertTrue(root.latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            fail();
        }
    }

    public class RootImpl extends AbstractRoot {

        CountDownLatch latch = new CountDownLatch(1);

        @Override
        protected void activating() {
            setRunning();
        }

        @Override
        public void processCall(Call call, PacketRouter router) {
            router.route(call.reply(PString.of("OK")));
        }

        @Override
        protected void terminating() {
            System.out.println("Terminate called");
            latch.countDown();
        }

    }

    public class DelegatingRootImpl extends AbstractRoot {

        CountDownLatch latch = new CountDownLatch(1);
        Delegate del;

        @Override
        protected void activating() {
            setRunning();
            del = new Delegate(getRootHub());
            attachDelegate(del);
            del.start();
        }

        @Override
        protected void terminating() {
            System.out.println("Terminate called");
            assertNotEquals(del.active, Thread.currentThread());
            try {
                del.active.join(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace(System.err);
            }
            assertTrue(!del.active.isAlive());
            latch.countDown();
        }

        @Override
        public void processCall(Call call, PacketRouter router) {
            assertEquals(del.active, Thread.currentThread());
            router.route(call.reply(PString.of("OK")));
        }

        class Delegate extends AbstractRoot.Delegate {

            Thread active;
            RootHub hub;

            Delegate(RootHub hub) {
                this.hub = hub;
            }

            void start() {
                active = getThreadFactory().newThread(() -> {
                    while (doUpdate(hub.getClock().getTime())) {
                        try {
                            doTimedPoll(1, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace(System.err);
                        }
                    }
                    detachDelegate(this);
                });
                active.start();
            }

        }
    }

    public class RootHubImpl implements RootHub {

        private final Root root;
        private final Root.Controller ctrl;
        private final Queue<Packet> queue;

        private RootHubImpl(Root root, Queue<Packet> queue) {
            this.root = root;
            this.queue = queue;
            this.ctrl = root.initialize("test", this);
        }

        @Override
        public boolean dispatch(Packet packet) {
            if ("test".equals(packet.rootID())) {
                return ctrl.submitPacket(packet);
            } else {
                System.out.println(packet);
                queue.add(packet);
            }
            return true;
        }

        @Override
        public Clock getClock() {
            return System::nanoTime;
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

    }

}
