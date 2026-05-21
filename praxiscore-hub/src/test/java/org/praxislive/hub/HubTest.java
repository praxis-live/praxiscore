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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import org.praxislive.base.AbstractComponent;
import org.praxislive.base.AbstractRoot;
import org.praxislive.base.AbstractRootContainer;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.HubProxy;
import org.praxislive.core.Info;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.services.ComponentFactoryService;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;

/**
 *
 */
public class HubTest extends AbstractTestBase {

    @Test
    public void testHubEval() throws Exception {
        Hub hub = Hub.builder()
                .addExtension(new ComponentFactoryImpl())
                .build();
        hub.start();
        List<Value> result = hub.eval("""
                @ /root root:test {
                    @ ./cmp test:component {
                        .value "FOO"
                    }
                }
                /root.start
                array [/root.is-running] [/root/cmp.value]
                """).get(TIMEOUT, TimeUnit.MILLISECONDS);
        assertEquals(1, result.size());
        PArray array = PArray.from(result.getFirst()).orElseThrow();
        log(array);
        assertTrue(PBoolean.from(array.get(0)).orElseThrow().value());
        assertEquals("FOO", array.get(1).toString());
        hub.shutdown();
        hub.await(TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testHubProxy() throws Exception {
        Hub hub = Hub.builder()
                .addExtension(new ComponentFactoryImpl())
                .build();
        hub.start();
        hub.eval("""
                @ /root root:test {
                    @ ./cmp test:component {
                        .value "FOO"
                    }
                }
                /root.start
                """).get(TIMEOUT, TimeUnit.MILLISECONDS);
        HubProxy proxy = hub.createHubProxy();
        HubProxy.Root root = proxy.root("root");
        ComponentInfo info = root.validate()
                .get(TIMEOUT, TimeUnit.MILLISECONDS)
                .orElseThrow();
        log(info.print());
        assertTrue(root.isContainer());
        assertNull(root.parent());
        assertEquals(List.of(), root.children());
        root.sync();
        repeatUntil(() -> !root.children().isEmpty());
        assertEquals(List.of("cmp"), root.children());
        HubProxy.Component cmp = root.resolve("cmp");
        assertEquals(root, cmp.parent());
        assertEquals("cmp", cmp.id());
        repeatUntil(() -> cmp.isValid());
        cmp.sync(Set.of("value"));
        repeatUntil(() -> cmp.get("value").isPresent());
        assertEquals("FOO", cmp.get("value").orElseThrow().toString());
        cmp.set("value", PString.of("BAR"));
        repeatUntil(() -> Objects.equals("BAR",
                cmp.get("value").map(Value::toString).orElse("")));
        HubProxy.Component invalid = root.resolve("none");
        assertTrue(invalid.validate().get(TIMEOUT, TimeUnit.MILLISECONDS).isEmpty());
        HubProxy.Component cmp2 = proxy.component(ComponentAddress.of("/root/cmp"));
        assertEquals(cmp, cmp2);
        assertEquals(Set.of(root, cmp, invalid), proxy.proxies());
        proxy.proxies().stream().filter(c -> !c.isValid()).forEach(c -> c.dispose());
        assertEquals(Set.of(root, cmp), proxy.proxies());
        proxy.clear();
        assertEquals(Set.of(), proxy.proxies());
        HubProxy.Root root2 = proxy.root("root");
        assertNotEquals(root, root2);
        root2.sync();
        root2.stop();
        repeatUntil(() -> Objects.equals(PBoolean.FALSE,
                root2.get("is-running").orElse(PBoolean.TRUE)));
        proxy.dispose();
        hub.shutdown();
        hub.await(TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private static class ComponentFactoryImpl extends AbstractRoot implements RootHub.ServiceProvider {

        @Override
        public List<Class<? extends Service>> services() {
            return List.of(ComponentFactoryService.class, RootFactoryService.class);
        }

        @Override
        protected void processCall(Call call, PacketRouter router) {
            if (call.isRequest()) {
                switch (call.to().controlID()) {
                    case RootFactoryService.NEW_ROOT_INSTANCE -> {
                        router.route(call.reply(PReference.of(new RootImpl())));
                    }
                    case ComponentFactoryService.NEW_INSTANCE -> {
                        router.route(call.reply(PReference.of(new CmpImpl())));
                    }
                }
            }
        }

    }

    private static class RootImpl extends AbstractRootContainer {

        private final ComponentInfo info;

        private RootImpl() {
            this.info = Info.component()
                    .merge(ComponentProtocol.API_INFO)
                    .merge(ContainerProtocol.API_INFO)
                    .merge(StartableProtocol.API_INFO)
                    .build();
        }

        @Override
        public ComponentInfo getInfo() {
            return info;
        }

    }

    private static class CmpImpl extends AbstractComponent {

        private final ComponentInfo info;

        private String value = "";

        private CmpImpl() {
            this.info = Info.component(cmp -> cmp
                    .merge(ComponentProtocol.API_INFO)
                    .control("value", p -> p.property().input(PString.class))
            );
            registerControl("value", (call, router) -> {
                if (call.isRequest()) {
                    if (!call.args().isEmpty()) {
                        value = call.args().getFirst().toString();
                    }
                    router.route(call.reply(PString.of(value)));
                }
            });
        }

        @Override
        public ComponentInfo getInfo() {
            return info;
        }

    }

}
