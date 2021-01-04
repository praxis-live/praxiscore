/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
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
package org.praxislive.video.pgl;

import org.lwjgl.system.Platform;
import org.praxislive.core.Lookup;
import org.praxislive.core.MainThread;
import org.praxislive.video.ClientConfiguration;
import org.praxislive.video.Player;
import org.praxislive.video.PlayerConfiguration;
import org.praxislive.video.PlayerFactory;
import org.praxislive.video.QueueContext;
import org.praxislive.video.RenderingHints;
import org.praxislive.video.WindowHints;

/**
 *
 */
public class PGLPlayerFactory implements PlayerFactory {

    private final PGLProfile profile;

    private PGLPlayerFactory(PGLProfile profile) {
        this.profile = profile;
    }

    @Override
    public Player createPlayer(PlayerConfiguration config, ClientConfiguration[] clients)
            throws Exception {
        if (clients.length != 1 || clients[0].getSourceCount() != 0 || clients[0].getSinkCount() != 1) {
            throw new IllegalArgumentException("Invalid client configuration");
        }

        int width = config.getWidth();
        int height = config.getHeight();

        Lookup configLookup = config.getLookup();

        RenderingHints renderHints = configLookup.find(RenderingHints.class).orElseGet(RenderingHints::new);

        // @TODO fix default profile lookup support
        PGLProfile glProfile = profile;
        if (profile == null) {
            if (Platform.get() == Platform.LINUX &&
                    (Platform.getArchitecture() == Platform.Architecture.ARM32 ||
                    Platform.getArchitecture() == Platform.Architecture.ARM64)) {
                glProfile = PGLProfile.GLES2;
            } else {
                glProfile = PGLProfile.GL3;
            }
        }
        Lookup clientLookup = clients[0].getLookup();

        int outWidth = clientLookup.find(ClientConfiguration.Dimension.class)
                .map(ClientConfiguration.Dimension::getWidth)
                .orElse(width);

        int outHeight = clientLookup.find(ClientConfiguration.Dimension.class)
                .map(ClientConfiguration.Dimension::getHeight)
                .orElse(height);

        int rotation = clientLookup.find(ClientConfiguration.Rotation.class)
                .map(ClientConfiguration.Rotation::getAngle)
                .filter(i -> i == 0 || i == 90 || i == 180 || i == 270)
                .orElse(0);

        int device = clientLookup.find(ClientConfiguration.DeviceIndex.class)
                .map(ClientConfiguration.DeviceIndex::getValue)
                .orElse(-1);

        WindowHints wHints = clientLookup.find(WindowHints.class).orElseGet(WindowHints::new);

        // @TODO fake rather than get()?
        QueueContext queue = config.getLookup().find(QueueContext.class).get();
        MainThread main = config.getLookup().find(MainThread.class).get();

        return new PGLPlayer(
                config.getClock(),
                config.getWidth(),
                config.getHeight(),
                config.getFPS(),
                renderHints,
                outWidth,
                outHeight,
                rotation,
                device,
                wHints,
                main,
                queue,
                glProfile);

    }

    public static class Default implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(null);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL";
        }

    }

    public static class GL2 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GL2);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GL2";
        }

    }

    public static class GL3 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GL3);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GL3";
        }

    }

    public static class GL4 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GL4);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GL4";
        }

    }

    public static class GLES2 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GLES2);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GLES2";
        }

    }

}
