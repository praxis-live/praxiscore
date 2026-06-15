/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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
package org.praxislive.code.userapi;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VecTest {

    @Test
    public void testTransformDoubleBuffer() {
        double[] data = new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        DoubleBuffer buffer = DoubleBuffer.wrap(data);
        Vec.transform(buffer, 2, d -> {
            d[0] = -1;
            d[1] *= 1.5;
        });
        assertArrayEquals(new double[]{-1, 3, -1, 6, -1, 9, -1, 12, 9}, data, 0.001);
        assertEquals(1, buffer.remaining());
    }

    @Test
    public void testTransformIntBuffer() {
        int[] data = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        IntBuffer buffer = IntBuffer.wrap(data);
        Vec.transform(buffer, 2, d -> {
            d[0] = -1;
            d[1] *= 2;
        });
        assertArrayEquals(new int[]{-1, 4, -1, 8, -1, 12, -1, 16, 9}, data);
        assertEquals(1, buffer.remaining());
    }

    @Test
    void testReadDoubleBuffer() {
        double[] data = new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        DoubleBuffer buffer = DoubleBuffer.wrap(data);
        buffer.limit(7);
        List<Vec.D2> vecs = Vec.read(buffer, 2, Vec.D2::new);
        assertEquals(List.of(
                new Vec.D2(1, 2),
                new Vec.D2(3, 4),
                new Vec.D2(5, 6)),
                vecs);
        buffer.clear().position(1);
        vecs = Vec.read(buffer, 4, Vec.D2::new);
        assertEquals(List.of(
                new Vec.D2(2, 3),
                new Vec.D2(6, 7)),
                vecs);
    }

    @Test
    void testReadIntBuffer() {
        int[] data = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        IntBuffer buffer = IntBuffer.wrap(data);
        buffer.limit(7);
        var vec3D = Vec.read(buffer, 3, Vec.I3::new);
        assertEquals(List.of(
                new Vec.I3(1, 2, 3),
                new Vec.I3(4, 5, 6)),
                vec3D);
        buffer.clear().position(1);
        var vec2D = Vec.read(buffer, 4, Vec.I2::new);
        assertEquals(List.of(
                new Vec.I2(2, 3),
                new Vec.I2(6, 7)),
                vec2D);
    }

    @Test
    void testGenerateIndexed_BufD2() {
        Vec.BufD2 buffer = Vec.BufD2.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 2;
            d.y = d.x + 1;
        });
        assertArrayEquals(new double[]{
            0, 1,
            2, 3,
            4, 5,
            0, 0
        }, buffer.data(), 0.001);
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufD3() {
        Vec.BufD3 buffer = Vec.BufD3.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 3;
            d.y = d.x + 1;
            d.z = d.y + 1;
        });
        assertArrayEquals(new double[]{
            0, 1, 2,
            3, 4, 5,
            6, 7, 8,
            0, 0, 0
        }, buffer.data(), 0.001);
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufD4() {
        Vec.BufD4 buffer = Vec.BufD4.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 4;
            d.y = d.x + 1;
            d.z = d.y + 1;
            d.w = d.z + 1;
        });
        assertArrayEquals(new double[]{
            0, 1, 2, 3,
            4, 5, 6, 7,
            8, 9, 10, 11,
            0, 0, 0, 0
        }, buffer.data(), 0.001);
        assertEquals(3, buffer.count());
    }

    @Test
    void testMerge_BufD() {
        Vec.BufD2 buf2 = Vec.BufD2.fromVecList(List.of(
                new Vec.D2(1, 2),
                new Vec.D2(3, 4),
                new Vec.D2(5, 6),
                new Vec.D2(7, 8),
                new Vec.D2(9, 10)
        ));
        Vec.BufD3 buf3 = Vec.BufD3.allocate(4);
        Vec.BufD4 buf4 = Vec.BufD4.allocate(5);
        int copied = buf3.merge(buf2, (dst, src) -> {
            dst.y = src.x;
            dst.z = src.y;
        });
        assertEquals(0, copied);
        assertEquals(0, buf3.count());
        buf3.count(4);
        copied = buf3.merge(buf2, (dst, src) -> {
            dst.y = src.x;
            dst.z = src.y;
        });
        assertEquals(4, copied);
        assertArrayEquals(new double[]{
            0, 1, 2,
            0, 3, 4,
            0, 5, 6,
            0, 7, 8
        }, buf3.data(), 0.001);
        buf4.count(3);
        copied = buf4.merge(buf3, (dst, src) -> {
            dst.x = src.z;
            dst.y = -src.y;
            dst.w = src.z * 2;
        });
        assertEquals(3, copied);
        assertEquals(3, buf4.count());
        assertArrayEquals(new double[]{
            2, -1, 0, 4,
            4, -3, 0, 8,
            6, -5, 0, 12,
            0, 0, 0, 0,
            0, 0, 0, 0
        }, buf4.data(), 0.001);
        buf4.count(5);
        copied = buf2.merge(buf4, (dst, src) -> {
            dst.x = src.y;
            dst.y = src.w;
        });
        assertEquals(5, copied);
        assertEquals(5, buf2.count());
        assertArrayEquals(new double[]{
            -1, 4,
            -3, 8,
            -5, 12,
            0, 0,
            0, 0
        }, buf2.data(), 0.001);
    }

    @Test
    void testTransformIndexed_BufD2() {
        Vec.BufD2 buffer = Vec.BufD2.fromVecList(List.of(
                new Vec.D2(1, 2),
                new Vec.D2(3, 4),
                new Vec.D2(5, 6)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
        });
        assertArrayEquals(new double[]{
            -1, 1,
            -6, 3,
            -15, 5
        }, buffer.data(), 0.001);
        assertEquals(3, buffer.count());
    }

    @Test
    void testTransformIndexed_BufD3() {
        Vec.BufD3 buffer = Vec.BufD3.fromVecList(List.of(
                new Vec.D3(1, 2, 3),
                new Vec.D3(4, 5, 6),
                new Vec.D3(7, 8, 9)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
            d.z = 0;
        });
        assertArrayEquals(new double[]{
            -1, 1, 0,
            -8, 4, 0,
            -21, 7, 0
        }, buffer.data(), 0.001);
        assertEquals(3, buffer.count());
    }

    @Test
    void testTransformIndexed_BufD4() {
        Vec.BufD4 buffer = Vec.BufD4.fromVecList(List.of(
                new Vec.D4(1, 2, 3, 4),
                new Vec.D4(5, 6, 7, 8),
                new Vec.D4(9, 10, 11, 12)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
            d.z += 100;
            d.w = 0;
        });
        assertArrayEquals(new double[]{
            -1, 1, 103, 0,
            -10, 5, 107, 0,
            -27, 9, 111, 0
        }, buffer.data(), 0.001);
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufF2() {
        Vec.BufF2 buffer = Vec.BufF2.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 2;
            d.y = d.x + 1;
        });
        assertArrayEquals(new float[]{
            0, 1,
            2, 3,
            4, 5,
            0, 0
        }, buffer.data(), 0.001f);
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufF3() {
        Vec.BufF3 buffer = Vec.BufF3.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 3;
            d.y = d.x + 1;
            d.z = d.y + 1;
        });
        assertArrayEquals(new float[]{
            0, 1, 2,
            3, 4, 5,
            6, 7, 8,
            0, 0, 0
        }, buffer.data(), 0.001f);
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufF4() {
        Vec.BufF4 buffer = Vec.BufF4.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 4;
            d.y = d.x + 1;
            d.z = d.y + 1;
            d.w = d.z + 1;
        });
        assertArrayEquals(new float[]{
            0, 1, 2, 3,
            4, 5, 6, 7,
            8, 9, 10, 11,
            0, 0, 0, 0
        }, buffer.data(), 0.001f);
        assertEquals(3, buffer.count());
    }

    @Test
    void testMerge_BufF() {
        Vec.BufF2 buf2 = Vec.BufF2.fromVecList(List.of(
                new Vec.D2(1, 2),
                new Vec.D2(3, 4),
                new Vec.D2(5, 6),
                new Vec.D2(7, 8),
                new Vec.D2(9, 10)
        ));
        Vec.BufF3 buf3 = Vec.BufF3.allocate(4);
        Vec.BufF4 buf4 = Vec.BufF4.allocate(5);
        int copied = buf3.merge(buf2, (dst, src) -> {
            dst.y = src.x;
            dst.z = src.y;
        });
        assertEquals(0, copied);
        assertEquals(0, buf3.count());
        buf3.count(4);
        copied = buf3.merge(buf2, (dst, src) -> {
            dst.y = src.x;
            dst.z = src.y;
        });
        assertEquals(4, copied);
        assertArrayEquals(new float[]{
            0, 1, 2,
            0, 3, 4,
            0, 5, 6,
            0, 7, 8
        }, buf3.data(), 0.001f);
        buf4.count(3);
        copied = buf4.merge(buf3, (dst, src) -> {
            dst.x = src.z;
            dst.y = -src.y;
            dst.w = src.z * 2;
        });
        assertEquals(3, copied);
        assertEquals(3, buf4.count());
        assertArrayEquals(new float[]{
            2, -1, 0, 4,
            4, -3, 0, 8,
            6, -5, 0, 12,
            0, 0, 0, 0,
            0, 0, 0, 0
        }, buf4.data(), 0.001f);
        buf4.count(5);
        copied = buf2.merge(buf4, (dst, src) -> {
            dst.x = src.y;
            dst.y = src.w;
        });
        assertEquals(5, copied);
        assertEquals(5, buf2.count());
        assertArrayEquals(new float[]{
            -1, 4,
            -3, 8,
            -5, 12,
            0, 0,
            0, 0
        }, buf2.data(), 0.001f);
    }

    @Test
    void testTransformIndexed_BufF2() {
        Vec.BufF2 buffer = Vec.BufF2.fromVecList(List.of(
                new Vec.D2(1, 2),
                new Vec.D2(3, 4),
                new Vec.D2(5, 6)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
        });
        assertArrayEquals(new float[]{
            -1, 1,
            -6, 3,
            -15, 5
        }, buffer.data(), 0.001f);
        assertEquals(3, buffer.count());
    }

    @Test
    void testTransformIndexed_BufF3() {
        Vec.BufF3 buffer = Vec.BufF3.fromVecList(List.of(
                new Vec.D3(1, 2, 3),
                new Vec.D3(4, 5, 6),
                new Vec.D3(7, 8, 9)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
            d.z = 0;
        });
        assertArrayEquals(new float[]{
            -1, 1, 0,
            -8, 4, 0,
            -21, 7, 0
        }, buffer.data(), 0.001f);
        assertEquals(3, buffer.count());
    }

    @Test
    void testTransformIndexed_BufF4() {
        Vec.BufF4 buffer = Vec.BufF4.fromVecList(List.of(
                new Vec.D4(1, 2, 3, 4),
                new Vec.D4(5, 6, 7, 8),
                new Vec.D4(9, 10, 11, 12)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
            d.z += 100;
            d.w = 0;
        });
        assertArrayEquals(new float[]{
            -1, 1, 103, 0,
            -10, 5, 107, 0,
            -27, 9, 111, 0
        }, buffer.data(), 0.001f);
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufI2() {
        Vec.BufI2 buffer = Vec.BufI2.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 2;
            d.y = d.x + 1;
        });
        assertArrayEquals(new int[]{
            0, 1,
            2, 3,
            4, 5,
            0, 0
        }, buffer.data());
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufI3() {
        Vec.BufI3 buffer = Vec.BufI3.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 3;
            d.y = d.x + 1;
            d.z = d.y + 1;
        });
        assertArrayEquals(new int[]{
            0, 1, 2,
            3, 4, 5,
            6, 7, 8,
            0, 0, 0
        }, buffer.data());
        assertEquals(3, buffer.count());
    }

    @Test
    void testGenerateIndexed_BufI4() {
        Vec.BufI4 buffer = Vec.BufI4.allocate(4);
        assertEquals(4, buffer.capacity());
        assertEquals(0, buffer.count());
        buffer.generateIndexed(3, (d, i) -> {
            d.x = i * 4;
            d.y = d.x + 1;
            d.z = d.y + 1;
            d.w = d.z + 1;
        });
        assertArrayEquals(new int[]{
            0, 1, 2, 3,
            4, 5, 6, 7,
            8, 9, 10, 11,
            0, 0, 0, 0
        }, buffer.data());
        assertEquals(3, buffer.count());
    }

    @Test
    void testMerge_BufI() {
        Vec.BufI2 buf2 = Vec.BufI2.fromVecList(List.of(
                new Vec.I2(1, 2),
                new Vec.I2(3, 4),
                new Vec.I2(5, 6),
                new Vec.I2(7, 8),
                new Vec.I2(9, 10)
        ));
        Vec.BufI3 buf3 = Vec.BufI3.allocate(4);
        Vec.BufI4 buf4 = Vec.BufI4.allocate(5);
        int copied = buf3.merge(buf2, (dst, src) -> {
            dst.y = src.x;
            dst.z = src.y;
        });
        assertEquals(0, copied);
        assertEquals(0, buf3.count());
        buf3.count(4);
        copied = buf3.merge(buf2, (dst, src) -> {
            dst.y = src.x;
            dst.z = src.y;
        });
        assertEquals(4, copied);
        assertArrayEquals(new int[]{
            0, 1, 2,
            0, 3, 4,
            0, 5, 6,
            0, 7, 8
        }, buf3.data());
        buf4.count(3);
        copied = buf4.merge(buf3, (dst, src) -> {
            dst.x = src.z;
            dst.y = -src.y;
            dst.w = src.z * 2;
        });
        assertEquals(3, copied);
        assertEquals(3, buf4.count());
        assertArrayEquals(new int[]{
            2, -1, 0, 4,
            4, -3, 0, 8,
            6, -5, 0, 12,
            0, 0, 0, 0,
            0, 0, 0, 0
        }, buf4.data());
        buf4.count(5);
        copied = buf2.merge(buf4, (dst, src) -> {
            dst.x = src.y;
            dst.y = src.w;
        });
        assertEquals(5, copied);
        assertEquals(5, buf2.count());
        assertArrayEquals(new int[]{
            -1, 4,
            -3, 8,
            -5, 12,
            0, 0,
            0, 0
        }, buf2.data());
    }

    @Test
    void testTransformIndexed_BufI2() {
        Vec.BufI2 buffer = Vec.BufI2.fromVecList(List.of(
                new Vec.I2(1, 2),
                new Vec.I2(3, 4),
                new Vec.I2(5, 6)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
        });
        assertArrayEquals(new int[]{
            -1, 1,
            -6, 3,
            -15, 5
        }, buffer.data());
        assertEquals(3, buffer.count());
    }

    @Test
    void testTransformIndexed_BufI3() {
        Vec.BufI3 buffer = Vec.BufI3.fromVecList(List.of(
                new Vec.I3(1, 2, 3),
                new Vec.I3(4, 5, 6),
                new Vec.I3(7, 8, 9)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
            d.z = 0;
        });
        assertArrayEquals(new int[]{
            -1, 1, 0,
            -8, 4, 0,
            -21, 7, 0
        }, buffer.data());
        assertEquals(3, buffer.count());
    }

    @Test
    void testTransformIndexed_BufI4() {
        Vec.BufI4 buffer = Vec.BufI4.fromVecList(List.of(
                new Vec.I4(1, 2, 3, 4),
                new Vec.I4(5, 6, 7, 8),
                new Vec.I4(9, 10, 11, 12)
        ));
        buffer.transformIndexed((d, i) -> {
            d.y = d.x;
            d.x *= -1 - i;
            d.z += 100;
            d.w = 0;
        });
        assertArrayEquals(new int[]{
            -1, 1, 103, 0,
            -10, 5, 107, 0,
            -27, 9, 111, 0
        }, buffer.data());
        assertEquals(3, buffer.count());
    }

}
