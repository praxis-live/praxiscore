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
package org.praxislive.audio.components;

import org.praxislive.code.GenerateTemplate;

import org.praxislive.audio.code.AudioCodeDelegate;

// default imports
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import org.praxislive.core.*;
import org.praxislive.core.types.*;
import org.praxislive.code.userapi.*;
import static org.praxislive.code.userapi.Constants.*;

import org.jaudiolibs.pipes.*;
import org.jaudiolibs.pipes.units.*;
import org.praxislive.audio.code.userapi.*;
import static org.praxislive.audio.code.userapi.AudioConstants.*;

/**
 *
 */
@GenerateTemplate(AudioLevel.TEMPLATE_PATH)
public class AudioLevel extends AudioCodeDelegate {
    
    final static String TEMPLATE_PATH = "resources/audio_level.pxj";

    // PXJ-BEGIN:body
    
    @In(1) AudioIn in;
    @Out(1) AudioOut out;
    
    @AuxOut(1) Output level;
    
    private float[] cache;

    @Override
    public void init() {
        link(in, new AudioMeasure(), out);
    }

    @Override
    public void update() {
        if (cache != null) {
            level.send(calculateRMS(cache));
        } else {
            level.send(0);
        }
    }
    
    private double calculateRMS(float[] buffer) {
        double ret = 0;
        for (float sample : buffer) {
            ret += (sample * sample);
        }
        ret /= buffer.length;
        return Math.sqrt(ret);
    }

    private class AudioMeasure extends Pipe {
        
        AudioMeasure() {
            super(1,1);
        }

        @Override
        protected void process(List<Buffer> list) {
            Buffer buffer = list.get(0);
            float[] audio = buffer.getData();
            int size = buffer.getSize();
            if (cache == null || cache.length != size) {
                cache = new float[size];
            }
            System.arraycopy(audio, 0, cache, 0, size); 
        }
        
    }
    
    // PXJ-END:body
    
}
