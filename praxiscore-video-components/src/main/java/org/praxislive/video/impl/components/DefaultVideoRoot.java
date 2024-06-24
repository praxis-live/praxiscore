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
package org.praxislive.video.impl.components;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.AbstractRootContainer;
import org.praxislive.code.SharedCodeProperty;
import org.praxislive.core.Call;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.video.ClientConfiguration;
import org.praxislive.video.ClientRegistrationException;
import org.praxislive.video.Player;
import org.praxislive.video.PlayerConfiguration;
import org.praxislive.video.PlayerFactory;
import org.praxislive.video.QueueContext;
import org.praxislive.video.RenderingHints;
import org.praxislive.video.VideoContext;
import org.praxislive.video.pipes.FrameRateListener;
import org.praxislive.video.pipes.FrameRateSource;

/**
 *
 */
public class DefaultVideoRoot extends AbstractRootContainer {

    private final static String SOFTWARE = "Software";
    private final static List<String> RENDERERS = new ArrayList<>();

    static {
        RENDERERS.add(SOFTWARE);
        Lookup.SYSTEM.findAll(PlayerFactory.Provider.class)
                .forEach(r -> RENDERERS.add(r.getLibraryName()));
    }

    private final static System.Logger LOG = System.getLogger(DefaultVideoRoot.class.getName());

    private final static int WIDTH_DEFAULT = 640;
    private final static int HEIGHT_DEFAULT = 480;
    private final static double FPS_DEFAULT = 30;

    private final ComponentInfo info;
    private final VideoContextImpl ctxt;
    private final SharedCodeProperty sharedCode;

    private int width = WIDTH_DEFAULT;
    private int height = HEIGHT_DEFAULT;
    private double fps = FPS_DEFAULT;
    private String renderer = SOFTWARE;
    private boolean smooth = true;
    private Player player;
    private VideoContext.OutputClient outputClient;
    private Lookup lookup;

    public DefaultVideoRoot() {

        sharedCode = new SharedCodeProperty(this, this::handleLog);
        registerControl("shared-code", sharedCode);

        registerControl("renderer", new RendererProperty());
        registerControl("width", new WidthProperty());
        registerControl("height", new HeightProperty());
        registerControl("fps", new FpsProperty());
        registerControl("smooth", new SmoothProperty());

        info = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .control(ContainerProtocol.SUPPORTED_TYPES, ContainerProtocol.SUPPORTED_TYPES_INFO)
                .merge(StartableProtocol.API_INFO)
                .control("shared-code", SharedCodeProperty.INFO)
                .control("renderer", c -> c.property()
                    .defaultValue(PString.of(SOFTWARE))
                    .input(a -> a.string().allowed(RENDERERS.toArray(String[]::new))))
                .control("width", c -> c.property()
                    .defaultValue(PNumber.of(WIDTH_DEFAULT))
                    .input(a -> a
                        .number().min(1).max(16384)
                ))
                .control("height", c -> c.property()
                    .defaultValue(PNumber.of(HEIGHT_DEFAULT))
                    .input(a -> a
                        .number().min(1).max(16384)
                ))
                .control("fps", c -> c.property()
                    .defaultValue(PNumber.of(FPS_DEFAULT))
                    .input(a -> a
                        .number().min(1).max(256)
                ))
                .control("smooth", c -> c.property()
                    .defaultValue(PBoolean.TRUE)
                    .input(PBoolean.class)
                )
                .property(ComponentInfo.KEY_COMPONENT_TYPE, ComponentType.of("root:video"))
        );

        ctxt = new VideoContextImpl();
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), ctxt, sharedCode.getSharedCodeContext());
        }
        return lookup;
    }

    @Override
    protected void starting() {
        try {
            String lib = renderer;
            var delegate = new VideoDelegate();
            player = createPlayer(lib, delegate);
            lookup = Lookup.of(getLookup(),
                    player.getLookup().findAll(Object.class).toArray());
            if (outputClient != null && outputClient.getOutputCount() > 0) {
                player.getSink(0).addSource(outputClient.getOutputSource(0));
            }
            attachDelegate(delegate);
            delegate.start();
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.ERROR,
                    "Couldn't start video renderer", ex);
            setIdle();
        }
    }

    private Player createPlayer(String library, VideoDelegate delegate) throws Exception {
        Lookup clientLookup = Lookup.EMPTY;
        if (outputClient != null) {
            clientLookup = outputClient.getLookup();
        }
        PlayerFactory factory = findPlayerFactory(library);
        RenderingHints renderHints = new RenderingHints();
        renderHints.setSmooth(smooth);
        Lookup plLkp = Lookup.of(getLookup(), renderHints, delegate);
        Player pl = factory.createPlayer(new PlayerConfiguration(getRootHub().getClock(), width, height, fps, plLkp),
                new ClientConfiguration[]{
                    new ClientConfiguration(0, 1, clientLookup)
                });
        pl.addFrameRateListener(delegate);
        return pl;
    }

    private PlayerFactory findPlayerFactory(String lib) throws Exception {
        if (lib == null || lib.isEmpty() || "Software".equals(lib)) {
            return SWPlayer.getFactory();
        }
        return Lookup.SYSTEM.findAll(PlayerFactory.Provider.class)
                .filter(p -> lib.equals(p.getLibraryName()))
                .map(PlayerFactory.Provider::getFactory)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No valid renderer found"));
    }

    @Override
    protected void stopping() {
        lookup = null;
        player.terminate();
        player = null;
        interrupt();
    }

    @Override
    public ComponentInfo getInfo() {
        return info;
    }

    @Override
    public void write(TreeWriter writer) {
        super.write(writer);
        PMap sharedCodeValue = sharedCode.getValue();
        if (!sharedCodeValue.isEmpty()) {
            writer.writeProperty("shared-code", sharedCodeValue);
        } 
        if (!SOFTWARE.equals(renderer)) {
            writer.writeProperty("renderer", PString.of(renderer));
        }
        if (width != WIDTH_DEFAULT) {
            writer.writeProperty("width", PNumber.of(width));
        }
        if (height != HEIGHT_DEFAULT) {
            writer.writeProperty("height", PNumber.of(height));
        }
        if (fps != FPS_DEFAULT) {
            writer.writeProperty("fps", PNumber.of(fps));
        }
        if (!smooth) {
            writer.writeProperty("smooth", PBoolean.FALSE);
        }
    }

    private void handleLog(LogBuilder log) {
        if (log.isEmpty()) {
            return;
        }
        getLookup().find(Services.class)
                .flatMap(srv -> srv.locate(LogService.class))
                .ifPresent(logger -> {
                    var to = ControlAddress.of(logger, LogService.LOG);
                    var from = ControlAddress.of(getAddress(), "_log");
                    var call = Call.createQuiet(to,
                            from,
                            getExecutionContext().getTime(),
                            log.toList());
                    getRouter().route(call);
                });
    }

    private class VideoDelegate extends Delegate
            implements FrameRateListener, QueueContext {

        @Override
        public void nextFrame(FrameRateSource source) {
            boolean ok = doUpdate(source.getTime());
            if (!ok) {
                player.terminate();
            }
        }

        @Override
        public void process(long time, TimeUnit unit) throws InterruptedException {
            doTimedPoll(time, unit);
        }

        private void start() {
            var runner = getThreadFactory().newThread(() -> {
                player.run();
                setIdle();
                detachDelegate(this);
            });
            runner.start();
        }

    }

    private class VideoContextImpl extends VideoContext {

        @Override
        public int registerVideoInputClient(InputClient client) throws ClientRegistrationException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void unregisterVideoInputClient(InputClient client) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int registerVideoOutputClient(OutputClient client) throws ClientRegistrationException {
            if (outputClient == null) {
                outputClient = client;
                return 1;
            } else {
                throw new ClientRegistrationException();
            }
        }

        // @TODO should not allow while running!
        @Override
        public void unregisterVideoOutputClient(OutputClient client) {
            if (outputClient == client) {
                outputClient = null;
            }
        }
    }

    private class WidthProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set width while running");
            }
            int w = PNumber.from(arg).orElseThrow().toIntValue();
            if (w < 1 || w > 16384) {
                throw new IllegalArgumentException();
            }
            width = w;
        }

        @Override
        protected Value get() {
            return PNumber.of(width);
        }
    }

    private class HeightProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set height while running");
            }
            int h = PNumber.from(arg).orElseThrow().toIntValue();
            if (h < 1 || h > 16384) {
                throw new IllegalArgumentException();
            }
            height = h;
        }

        @Override
        protected Value get() {
            return PNumber.of(height);
        }
    }

    private class FpsProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set fps while running");
            }
            double f = PNumber.from(arg).orElseThrow().value();
            if (f < 1 || f > 256) {
                throw new IllegalArgumentException();
            }
            fps = f;
        }

        @Override
        protected Value get() {
            return PNumber.of(fps);
        }

    }

    private class RendererProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set renderer while running");
            }
            String r = arg.toString();
            if (!RENDERERS.contains(r)) {
                throw new IllegalArgumentException();
            }
            renderer = r;
        }

        @Override
        protected Value get() {
            return PString.of(renderer);
        }

    }

    private class SmoothProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new UnsupportedOperationException("Can't set smooth while running");
            }
            smooth = PBoolean.from(arg).orElseThrow().value();
        }

        @Override
        protected Value get() {
            return PBoolean.of(smooth);
        }
    }

}
