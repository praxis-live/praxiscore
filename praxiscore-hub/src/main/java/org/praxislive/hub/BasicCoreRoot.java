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
package org.praxislive.hub;

import java.lang.System.Logger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.praxislive.base.AbstractAsyncControl;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.Control;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.SystemManagerService;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;

import static java.lang.System.Logger.Level;

/**
 * A base implementation of a core root for use with {@link Hub}. This is the
 * default implementation used unless a custom {@link Hub.CoreRootFactory} is
 * provided. It is recommended to extend this class to provide custom core root
 * behaviour.
 */
public class BasicCoreRoot extends AbstractRoot {

    private final static Logger LOG = System.getLogger(BasicCoreRoot.class.getName());

    private final Hub.Accessor hubAccess;
    private final List<Root> exts;
    private final Map<String, Control> controls;

    private Controller controller;
    private int exitValue;

    /**
     * Create an instance.
     *
     * @param hubAccess private roothub access
     * @param exts extensions
     */
    protected BasicCoreRoot(Hub.Accessor hubAccess, List<Root> exts) {
        this.hubAccess = Objects.requireNonNull(hubAccess);
        this.exts = Objects.requireNonNull(exts);
        this.controls = new HashMap<>();
        this.exitValue = 0;
    }

    @Override
    public final Controller initialize(String id, RootHub hub) {
        Controller ctrl = super.initialize(id, hub);
        this.controller = ctrl;
        return ctrl;
    }

    /**
     * Activation hook implementation of the BasicCoreRoot. During activation,
     * controls and services on the core root are registered, extensions are
     * installed, the root is set to running, and extensions are started.
     * <p>
     * The {@link AbstractRoot#starting()} hook will be called after extensions
     * are installed, but before they are running.
     * <p>
     * This method is currently final. Customizing controls and services can be
     * achieved using {@link #buildControlMap(java.util.Map)} and
     * {@link #buildServiceMap(java.util.Map)}. Further customization should be
     * done in the starting hook.
     */
    @Override
    protected final void activating() {
        Map<Class<? extends Service>, ComponentAddress> services = new HashMap<>();
        buildServiceMap(services);
        services.forEach(hubAccess::registerService);
        Map<String, Control> ctrls = new HashMap<>();
        buildControlMap(ctrls);
        controls.putAll(ctrls);
        var extCtrls = installExtensions();
        setRunning(); // calls starting()
        extCtrls.forEach(this::startRoot);
    }

    /**
     * Termination hook implementation. This implementation shuts down all
     * registered roots. If overriding this method, ensure to call the super
     * implementation, or otherwise terminate all roots.
     */
    @Override
    protected void terminating() {
        String[] ids = hubAccess.getRootIDs();
        for (String id : ids) {
            uninstallRoot(id);
        }
    }

    /**
     * Force termination of this root.
     */
    protected final void forceTermination() {
        controller.shutdown();
        interrupt();
    }

    /**
     * The exit value to be returned from {@link Hub#exitValue()}. By default
     * this returns the value passed to the {@link SystemManagerService}
     * implementation, if any, otherwise 0.
     *
     * @return exit value
     */
    protected int exitValue() {
        return exitValue;
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        Control control = controls.get(call.to().controlID());
        try {
            if (control != null) {
                control.call(call, router);
            } else {
                if (call.isRequest()) {
                    router.route(call.error(PError.of("Unknown control address : " + call.to())));
                }
            }
        } catch (Exception ex) {
            if (call.isRequest()) {
                router.route(call.error(PError.of(ex)));
            }
        }
    }

    /**
     * Build the map of services implemented by this root. Usually the root
     * address from {@link #getAddress()} will be used, although subclasses may
     * use subpaths of that address if required. Subclasses may override this
     * method and choose whether or not to call the super implementation. This
     * implementation adds {@link RootManagerService} and
     * {@link SystemManagerService} at the root address if they are absent from
     * the map.
     *
     * @param srvs map of service addresses to register
     */
    protected void buildServiceMap(Map<Class<? extends Service>, ComponentAddress> srvs) {
        srvs.putIfAbsent(RootManagerService.class, getAddress());
        srvs.putIfAbsent(SystemManagerService.class, getAddress());
    }

    /**
     * Build the controls on this root. Subclasses may override this method and
     * choose whether or not to call the super implementation. This
     * implementation adds all {@link RootManagerService} and
     * {@link SystemManagerService} controls if they are absent from the map.
     *
     * @param ctrls map of control id to control
     */
    protected void buildControlMap(Map<String, Control> ctrls) {
        ctrls.computeIfAbsent(RootManagerService.ADD_ROOT, k -> new AddRootControl());
        ctrls.computeIfAbsent(RootManagerService.REMOVE_ROOT, k -> new RemoveRootControl());
        ctrls.computeIfAbsent(RootManagerService.ROOTS, k -> new RootsControl());
        ctrls.computeIfAbsent(SystemManagerService.SYSTEM_EXIT, k -> (call, router) -> {
            if (call.isRequest()) {
                if (!call.args().isEmpty()) {
                    exitValue = PNumber.from(call.args().get(0))
                            .orElse(PNumber.ZERO)
                            .toIntValue();
                }
                forceTermination();
                if (call.isReplyRequired()) {
                    router.route(call.reply());
                }
            }
        });
    }

    /**
     * Install a root in the hub with the provided ID. This method will
     * initialize the root and register it in the roothub, returning the root
     * controller. It does not start the root - see
     * {@link #startRoot(java.lang.String, org.praxislive.core.Root.Controller)}.
     *
     * @param id root ID
     * @param root root to initialize and install
     * @return root controller
     * @throws Exception if id is invalid or in use, or if root cannot be
     * initialized
     */
    protected final Root.Controller installRoot(String id, Root root)
            throws Exception {
        if (!ComponentAddress.isValidID(id) || hubAccess.getRootController(id) != null) {
            throw new IllegalArgumentException();
        }
        Root.Controller ctrl = root.initialize(id, hubAccess.getRootHub());
        if (hubAccess.registerRootController(id, ctrl)) {
            return ctrl;
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Uninstall the root with the provided ID and trigger it to shutdown.
     *
     * @param id root ID
     * @return root controller (or null if no registered root with id)
     */
    protected final Root.Controller uninstallRoot(String id) {
        Root.Controller ctrl = hubAccess.unregisterRootController(id);
        if (ctrl != null) {
            ctrl.shutdown();
            return ctrl;
        } else {
            return null;
        }
    }

    /**
     * Start the root with the provided ID and controller.
     *
     * @param id root ID
     * @param ctrl root controller
     */
    protected final void startRoot(final String id, final Root.Controller ctrl) {
        ctrl.start();
    }

    /**
     * Acquire the hub accessor.
     *
     * @return hub accessor
     */
    protected final Hub.Accessor getHubAccessor() {
        return hubAccess;
    }

    private Map<String, Root.Controller> installExtensions() {
        if (exts.isEmpty()) {
            return Map.of();
        }
        Map<String, Root.Controller> ctrls = new LinkedHashMap<>();
        for (var ext : exts) {
            var services = extractServices(ext);
            String extID = Hub.EXT_PREFIX + Integer.toHexString(ext.hashCode());
            try {
                LOG.log(Level.DEBUG, "Installing extension {0}", extID);
                var ctrl = installRoot(extID, ext);
                ctrls.put(extID, ctrl);
            } catch (Exception ex) {
                LOG.log(Level.ERROR, "Failed to install extension\n{0} to /{1}\n{2}",
                        new Object[]{ext.getClass(), extID, ex});
                continue;
            }
            ComponentAddress ad = ComponentAddress.of("/" + extID);
            for (var service : services) {
                LOG.log(Level.DEBUG, "Registering service {0}", service);
                hubAccess.registerService(service, ad);
            }
        }
        return ctrls;
    }

    private List<Class<? extends Service>> extractServices(Root root) {
        if (root instanceof RootHub.ServiceProvider serviceProvider) {
            return serviceProvider.services();
        } else {
            return List.of();
        }
    }

    public static Hub.CoreRootFactory factory() {
        return new Factory();
    }

    private class AddRootControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            List<Value> args = call.args();
            if (args.size() < 2) {
                throw new IllegalArgumentException("Invalid arguments");
            }
            if (!ComponentAddress.isValidID(args.get(0).toString())) {
                throw new IllegalArgumentException("Invalid Component ID");
            }
            ControlAddress to = ControlAddress.of(findService(RootFactoryService.class),
                    RootFactoryService.NEW_ROOT_INSTANCE);
            return Call.create(to, call.to(), call.time(), args.get(1));
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            List<Value> args = call.args();
            if (args.size() < 1) {
                throw new IllegalArgumentException("Invalid response");
            }
            Root r = PReference.from(args.get(0))
                    .flatMap(ref -> ref.as(Root.class))
                    .orElseThrow();
            Call active = getActiveCall();
//            addChild(active.getArgs().get(0).toString(), c);
            String id = active.args().get(0).toString();
            startRoot(id, installRoot(id, r));
            return active.reply();
        }

    }

    private class RemoveRootControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                String id = call.args().get(0).toString();
                uninstallRoot(id);
                router.route(call.reply());
            } else {
                throw new IllegalArgumentException();
            }
        }

    }

    private class RootsControl implements Control {

        private String[] knownIDs;
        private PArray ret;

        private RootsControl() {
            knownIDs = new String[0];
            ret = PArray.EMPTY;
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                String[] ids = hubAccess.getRootIDs();
                if (!Arrays.equals(ids, knownIDs)) {
                    knownIDs = ids;
                    ret = Stream.of(ids).map(PString::of).collect(PArray.collector());
                }
                router.route(call.reply(ret));
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private static class Factory implements Hub.CoreRootFactory {

        @Override
        public Root createCoreRoot(Hub.Accessor accessor, List<Root> extensions) {
            return new BasicCoreRoot(accessor, extensions);
        }

    }

}
