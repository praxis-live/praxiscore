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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.praxislive.hub.net.internal.HubConfigurationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.praxislive.base.AbstractAsyncControl;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Root;
import org.praxislive.core.Value;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PReference;
import org.praxislive.hub.BasicCoreRoot;
import org.praxislive.hub.Hub;

/**
 *
 */
class NetworkCoreRoot extends BasicCoreRoot {

    private final static String PROXY_PREFIX = Hub.SYS_PREFIX + "proxy_";

    private final Hub.Accessor hubAccess;
    private final List<ProxyData> proxies;
    private final List<Class<? extends Service>> services;
    private final ChildLauncher childLauncher;
    private final Map<String, String> remotes;

    private EventLoopGroup clientEventLoopGroup;
    private HubConfiguration configuration;
    private FileServer fileServer;

    NetworkCoreRoot(Hub.Accessor hubAccess,
            List<Root> exts,
            List<Class<? extends Service>> services,
            ChildLauncher childLauncher,
            HubConfiguration configuration) {
        super(hubAccess, exts);
        this.hubAccess = hubAccess;
        this.services = services;
        this.childLauncher = childLauncher;
        this.configuration = configuration;
        this.proxies = new ArrayList<>();
        this.remotes = new HashMap<>();
    }

    @Override
    protected void buildControlMap(Map<String, Control> ctrls) {
        ctrls.put(RootManagerService.ADD_ROOT, new AddRootControl());
        ctrls.put(RootManagerService.REMOVE_ROOT, new RemoveRootControl());
        ctrls.put(HubConfigurationService.HUB_CONFIGURE, new HubConfigurationControl());
        super.buildControlMap(ctrls);
    }

    @Override
    protected void registerServices() {
        super.registerServices();
        hubAccess.registerService(HubConfigurationService.class, getAddress());
    }

    @Override
    protected void starting() {
        if (configuration != null) {
            configure();
        }
    }

    @Override
    protected void terminating() {
        super.terminating();
        if (fileServer != null) {
            fileServer.stop();
            fileServer = null;
        }
    }

    private void ensureConfigured() {
        if (configuration == null) {
            configuration = HubConfiguration.fromMap(PMap.EMPTY);
            configure();
        }
    }

    private void configure() {

        var proxyInfo = configuration.proxies();

        if (proxyInfo.isEmpty()) {
            return;
        }

        clientEventLoopGroup = new NioEventLoopGroup();

        var requireServer = configuration.isFileServerEnabled()
                && proxyInfo.stream().anyMatch(p -> !p.isLocal());
        var serverInfo = requireServer ? activateFileServer() : null;

        for (int i = 0; i < proxyInfo.size(); i++) {
            var id = PROXY_PREFIX + (i + 1);
            var info = proxyInfo.get(i);
            try {
                installRoot(id, "netex", new ProxyClientRoot(info, clientEventLoopGroup,
                        services, childLauncher, serverInfo));
                proxies.add(new ProxyData(info, ComponentAddress.of("/" + id)));
            } catch (Exception ex) {
                System.getLogger(NetworkCoreRoot.class.getName())
                        .log(System.Logger.Level.ERROR, "", ex);
            }
        }

    }

    private FileServer.Info activateFileServer() {
        try {
            fileServer = new FileServer(Utils.getUserDirectory().toPath(), Utils.getFileServerPort());
            return fileServer.start();
        } catch (IOException ex) {
            System.getLogger(NetworkCoreRoot.class.getName())
                    .log(System.Logger.Level.ERROR, "", ex);
            fileServer = null;
        }
        return null;
    }

    private static class ProxyData {

        private final ProxyInfo info;
        private final ComponentAddress address;

        public ProxyData(ProxyInfo info, ComponentAddress address) {
            this.info = info;
            this.address = address;
        }

    }

    private class AddRootControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            ensureConfigured();
            List<Value> args = call.args();
            if (args.size() < 2) {
                throw new IllegalArgumentException("Invalid arguments");
            }
            String id = args.get(0).toString();
            if (!ComponentAddress.isValidID(id)) {
                throw new IllegalArgumentException("Invalid Component ID");
            }
            ComponentType type = ComponentType.from(args.get(1))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Component type"));

            ComponentAddress proxy = proxies.stream()
                    .filter(p -> p.info.matches(id, type))
                    .map(p -> p.address)
                    .findFirst().orElse(null);

            ControlAddress to;
            if (proxy != null) {
                to = ControlAddress.of(proxy, RootManagerService.ADD_ROOT);
                return Call.create(to, call.to(), call.time(), args);
            } else {
                to = ControlAddress.of(findService(RootFactoryService.class),
                        RootFactoryService.NEW_ROOT_INSTANCE);
                return Call.create(to, call.to(), call.time(), args.get(1));
            }
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            Call active = getActiveCall();
            String id = active.args().get(0).toString();
            String source = call.from().component().rootID();
            if (source.startsWith(PROXY_PREFIX)) {
                Root.Controller ctrl = getHubAccessor().getRootController(source);
                getHubAccessor().registerRootController(id, ctrl);
                remotes.put(id, source);
            } else {
                List<Value> args = call.args();
                if (args.size() < 1) {
                    throw new IllegalArgumentException("Invalid response");
                }
                Root r = PReference.from(args.get(0))
                        .flatMap(ref -> ref.as(Root.class))
                        .orElseThrow();
                String type = active.args().get(1).toString();
                installRoot(id, type, r);
            }
            return active.reply();
        }

    }

    private class RemoveRootControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            String id = call.args().get(0).toString();
            String remoteProxy = remotes.get(id);
            if (remoteProxy != null) {
                ControlAddress to = ControlAddress.of(
                        ComponentAddress.of("/" + remoteProxy),
                        RootManagerService.REMOVE_ROOT);
                return Call.create(to, call.to(), call.time(), call.args());
            } else {
                uninstallRoot(id);
                return call.reply();
            }
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            Call active = getActiveCall();
            String id = active.args().get(0).toString();
            getHubAccessor().unregisterRootController(id);
            remotes.remove(id);
            return call.reply();
        }

    }

    private class HubConfigurationControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                if (configuration != null) {
                    throw new IllegalStateException("Hub Configuration already fixed");
                }
                configuration = HubConfiguration.fromMap(
                        PMap.from(call.args().get(0)).orElseThrow());
                configure();
                if (call.isReplyRequired()) {
                    router.route(call.reply());
                }
            }

        }

    }

}
