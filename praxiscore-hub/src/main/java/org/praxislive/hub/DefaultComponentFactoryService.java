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
package org.praxislive.hub;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.base.AbstractAsyncControl;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Root;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.ComponentFactoryService;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;

/**
 *
 */
class DefaultComponentFactoryService extends AbstractRoot
        implements RootHub.ServiceProvider {

    private final ComponentRegistry registry;
    private final NewInstanceControl newInstance;
    private final NewRootInstanceControl newRoot;

    public DefaultComponentFactoryService() {
        registry = ComponentRegistry.getInstance();
        newInstance = new NewInstanceControl();
        newRoot = new NewRootInstanceControl();
    }

    @Override
    public List<Class<? extends Service>> services() {
        return Stream.of(ComponentFactoryService.class,
                RootFactoryService.class)
                .collect(Collectors.toList());
    }

    @Override
    protected void activating() {
        setRunning();
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        switch (call.to().controlID()) {
            case ComponentFactoryService.NEW_INSTANCE: {
                try {
                    newInstance.call(call, router);
                } catch (Exception ex) {
                    router.route(call.error(PError.of(ex)));
                }
            }
            break;
            case RootFactoryService.NEW_ROOT_INSTANCE: {
                try {
                    newRoot.call(call, router);
                } catch (Exception ex) {
                    router.route(call.error(PError.of(ex)));
                }
            }
            break;
            default:
                if (call.isRequest()) {
                    router.route(call.error(PError.of("Unknown control ID")));
                }

        }

    }

    private class NewInstanceControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            ComponentType type = ComponentType.from(call.args().get(0)).orElseThrow();
            ComponentFactory factory = registry.getComponentFactory(type);
            ComponentFactory.Redirect redirect = factory.componentRedirect().orElse(null);
            if (redirect != null) {
                ControlAddress altFactory = getLookup().find(Services.class)
                        .flatMap(srvs -> srvs.locate(redirect.service()))
                        .map(cmp -> ControlAddress.of(cmp, redirect.control()))
                        .orElseThrow(() -> new IllegalStateException("Alternative factory service not found"));

                return Call.create(altFactory,
                        call.to(),
                        call.time(),
                        call.args());
            } else {
                Component component = factory.createComponent(type);
                return call.reply(PReference.of(component));
            }
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            return getActiveCall().reply(call.args());
        }

    }

    private class NewRootInstanceControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            ComponentType type = ComponentType.from(call.args().get(0)).orElseThrow();
            ComponentFactory factory = registry.getRootComponentFactory(type);
            ComponentFactory.Redirect redirect = factory.rootRedirect().orElse(null);
            if (redirect != null) {
                ControlAddress altFactory = getLookup().find(Services.class)
                        .flatMap(srvs -> srvs.locate(redirect.service()))
                        .map(cmp -> ControlAddress.of(cmp, redirect.control()))
                        .orElseThrow(() -> new IllegalStateException("Alternative factory service not found"));

                return Call.create(altFactory,
                        call.to(),
                        call.time(),
                        call.args());
            } else {
                Root root = factory.createRoot(type);
                return call.reply(PReference.of(root));
            }
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            return getActiveCall().reply(call.args());
        }

    }

}
