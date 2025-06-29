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
package org.praxislive.video.pgl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.core.Clock;
import org.praxislive.core.Lookup;
import org.praxislive.core.MainThread;
import org.praxislive.video.Player;
import org.praxislive.video.QueueContext;
import org.praxislive.video.RenderingHints;
import org.praxislive.video.WindowHints;
import org.praxislive.video.pipes.FrameRateListener;
import org.praxislive.video.pipes.VideoPipe;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PSurface;
import processing.lwjgl.PLWJGL;
import processing.lwjgl.PSurfaceLWJGL;

/**
 *
 */
public class PGLPlayer implements Player {

    private final static Logger LOG = Logger.getLogger(PGLPlayer.class.getName());
    private final int surfaceWidth, surfaceHeight; // dimensions of surface
    private final int outputWidth, outputHeight, outputRotation, outputDevice;
    private final double fps; // frames per second
    private final long frameNanos;
    private final RenderingHints renderHints;
    private final WindowHints wHints;
    private final QueueContext queue;
    private final PGLProfile profile;
    private final Clock clock;
    private final MainThread main;

    private volatile boolean running = false; // flag to control animation
    private volatile long time;
    private List<FrameRateListener> listeners = new ArrayList<>();
    private Applet applet = null;
    private PGLOutputSink sink = null;
    private Lookup lookup;

    PGLPlayer(
            Clock clock,
            int width,
            int height,
            double fps,
            RenderingHints renderHints,
            int outputWidth,
            int outputHeight,
            int outputRotation,
            int outputDevice,
            WindowHints wHints,
            MainThread main,
            QueueContext queue,
            PGLProfile profile) {
        if (width <= 0 || height <= 0 || fps <= 0) {
            throw new IllegalArgumentException();
        }
        this.clock = clock;
        this.surfaceWidth = width;
        this.surfaceHeight = height;
        this.fps = fps;
        this.renderHints = renderHints;
        frameNanos = (long) (1000000000.0 / fps);
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.outputRotation = outputRotation;
        this.outputDevice = outputDevice;
        this.wHints = wHints;
        this.main = main;
        this.queue = queue;
        this.profile = profile;

        applet = new Applet();
        sink = new PGLOutputSink();
        lookup = Lookup.of(applet);
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public void run() {
        running = true;
        time = clock.getTime();
        try {
            init();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Unable to start OpenGL player", ex);
            running = false;
            try {
                applet.dispose();
            } catch (Exception ex2) {
                LOG.log(Level.FINE, "Unable to dispose PApplet", ex2);
            }
        }
        while (running) {
            try {
                if (clock.getTime() < (time + frameNanos - 2_000_000)) {
                    synchronized (applet) {
                        queue.process(0, TimeUnit.MILLISECONDS);
                    }
                }// else {
                Thread.sleep(1);
//                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Exception during run", ex);
            }
        }
        if (applet.error != null) {
            throw new RuntimeException(applet.error);
        }
    }

    private void init() throws Exception {

        main.runLater(() -> {

            if (PApplet.mainThread() == null) {
                PApplet.setMainThreadContext(new PApplet.MainThreadContext() {

                    public void runLater(Runnable task) {
                        main.runLater(task);
                    }

                    public boolean isMainThread() {
                        return main.isMainThread();
                    }

                });
            }

            if (outputDevice > -1) {
                PApplet.runSketch(new String[]{
                    "--display=" + (outputDevice + 1),
                    "PraxisLIVE"
                }, applet);
            } else {
                PApplet.runSketch(new String[]{"PraxisLIVE"}, applet);
            }

        });

    }

    private void fireListeners() {
        int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).nextFrame(this);
        }
    }

    @Override
    public VideoPipe getSource(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getSourceCount() {
        return 0;
    }

    @Override
    public VideoPipe getSink(int index) {
        if (index == 0) {
            return getOutputSink();
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public int getSinkCount() {
        return 1;
    }

    public VideoPipe getOutputSink() {
        return sink;
    }

    @Override
    public void terminate() {
        if (running) {
            applet.exit();
        }
    }

    @Override
    public void addFrameRateListener(FrameRateListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        if (listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
    }

    @Override
    public void removeFrameRateListener(FrameRateListener listener) {
        listeners.remove(listener);
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public boolean isRendering() {
        return applet.rendering;
    }

    class Applet extends PApplet {

        Throwable error;

        private final PGLContext context;
        private boolean rendering;
        private PGLSurface pglSurface;

        private Applet() {
            context = new PGLContext(this, profile, surfaceWidth, surfaceHeight);
        }

        @Override
        protected PGraphics makeGraphics(int w, int h, String renderer, String path, boolean primary) {
            if (PGLGraphics.ID.equals(renderer) || P2D.equals(renderer)) {
                PGLGraphics pgl = new PGLGraphics(context, primary, w, h);
                return pgl;
            } else if (PGLGraphics3D.ID.equals(renderer) || P3D.equals(renderer)) {
                PGLGraphics3D pgl3d = new PGLGraphics3D(context, primary, w, h);
                return pgl3d;
            } else {
                throw new Error();
            }
        }

        @Override
        public void settings() {
            switch (profile) {
                case GL2:
                    PLWJGL.profile = 1;
                    break;
                case GL3:
                    PLWJGL.profile = 3;
                    break;
                case GL4:
                    PLWJGL.profile = 4;
                    break;
                case GLES2:
                    PLWJGL.profile = 2;
                    break;
            }
            if (wHints.isFullScreen()) {
                if (outputDevice > -1) {
                    fullScreen(PGLGraphics.ID, outputDevice + 1);
                } else {
                    fullScreen(PGLGraphics.ID);
                }
            } else {
                if (outputRotation == 0 || outputRotation == 180) {
                    size(outputWidth, outputHeight, PGLGraphics.ID);
                } else {
                    size(outputHeight, outputWidth, PGLGraphics.ID);
                }
            }
            if (!renderHints.isSmooth()) {
                noSmooth();
            }
        }

        @Override
        protected PSurface initSurface() {
            PSurface s = super.initSurface();
            s.setTitle(wHints.getTitle());
            s.setResizable(true);
            if (wHints.isUndecorated() && s instanceof PSurfaceLWJGL gls) {
                gls.setUndecorated(true);
            }
            return s;
        }

        @Override
        public void setup() {
            assert pglSurface == null;
            if (pglSurface == null) {
                pglSurface = context.createSurface(surfaceWidth, surfaceHeight, false);
            }
            if (!wHints.isShowCursor()) {
                noCursor();
            }
            if (wHints.isAlwaysOnTop()) {
                surface.setAlwaysOnTop(wHints.isAlwaysOnTop());
            }
            frameRate((float) fps);
        }

        @Override
        public synchronized void draw() {
            time = clock.getTime() + frameNanos;
            // @TODO clamp to window size if allowing window resizing
            int renderWidth = outputWidth;
            int renderHeight = outputHeight;
            boolean vertical = outputRotation == 90 || outputRotation == 270;
            if (vertical) {
                if (height < renderWidth || width < renderHeight) {
                    double ratio = Math.min((double) height / renderWidth, (double) width / renderHeight);
                    renderWidth *= ratio;
                    renderHeight *= ratio;
                }
            } else {
                if (width < renderWidth || height < renderHeight) {
                    double ratio = Math.min((double) width / renderWidth, (double) height / renderHeight);
                    renderWidth *= ratio;
                    renderHeight *= ratio;
                }
            }
            if (outputRotation != 0
                    || surfaceWidth != width
                    || surfaceHeight != height) {
                int mX = mouseX;
                int mY = mouseY;
                int pmX = pmouseX;
                int pmY = pmouseY;
                transformMouse(vertical, renderWidth, renderHeight);
                fireListeners();
                sink.process(pglSurface, time);
                mouseX = mX;
                mouseY = mY;
                pmouseX = pmX;
                pmouseY = pmY;
            } else {
                fireListeners();
                sink.process(pglSurface, time);
            }
            PImage img = context.asImage(pglSurface);
            context.primary().endOffscreen();
            background(0, 0, 0);
            translate(width / 2, height / 2);
            rotate(radians(outputRotation));
            image(img, -renderWidth / 2, -renderHeight / 2,
                    renderWidth, renderHeight);
        }

        private void transformMouse(boolean vertical, int renderWidth, int renderHeight) {
            double renderWidthRatio = (double) surfaceWidth / renderWidth;
            double renderHeightRatio = (double) surfaceHeight / renderHeight;
            int xformRenderWidth = vertical ? renderHeight : renderWidth;
            int xformRenderHeight = vertical ? renderWidth : renderHeight;
            if (width > xformRenderWidth) {
                int diff = width - xformRenderWidth;
                mouseX -= diff / 2;
                pmouseX -= diff / 2;
                mouseX = mouseX < 0 ? 0 : mouseX > xformRenderWidth ? xformRenderWidth : mouseX;
                pmouseX = pmouseX < 0 ? 0 : pmouseX > xformRenderWidth ? xformRenderWidth : pmouseX;
            }
            if (height > xformRenderHeight) {
                int diff = height - xformRenderHeight;
                mouseY -= diff / 2;
                pmouseY -= diff / 2;
                mouseY = mouseY < 0 ? 0 : mouseY > xformRenderHeight ? xformRenderHeight : mouseY;
                pmouseY = pmouseY < 0 ? 0 : pmouseY > xformRenderHeight ? xformRenderHeight : pmouseY;
            }

            double x, y;
            switch (outputRotation) {
                case 90 -> {
                    x = mouseY * renderWidthRatio;
                    y = mouseX * renderHeightRatio;
                    mouseX = (int) (x);
                    mouseY = (int) (surfaceHeight - y);
                    x = pmouseY * renderWidthRatio;
                    y = pmouseX * renderHeightRatio;
                    pmouseX = (int) (x);
                    pmouseY = (int) (surfaceHeight - y);
                }
                case 180 -> {
                    x = mouseX * renderWidthRatio;
                    y = mouseY * renderHeightRatio;
                    mouseX = (int) (surfaceWidth - x);
                    mouseY = (int) (surfaceHeight - y);
                    x = pmouseX * renderWidthRatio;
                    y = pmouseY * renderHeightRatio;
                    pmouseX = (int) (surfaceWidth - x);
                    pmouseY = (int) (surfaceHeight - y);
                }
                case 270 -> {
                    x = mouseY * renderWidthRatio;
                    y = mouseX * renderHeightRatio;
                    mouseX = (int) (surfaceWidth - x);
                    mouseY = (int) (y);
                    x = pmouseY * renderWidthRatio;
                    y = pmouseX * renderHeightRatio;
                    pmouseX = (int) (surfaceWidth - x);
                    pmouseY = (int) (y);
                }
                default -> {
                    mouseX *= renderWidthRatio;
                    mouseY *= renderHeightRatio;
                    pmouseX *= renderWidthRatio;
                    pmouseY *= renderHeightRatio;
                }
            }

        }

        @Override
        public synchronized void dispose() {
            sink.disconnect();
            context.dispose();
            super.dispose();
        }

        @Override
        public void exitActual() {
            running = false;
        }

        @Override
        public void die(String what) {
            throw new RuntimeException(what);
        }

        @Override
        public void die(String what, Exception e) {
            throw new RuntimeException(what, e);
        }

    }

}
