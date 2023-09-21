/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2022 Neil C Smith.
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
package org.praxislive.code;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Random;
import org.praxislive.code.userapi.Constants;

/**
 * Default base for code delegates providing a variety of functions.
 */
public class DefaultCodeDelegate extends CodeDelegate implements DefaultDelegateAPI {

    final static List<String> DEFAULT_IMPORTS = List.of(
        "java.util.*",
        "java.util.function.*",
        "java.util.stream.*",
        "org.praxislive.core.*",
        "org.praxislive.core.types.*",
        "org.praxislive.code.userapi.*",
        "static org.praxislive.code.userapi.Constants.*"
    );

    protected final Random RND;

    public DefaultCodeDelegate() {
        RND = new Random();
    }

    /**
     * Return a random number between zero and max (exclusive)
     *
     * @param max the upper bound of the range
     * @return
     */
    @Override
    public final double random(double max) {
        return RND.nextDouble() * max;
    }

    /**
     * Return a random number between min (inclusive) and max (exclusive)
     *
     * @param min the lower bound of the range
     * @param max the upper bound of the range
     * @return
     */
    @Override
    public final double random(double min, double max) {
        if (min >= max) {
            return min;
        }
        return random(max - min) + min;
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    @Override
    public final double randomOf(double... values) {
        return values[RND.nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    @Override
    public final int randomOf(int... values) {
        return values[RND.nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    @Override
    public final String randomOf(String... values) {
        return values[RND.nextInt(values.length)];
    }

    // PERLIN NOISE - copied from Processing core.
    // @TODO fully convert to double???
    private static final int PERLIN_YWRAPB = 4;
    private static final int PERLIN_YWRAP = 1 << PERLIN_YWRAPB;
    private static final int PERLIN_ZWRAPB = 8;
    private static final int PERLIN_ZWRAP = 1 << PERLIN_ZWRAPB;
    private static final int PERLIN_SIZE = 4095;
//  private static final float sinLUT[];
    private static final float cosLUT[];
    private static final float SINCOS_PRECISION = 0.5f;
    private static final int SINCOS_LENGTH = (int) (360f / SINCOS_PRECISION);

    static {
//    sinLUT = new float[SINCOS_LENGTH];
        cosLUT = new float[SINCOS_LENGTH];
        for (int i = 0; i < SINCOS_LENGTH; i++) {
//      sinLUT[i] = (float) Math.sin(i * Constants.DEG_TO_RAD * SINCOS_PRECISION);
            cosLUT[i] = (float) Math.cos(i * Constants.DEG_TO_RAD * SINCOS_PRECISION);
        }
    }
    private int perlin_octaves = 4; // default to medium smooth
    private float perlin_amp_falloff = 0.5f; // 50% reduction/octave
    private int perlin_TWOPI, perlin_PI;
    private float[] perlin_cosTable;
    private float[] perlin;
    private Random perlinRandom;

    /**
     * Computes the Perlin noise function value at point x.
     *
     * @param x
     * @return
     */
    @Deprecated
    public double noise(double x) {
        return noise(x, 0f, 0f);
    }

    /**
     * Computes the Perlin noise function value at the point x, y.
     *
     * @param x
     * @param y
     * @return
     */
    @Deprecated
    public double noise(double x, double y) {
        return noise(x, y, 0f);
    }

    /**
     * Computes the Perlin noise function value at x, y, z.
     *
     * @param x
     * @param z
     * @param y
     * @return
     */
    @Deprecated
    public double noise(double x, double y, double z) {
        if (perlin == null) {
            if (perlinRandom == null) {
                perlinRandom = new Random();
            }
            perlin = new float[PERLIN_SIZE + 1];
            for (int i = 0; i < PERLIN_SIZE + 1; i++) {
                perlin[i] = perlinRandom.nextFloat();
            }
            perlin_cosTable = cosLUT;
            perlin_TWOPI = perlin_PI = SINCOS_LENGTH;
            perlin_PI >>= 1;
        }

        if (x < 0) {
            x = -x;
        }
        if (y < 0) {
            y = -y;
        }
        if (z < 0) {
            z = -z;
        }

        int xi = (int) x, yi = (int) y, zi = (int) z;
        float xf = (float) (x - xi);
        float yf = (float) (y - yi);
        float zf = (float) (z - zi);
        float rxf, ryf;

        float r = 0;
        float ampl = 0.5f;

        float n1, n2, n3;

        for (int i = 0; i < perlin_octaves; i++) {
            int of = xi + (yi << PERLIN_YWRAPB) + (zi << PERLIN_ZWRAPB);

            rxf = noise_fsc(xf);
            ryf = noise_fsc(yf);

            n1 = perlin[of & PERLIN_SIZE];
            n1 += rxf * (perlin[(of + 1) & PERLIN_SIZE] - n1);
            n2 = perlin[(of + PERLIN_YWRAP) & PERLIN_SIZE];
            n2 += rxf * (perlin[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n2);
            n1 += ryf * (n2 - n1);

            of += PERLIN_ZWRAP;
            n2 = perlin[of & PERLIN_SIZE];
            n2 += rxf * (perlin[(of + 1) & PERLIN_SIZE] - n2);
            n3 = perlin[(of + PERLIN_YWRAP) & PERLIN_SIZE];
            n3 += rxf * (perlin[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n3);
            n2 += ryf * (n3 - n2);

            n1 += noise_fsc(zf) * (n2 - n1);

            r += n1 * ampl;
            ampl *= perlin_amp_falloff;
            xi <<= 1;
            xf *= 2;
            yi <<= 1;
            yf *= 2;
            zi <<= 1;
            zf *= 2;

            if (xf >= 1.0f) {
                xi++;
                xf--;
            }
            if (yf >= 1.0f) {
                yi++;
                yf--;
            }
            if (zf >= 1.0f) {
                zi++;
                zf--;
            }
        }
        return r;
    }

    private float noise_fsc(float i) {
        return 0.5f * (1.0f - perlin_cosTable[(int) (i * perlin_PI) % perlin_TWOPI]);
    }

    // make perlin noise quality user controlled to allow
    // for different levels of detail. lower values will produce
    // smoother results as higher octaves are surpressed
    /**
     *
     * @param lod
     */
    @Deprecated
    public void noiseDetail(int lod) {
        if (lod > 0) {
            perlin_octaves = lod;
        }
    }

    /**
     *
     * @param lod
     * @param falloff
     */
    @Deprecated
    public void noiseDetail(int lod, double falloff) {
        if (lod > 0) {
            perlin_octaves = lod;
        }
        if (falloff > 0) {
            perlin_amp_falloff = (float) falloff;
        }
    }

    /**
     *
     * @param what
     */
    @Deprecated
    public void noiseSeed(long what) {
        if (perlinRandom == null) {
            perlinRandom = new Random();
        }
        perlinRandom.setSeed(what);
        perlin = null;
    }

    // end of Perlin noise functions
    // start of PApplet statics
    /**
     * Copies an array (or part of an array) to another array. The src array is
     * copied to the dst array, beginning at the position specified by srcPos
     * and into the position specified by dstPos. The number of elements to copy
     * is determined by length.
     *
     * @param src
     * @param srcPosition
     * @param dst
     * @param dstPosition
     * @param length
     */
    @Deprecated
    public void arrayCopy(Object src, int srcPosition, Object dst, int dstPosition, int length) {
        System.arraycopy(src, srcPosition, dst, dstPosition, length);
    }

    /**
     * Copies an array (or part of an array) to another array. The src array is
     * copied to the dst array. The number of elements to copy is determined by
     * length.
     *
     * @param src
     * @param dst
     * @param length
     */
    @Deprecated
    public void arrayCopy(Object src, Object dst, int length) {
        System.arraycopy(src, 0, dst, 0, length);
    }

    /**
     * Copies an array to another array. The src array is copied to the dst
     * array.
     *
     * @param src
     * @param dst
     */
    @Deprecated
    public void arrayCopy(Object src, Object dst) {
        System.arraycopy(src, 0, dst, 0, Array.getLength(src));
    }

}
