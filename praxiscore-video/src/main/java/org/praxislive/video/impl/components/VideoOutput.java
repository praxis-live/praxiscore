/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2019 Neil C Smith.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.praxislive.base.AbstractComponent;
import org.praxislive.base.AbstractProperty;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Value;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Lookup;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.praxislive.video.ClientConfiguration;
import org.praxislive.video.ClientRegistrationException;
import org.praxislive.video.DefaultVideoInputPort;
import org.praxislive.video.VideoContext;
import org.praxislive.video.VideoPort;
import org.praxislive.video.WindowHints;
import org.praxislive.video.pipes.VideoPipe;
import org.praxislive.video.pipes.impl.Placeholder;

/**
 *
 */
public class VideoOutput extends AbstractComponent {

    private final static String DEF_TITLE_PREFIX = "PRAXIS : ";

    private final Placeholder placeholder;
    private final VideoContext.OutputClient client;
    private final IntegerProperty width;
    private final IntegerProperty height;
    private final IntegerProperty rotation;
    private final IntegerProperty device;
    private final WindowHints wHints;
    private final ComponentInfo info;

    private VideoContext context;
    private String title = "";
    private String defaultTitle = "";

    public VideoOutput() {
        placeholder = new Placeholder();
        registerPort("in", new DefaultVideoInputPort(placeholder));
        client = new OutputClientImpl();
        wHints = new WindowHints();

        registerControl("title", new TitleProperty());

        info = Info.component(cmp -> cmp
                .control("title", c -> c.property().input(PString.class)
                    .defaultValue(PString.EMPTY))
                .control("device", c -> c.property().input(a -> a
                        .type(Value.class)
                        .property(ArgumentInfo.KEY_EMPTY_IS_DEFAULT, PBoolean.TRUE)
                        .property(ArgumentInfo.KEY_SUGGESTED_VALUES,
                                IntStream.of(0, 1, 2, 3).mapToObj(PNumber::of).collect(PArray.collector())
                        ))
                    .defaultValue(PString.EMPTY))
                .control("width", c -> c.property().input(a -> a
                        .type(Value.class)
                        .property(ArgumentInfo.KEY_EMPTY_IS_DEFAULT, PBoolean.TRUE))
                    .defaultValue(PString.EMPTY)
                )
                .control("height", c -> c.property().input(a -> a
                        .type(Value.class)
                        .property(ArgumentInfo.KEY_EMPTY_IS_DEFAULT, PBoolean.TRUE))
                    .defaultValue(PString.EMPTY)
                )
                .control("rotation", c -> c.property().input(a -> a
                        .type(Value.class)
                        .property(ArgumentInfo.KEY_EMPTY_IS_DEFAULT, PBoolean.TRUE)
                        .property(ArgumentInfo.KEY_SUGGESTED_VALUES,
                                IntStream.of(0, 90, 180, 270).mapToObj(PNumber::of).collect(PArray.collector())
                        ))
                    .defaultValue(PString.EMPTY))
                .control("full-screen", c -> c.property().input(PBoolean.class).defaultValue(PBoolean.FALSE))
                .control("always-on-top", c -> c.property().input(PBoolean.class).defaultValue(PBoolean.FALSE))
                .control("undecorated", c -> c.property().input(PBoolean.class).defaultValue(PBoolean.FALSE))
                .control("show-cursor", c -> c.property().input(PBoolean.class).defaultValue(PBoolean.FALSE))
                
                .port("in", p -> p.input(VideoPort.class))
        );

        device = new IntegerProperty();
        width = new IntegerProperty();
        height = new IntegerProperty();
        rotation = new IntegerProperty();

        registerControl("device", device);
        registerControl("width", width);
        registerControl("height", height);
        registerControl("rotation", rotation);

        registerControl("full-screen", new FullScreenProperty());
        registerControl("always-on-top", new AlwaysOnTopProperty());
        registerControl("undecorated", new UndecoratedProperty());
        registerControl("show-cursor", new ShowCursorProperty());

    }

    @Override
    public void hierarchyChanged() {
        super.hierarchyChanged();
        VideoContext ctxt = getLookup().find(VideoContext.class).orElse(null);
        if (ctxt != context) {
            if (context != null) {
                context.unregisterVideoOutputClient(client);
                context = null;
            }
            if (ctxt == null) {
                return;
            }
            try {
                ctxt.registerVideoOutputClient(client);
                context = ctxt;
            } catch (ClientRegistrationException ex) {
                Logger.getLogger(VideoOutput.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        ComponentAddress ad = getAddress();
        if (ad != null) {
            defaultTitle = DEF_TITLE_PREFIX + "/" + ad.rootID();
        } else {
            defaultTitle = DEF_TITLE_PREFIX;
        }
        if (title.isEmpty()) {
            wHints.setTitle(defaultTitle);
        }
    }

    @Override
    public ComponentInfo getInfo() {
        return info;
    }

    private class IntegerProperty extends AbstractProperty {

        private Value value;

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (arg.isEmpty()) {
                value = PString.EMPTY;
            } else {
                value = PNumber.from(arg).orElseThrow();
            }
        }

        @Override
        protected Value get() {
            return value;
        }

    }

    private class TitleProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (arg.isEmpty()) {
                title = "";
                wHints.setTitle(defaultTitle);
            } else {
                title = arg.toString();
                wHints.setTitle(title);
            }
        }

        @Override
        protected Value get() {
            return PString.of(title);
        }

    }

    private class FullScreenProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            wHints.setFullScreen(PBoolean.from(arg).orElse(PBoolean.FALSE).value());
        }

        @Override
        protected Value get() {
            return PBoolean.of(wHints.isFullScreen());
        }

    }

    private class AlwaysOnTopProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            wHints.setAlwaysOnTop(PBoolean.from(arg).orElse(PBoolean.FALSE).value());
        }

        @Override
        protected Value get() {
            return PBoolean.of(wHints.isAlwaysOnTop());
        }

    }

    private class UndecoratedProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            wHints.setUndecorated(PBoolean.from(arg).orElse(PBoolean.FALSE).value());
        }

        @Override
        protected Value get() {
            return PBoolean.of(wHints.isUndecorated());
        }

    }

    private class ShowCursorProperty extends AbstractProperty {

        @Override
        protected void set(long time, Value arg) throws Exception {
            wHints.setShowCursor(PBoolean.from(arg).orElse(PBoolean.FALSE).value());
        }

        @Override
        protected Value get() {
            return PBoolean.of(wHints.isShowCursor());
        }

    }

    private class OutputClientImpl extends VideoContext.OutputClient {

        @Override
        public int getOutputCount() {
            return 1;
        }

        @Override
        public VideoPipe getOutputSource(int index) {
            if (index == 0) {
                return placeholder;
            } else {
                throw new IndexOutOfBoundsException();
            }
        }

        @Override
        public Lookup getLookup() {
            Integer w = getInteger(width.value);
            Integer h = getInteger(height.value);
            List<Object> items = new ArrayList<Object>();
            if (w != null && h != null) {
                items.add(new ClientConfiguration.Dimension(w, h));
            }
            Integer rot = getInteger(rotation.value);
            if (rot != null) {
                switch (rot) {
                    case 90:
                        items.add(ClientConfiguration.Rotation.DEG_90);
                        break;
                    case 180:
                        items.add(ClientConfiguration.Rotation.DEG_180);
                        break;
                    case 270:
                        items.add(ClientConfiguration.Rotation.DEG_270);
                        break;
                }
            }
            Integer dev = getInteger(device.value);
            if (dev != null) {
                items.add(new ClientConfiguration.DeviceIndex(dev - 1));
            }
            items.add(wHints);
            return Lookup.of(items.toArray());
        }

        private Integer getInteger(Value val) {
            if (val == null) {
                return null;
            }
            return PNumber.from(val).map(PNumber::toIntValue).orElse(null);
        }
    }
}
