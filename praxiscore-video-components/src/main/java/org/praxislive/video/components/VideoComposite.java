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
package org.praxislive.video.components;

import org.praxislive.code.GenerateTemplate;

import org.praxislive.video.code.VideoCodeDelegate;

// default imports
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import org.praxislive.core.*;
import org.praxislive.core.types.*;
import org.praxislive.code.userapi.*;
import static org.praxislive.code.userapi.Constants.*;
import org.praxislive.video.code.userapi.*;
import static org.praxislive.video.code.userapi.VideoConstants.*;

/**
 *
 *
 */
@GenerateTemplate(VideoComposite.TEMPLATE_PATH)
public class VideoComposite extends VideoCodeDelegate {

    final static String TEMPLATE_PATH = "resources/composite.pxj";

    // PXJ-BEGIN:body

    @In(1) PImage in;
    @In(2) PImage src;

    @P(1) BlendMode mode;
    @P(2) @Type.Number(min = 0, max = 1, def = 1) double mix;
    @P(3) boolean forceAlpha;

    @Persist Async<PBytes> watchInResponse, watchSrcResponse;

    @Override
    public void init() {
        attachAlphaQuery("src", outAlpha -> outAlpha || forceAlpha);
    }

    @Override
    public void draw() {
        checkWatches();
        copy(in);
        release(in);
        blendMode(mode, mix);
        image(src, 0, 0);
    }

    @FN.Watch(mime = MIME_PNG, relatedPort = "in")
    Async<PBytes> watchIn() {
        if (watchInResponse == null || watchInResponse.failed()) {
            watchInResponse = timeout(1, new Async<>());
        }
        return watchInResponse;
    }

    @FN.Watch(mime = MIME_PNG, relatedPort = "src")
    Async<PBytes> watchSrc() {
        if (watchSrcResponse == null || watchSrcResponse.failed()) {
            watchSrcResponse = timeout(1, new Async<>());
        }
        return watchSrcResponse;
    }

    private void checkWatches() {
        double size = max(width, height);
        double scale = size > 800 ? 400 / size : 0.5;
        if (watchInResponse != null) {
            Async.bind(write(MIME_PNG, in, scale), watchInResponse);
            watchInResponse = null;
        }
        if (watchSrcResponse != null) {
            Async.bind(write(MIME_PNG, src, scale), watchSrcResponse);
            watchSrcResponse = null;
        }
    }

    // PXJ-END:body
}
