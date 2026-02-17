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
 */
package org.praxislive.core.types;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.praxislive.core.AbstractTestBase;
import org.praxislive.core.DataObject;

import static org.junit.jupiter.api.Assertions.*;

public class PBytesTest extends AbstractTestBase {

    private static class Data implements DataObject {

        double x, y, z;

        Data() {
        }

        Data(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void writeTo(DataOutput out) throws Exception {
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
        }

        @Override
        public void readFrom(DataInput in) throws Exception {
            x = in.readDouble();
            y = in.readDouble();
            z = in.readDouble();
        }

        @Override
        public String toString() {
            return String.format("Data : %.2f,%.2f,%.2f", x, y, z);
        }
    }

    private static class FailedData implements DataObject {

        @Override
        public void writeTo(DataOutput out) throws Exception {
        }

        @Override
        public void readFrom(DataInput in) throws Exception {
        }

    }

    private final PBytes testBytes;

    public PBytesTest() throws IOException {
        PBytes.OutputStream os = new PBytes.OutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        dos.writeDouble(10);
        dos.writeDouble(20);
        dos.writeDouble(30);
        dos.writeDouble(40);
        dos.writeDouble(50);
        dos.writeDouble(60);
        dos.flush();
        testBytes = os.toBytes();
    }

    /**
     * Test of forEachIn method, of class PBytes.
     */
    @Test
    public void testForEachIn() throws IOException {
        PBytes bytes = testBytes;
        Data data = new Data();
        int[] count = new int[1];
        bytes.forEachIn(data, d -> {
            log(d);
            count[0]++;
        });
        assertEquals(data.x, 40, 0.001);
        assertEquals(data.y, 50, 0.001);
        assertEquals(data.z, 60, 0.001);
        assertEquals(count[0], 2);

        FailedData failer = new FailedData();
        Exception ex = null;
        count[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> {
            bytes.forEachIn(failer, f -> {
                count[0]++;
            });
        });
        assertEquals(count[0], 0);
    }

    /**
     * Test of transformIn method, of class PBytes.
     */
    @Test
    public void testTransformIn() throws IOException {
        PBytes bytes = testBytes;
        Data data = new Data();
        bytes = bytes.transformIn(data, d -> {
            log(String.format("Data : %.2f,%.2f,%.2f", d.x, d.y, d.z));
            d.x *= 2;
            d.y *= -2;
            d.z = Math.PI;
        });
        int[] count = new int[1];
        bytes.transformIn(data, d -> {
            log(d);
            count[0]++;
        });
        assertEquals(data.x, 40 * 2, 0.001);
        assertEquals(data.y, 50 * -2, 0.001);
        assertEquals(data.z, Math.PI, 0.001);
        assertEquals(count[0], 2);
        assertEquals(bytes.size(), 8 * 3 * 2);
    }

    /**
     * Test of streamOf method, of class PBytes.
     */
    @Test
    public void testStreamOf_Supplier() throws IOException {
        PBytes bytes = testBytes;
        List<Data> list = bytes.streamOf(Data::new).collect(Collectors.toList());
        log(list);
        assertEquals(list.size(), 2);
        assertEquals(list.get(0).x, 10, 0.001);
        assertEquals(list.get(0).y, 20, 0.001);
        assertEquals(list.get(0).z, 30, 0.001);
        assertEquals(list.get(1).x, 40, 0.001);
        assertEquals(list.get(1).y, 50, 0.001);
        assertEquals(list.get(1).z, 60, 0.001);
    }

    /**
     * Test of streamOf method, of class PBytes.
     */
    @Test
    public void testStreamOf_int_Supplier() {
        PBytes bytes = testBytes;
        List<Data> list = bytes.streamOf(5, Data::new)
                .map(d -> {
                    if (d.x == 0) {
                        d.x = 70;
                        d.y = 80;
                        d.z = 90;
                    }
                    return d;
                })
                .collect(Collectors.toList());
        log(list);
        assertEquals(list.size(), 5);
        assertEquals(list.get(0).x, 10, 0.001);
        assertEquals(list.get(0).y, 20, 0.001);
        assertEquals(list.get(0).z, 30, 0.001);
        assertEquals(list.get(1).x, 40, 0.001);
        assertEquals(list.get(1).y, 50, 0.001);
        assertEquals(list.get(1).z, 60, 0.001);
        assertEquals(list.get(2).x, 70, 0.001);
        assertEquals(list.get(2).y, 80, 0.001);
        assertEquals(list.get(2).z, 90, 0.001);
    }

    /**
     * Test of collector method, of class PBytes.
     */
    @Test
    public void testCollector() {
        PBytes bytes = Stream.of(new Data(10, 20, 30), new Data(40, 50, 60)).collect(PBytes.collector());
        assertEquals(bytes, testBytes);
        Data d = new Data();
        PBytes bytes2 = bytes.streamOf((() -> d)).collect(PBytes.collector());
        assertEquals(bytes2, testBytes);
    }

    /**
     * Test of deserialize method, of class PBytes.
     */
    @Test
    public void testDeserialize() throws IOException {
        int[] ints = new int[]{1, 2, 3, 4, 5};
        PBytes bytes = PBytes.serialize(ints);
        log("Serialized size : " + bytes.size());
        log("Base64 : " + bytes.toString());
        int[] out = bytes.deserialize(int[].class);
        assertArrayEquals(ints, out);
        assertThrows(IOException.class, () -> {
            double[] dbles = bytes.deserialize(double[].class);
        });
    }
}
