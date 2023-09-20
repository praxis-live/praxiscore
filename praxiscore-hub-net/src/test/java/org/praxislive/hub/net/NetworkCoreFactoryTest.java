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
package org.praxislive.hub.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;
import org.praxislive.hub.Hub;

import static org.junit.jupiter.api.Assertions.*;
import static java.lang.System.Logger.Level;

/**
 *
 */
public class NetworkCoreFactoryTest {

    private static final System.Logger LOG = System.getLogger(NetworkCoreFactoryTest.class.getName());

    @Test
    @Timeout(10)
    public void testLocalParentChild() throws Exception {

        var childCoreFactory = NetworkCoreFactory.builder()
                .enableServer()
                .build();
        var childHub = Hub.builder()
                .setCoreRootFactory(childCoreFactory)
                .addExtension(new RootFactoryImpl())
                .build();
        childHub.start();
        int port = childCoreFactory.awaitInfo(10, TimeUnit.SECONDS)
                .serverAddress()
                .map(InetSocketAddress.class::cast)
                .map(InetSocketAddress::getPort)
                .orElseThrow();

        var runner = new TestRunner("""
                                    @ /root root:test
                                    /root.get-result
                                    """);
        var hubConfigMap = PMap.parse("""
                                      proxies {
                                        all {
                                          port %d
                                        }
                                      }
                                      """.formatted(port));
        var parentHub = Hub.builder()
                .setCoreRootFactory(NetworkCoreFactory.builder()
                        .hubConfiguration(HubConfiguration.fromMap(hubConfigMap))
                        .build()
                )
                .addExtension(runner)
                .build();
        parentHub.start();
        try {
            var result = runner.awaitResult();
            assertEquals("Hello World", result);
        } finally {
            parentHub.shutdown();
            parentHub.await();
            childHub.shutdown();
            childHub.await();
        }
    }

    @Test
    @Timeout(10)
    public void testLocalParentChildWithHubCommand() throws Exception {

        var childCoreFactory = NetworkCoreFactory.builder()
                .enableServer()
                .build();
        var childHub = Hub.builder()
                .setCoreRootFactory(childCoreFactory)
                .addExtension(new RootFactoryImpl())
                .build();
        childHub.start();
        int port = childCoreFactory.awaitInfo(10, TimeUnit.SECONDS)
                .serverAddress()
                .map(InetSocketAddress.class::cast)
                .map(InetSocketAddress::getPort)
                .orElseThrow();

        var runner = new TestRunner("""
                                    hub {
                                      proxies {
                                        test {
                                          id-pattern r*
                                          type-pattern te*
                                          port %d
                                        }
                                      }
                                    }
                                    @ /root root:test
                                    /root.get-result
                                    """.formatted(port));
        var parentHub = Hub.builder()
                .setCoreRootFactory(NetworkCoreFactory.builder().build())
                .addExtension(runner)
                .build();
        parentHub.start();
        try {
            var result = runner.awaitResult();
            assertEquals("Hello World", result);
        } finally {
            parentHub.shutdown();
            parentHub.await();
            childHub.shutdown();
            childHub.await();
        }
    }

    @Test
    @Timeout(10)
    public void testScriptServiceOnChild() throws Exception {
        var childCoreFactory = NetworkCoreFactory.builder()
                .enableServer()
                .build();
        var childScriptService = new ChildScriptService();
        var childHub = Hub.builder()
                .setCoreRootFactory(childCoreFactory)
                .addExtension(new RootFactoryImpl())
                .addExtension(childScriptService)
                .build();
        childHub.start();
        int port = childCoreFactory.awaitInfo(10, TimeUnit.SECONDS)
                .serverAddress()
                .map(InetSocketAddress.class::cast)
                .map(InetSocketAddress::getPort)
                .orElseThrow();

        var runner = new TestRunner("""
                                    @ /root root:test
                                    /root.get-result
                                    """);

        var proxyInfo = new ProxyInfo() {
            @Override
            public boolean matches(String rootID, ComponentType rootType) {
                return true;
            }

            @Override
            public SocketAddress socketAddress() {
                return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            }

            @Override
            public List<Class<? extends Service>> services() {
                return List.of(ScriptService.class);
            }

        };
        var parentHub = Hub.builder()
                .setCoreRootFactory(NetworkCoreFactory.builder()
                        .hubConfiguration(HubConfiguration.builder()
                                .proxy(proxyInfo)
                                .build())
                        .exposeServices(List.of(RootManagerService.class))
                        .build()
                )
                .addExtension(runner)
                .build();
        parentHub.start();
        try {
            var childCalled = childScriptService.await();
            assertTrue(childCalled);
            var result = runner.awaitResult();
            assertEquals("Hello World", result);
        } finally {
            parentHub.shutdown();
            parentHub.await();
            childHub.shutdown();
            childHub.await();
        }
    }

    private static class TestRunner extends AbstractRoot {

        private final String script;
        private final CompletableFuture<String> result;

        TestRunner(String script) {
            this.script = script;
            result = new CompletableFuture<>();
        }

        @Override
        protected void activating() {
            setRunning();
        }

        @Override
        protected void starting() {
            try {
                var to = ControlAddress.of(findService(ScriptService.class), ScriptService.EVAL);
                var call = Call.create(to,
                        ControlAddress.of(getAddress(), "result"),
                        getExecutionContext().getTime(),
                        PString.of(script));
                getRouter().route(call);
            } catch (ServiceUnavailableException ex) {
                LOG.log(Level.ERROR, "No script service", ex);
            }
        }

        @Override
        protected void processCall(Call call, PacketRouter router) {
            try {
                if (call.isReply()) {
                    result.complete(call.args().get(0).toString());
                } else if (call.isError()) {
                    var arg = call.args().get(0);
                    var err = PError.from(arg)
                            .orElse(PError.of(arg.toString()));
                    var ex = err.exception().orElseGet(() -> new Exception(err.toString()));
                    result.completeExceptionally(ex);
                } else {
                    throw new IllegalStateException("Invalid call received\n" + call);
                }
            } catch (Exception ex) {
                LOG.log(Level.ERROR, "Error in TestRunner call", ex);
                result.completeExceptionally(ex);
            }
        }

        String awaitResult() throws InterruptedException, ExecutionException {
            return result.get();
        }

    }

    private static class RootFactoryImpl extends AbstractRoot implements RootHub.ServiceProvider {

        @Override
        public List<Class<? extends Service>> services() {
            return List.of(RootFactoryService.class);
        }

        @Override
        protected void processCall(Call call, PacketRouter router) {
            if (call.isRequest() && RootFactoryService.NEW_ROOT_INSTANCE.equals(call.to().controlID())) {
                router.route(call.reply(PReference.of(new RootImpl())));
            } else {
                LOG.log(Level.ERROR, "Call not supported : \n" + call);
            }
        }

    }

    private static class RootImpl extends AbstractRoot {

        @Override
        protected void processCall(Call call, PacketRouter router) {
            if (call.isRequest()) {
                router.route(call.reply(PString.of("Hello World")));
            }
        }

    }

    private static class ChildScriptService extends AbstractRoot implements RootHub.ServiceProvider {

        private final CompletableFuture<Boolean> called = new CompletableFuture<>();
        private Call pending;

        @Override
        public List<Class<? extends Service>> services() {
            return List.of(ScriptService.class);
        }

        @Override
        protected void processCall(Call call, PacketRouter router) {
            if (call.isRequest()) {
                called.complete(true);
                if (pending != null) {
                    throw new IllegalStateException();
                }
                var to = getLookup().find(Services.class)
                        .map(srv -> srv.locateAll(ScriptService.class))
                        .flatMap(s -> s.filter(c -> !c.equals(getAddress())).findFirst())
                        .map(cmp -> ControlAddress.of(cmp, ScriptService.EVAL))
                        .orElseThrow();
                pending = call;
                router.route(Call.create(to,
                        ControlAddress.of(getAddress(), ScriptService.EVAL),
                        call.time(),
                        call.args()));
            } else if (call.isReply()) {
                router.route(pending.reply(call.args()));
            } else {
                router.route(pending.error(call.args()));
            }
        }

        boolean await() throws InterruptedException, ExecutionException {
            return called.get();

        }

    }

}
