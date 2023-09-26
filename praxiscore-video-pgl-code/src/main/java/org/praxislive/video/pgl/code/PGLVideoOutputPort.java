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
 *
 */
package org.praxislive.video.pgl.code;

import org.praxislive.code.CodeContext;
import org.praxislive.code.PortDescriptor;
import org.praxislive.core.PortInfo;
import org.praxislive.core.types.PMap;
import org.praxislive.video.DefaultVideoOutputPort;
import org.praxislive.video.VideoPort;
import org.praxislive.video.pipes.VideoPipe;
import org.praxislive.video.pipes.impl.Placeholder;

/**
 *
 *
 */
class PGLVideoOutputPort extends DefaultVideoOutputPort {

    private Placeholder pipe;

    private PGLVideoOutputPort(Placeholder pipe) {
        super(pipe);
        this.pipe = pipe;
    }

    VideoPipe getPipe() {
        return pipe;
    }

    static class Descriptor extends PortDescriptor<Descriptor> {

        private final static PortInfo INFO = PortInfo.create(VideoPort.class, PortInfo.Direction.OUT, PMap.EMPTY);

        private PGLVideoOutputPort port;

        Descriptor(String id, int index) {
            super(Descriptor.class, id, Category.Out, index);
        }

        @Override
        public void attach(CodeContext<?> context, Descriptor previous) {
            if (previous != null) {
                PGLVideoOutputPort vip = previous.port;
                if (vip.pipe.getSourceCount() == 1) {
                    vip.pipe.removeSource(vip.pipe.getSource(0));
                }
                port = vip;
            } else {
                port = new PGLVideoOutputPort(new Placeholder());
            }
        }

        @Override
        public PGLVideoOutputPort port() {
            return port;
        }

        @Override
        public PortInfo portInfo() {
            return INFO;
        }

    }

}
