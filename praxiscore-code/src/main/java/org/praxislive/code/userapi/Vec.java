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

import java.nio.BufferOverflowException;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

/**
 * A range of integer and floating point vector record types for holding two,
 * three and four dimensional data.
 * <p>
 * This class also contains mutable buffer types for working with arrays holding
 * element data corresponding to the various vector types.
 */
public sealed interface Vec {

    /**
     * Read the data from a {@link DoubleBuffer} into a list of {@code T} by
     * reading into a {@code double[]} of size stride and using the mapper
     * function to create an instance of T. The buffer is read using relative
     * methods taking account of position and limit. The same array is used in
     * every call to the mapper. The returned list is immutable.
     * <p>
     * This method can be used to read a list of Vec into a list - eg.
     * {@code read(buffer, 2, Vec.D2::new);}. The stride can be higher than the
     * component count of the Vec to read interleaved data.
     *
     * @param <T> mapped type
     * @param buffer data to read from
     * @param stride size of array / distance between data values
     * @param mapper function to create T from double array
     * @return list of T
     */
    public static <T> List<T> read(DoubleBuffer buffer, int stride,
            Function<double[], T> mapper) {
        if (stride < 1) {
            throw new IllegalArgumentException("Stride must be greater than zero");
        }
        if (buffer.remaining() < stride) {
            return List.of();
        }
        double[] holder = new double[stride];
        List<T> list = new ArrayList<>(buffer.remaining() / stride);
        while (buffer.remaining() >= stride) {
            buffer.get(holder);
            list.add(mapper.apply(holder));
        }
        return List.copyOf(list);
    }

    /**
     * Read the data from an {@link IntBuffer} into a list of {@code T} by
     * reading into a {@code int[]} of size stride and using the mapper function
     * to create an instance of T. The buffer is read using relative methods
     * taking account of position and limit. The same array is used in every
     * call to the mapper. The returned list is immutable.
     * <p>
     * This method can be used to read a list of Vec into a list - eg.
     * {@code read(buffer, 2, Vec.I2::new);}. The stride can be higher than the
     * component count of the Vec to read interleaved data.
     *
     * @param <T> mapped type
     * @param buffer data to read from
     * @param stride size of array / distance between data values
     * @param mapper function to create T from double array
     * @return list of T
     */
    public static <T> List<T> read(IntBuffer buffer, int stride,
            Function<int[], T> mapper) {
        if (stride < 1) {
            throw new IllegalArgumentException("Stride must be greater than zero");
        }
        if (buffer.remaining() < stride) {
            return List.of();
        }
        int[] holder = new int[stride];
        List<T> list = new ArrayList<>(buffer.remaining() / stride);
        while (buffer.remaining() >= stride) {
            buffer.get(holder);
            list.add(mapper.apply(holder));
        }
        return List.copyOf(list);
    }

    /**
     * Transform a {@link DoubleBuffer} in sections by repeatably reading into
     * an array of size stride, passing to the transformer function, and writing
     * back the result.
     *
     * @param buffer buffer
     * @param stride array size
     * @param transformer transform function accepting array of size stride
     */
    public static void transform(DoubleBuffer buffer, int stride,
            Consumer<double[]> transformer) {
        if (stride < 1) {
            throw new IllegalArgumentException("Stride must be greater than zero");
        }
        double[] holder = new double[stride];
        int position = buffer.position();
        int limit = buffer.limit();
        for (; position + stride < limit; position += stride) {
            buffer.get(position, holder);
            transformer.accept(holder);
            buffer.put(position, holder);
        }
        buffer.position(position);
    }

    /**
     * Transform an {@link IntBuffer} in sections by repeatably reading into an
     * array of size stride, passing to the transformer function, and writing
     * back the result.
     *
     * @param buffer buffer
     * @param stride array size
     * @param transformer transform function accepting array of size stride
     */
    public static void transform(IntBuffer buffer, int stride,
            Consumer<int[]> transformer) {
        if (stride < 1) {
            throw new IllegalArgumentException("Stride must be greater than zero");
        }
        int[] holder = new int[stride];
        int position = buffer.position();
        int limit = buffer.limit();
        for (; position + stride < limit; position += stride) {
            buffer.get(position, holder);
            transformer.accept(holder);
            buffer.put(position, holder);
        }
        buffer.position(position);
    }

    /**
     * A Vec of two doubles.
     */
    public static record D2(double x, double y) implements Vec {

        /**
         * Create a D2 from an array of values, where {code x = data[0]; y =
         * data[1]}. The data array must contain at least two values. Additional
         * values are ignored.
         *
         * @param data array
         * @throws IndexOutOfBoundsException if array is too small
         */
        public D2(double[] data) {
            this(data[0], data[1]);
        }

    }

    /**
     * A Vec of three doubles.
     */
    public static record D3(double x, double y, double z) implements Vec {

        /**
         * Create a D3 from an array of values, where {code x = data[0]; y =
         * data[1]; z = data[2]}. The data array must contain at least three
         * values. Additional values are ignored.
         *
         * @param data array
         * @throws IndexOutOfBoundsException if array is too small
         */
        public D3(double[] data) {
            this(data[0], data[1], data[2]);
        }

    }

    /**
     * A Vec of four doubles.
     */
    public static record D4(double x, double y, double z, double w) implements Vec {

        /**
         * Create a D4 from an array of values, where {code x = data[0]; y =
         * data[1]; z = data[2]; w = data[3]}. The data array must contain at
         * least four values. Additional values are ignored.
         *
         * @param data array
         * @throws IndexOutOfBoundsException if array is too small
         */
        public D4(double[] data) {
            this(data[0], data[1], data[2], data[3]);
        }
    }

    /**
     * A Vec of two integers.
     */
    public static record I2(int x, int y) implements Vec {

        /**
         * Create a I2 from an array of values, where {code x = data[0]; y =
         * data[1]}. The data array must contain at least two values. Additional
         * values are ignored.
         *
         * @param data array
         * @throws IndexOutOfBoundsException if array is too small
         */
        public I2(int[] data) {
            this(data[0], data[1]);
        }

    }

    /**
     * A Vec of three integers.
     */
    public static record I3(int x, int y, int z) implements Vec {

        /**
         * Create a I3 from an array of values, where {code x = data[0]; y =
         * data[1]; z = data[2]}. The data array must contain at least three
         * values. Additional values are ignored.
         *
         * @param data array
         * @throws IndexOutOfBoundsException if array is too small
         */
        public I3(int[] data) {
            this(data[0], data[1], data[2]);
        }

    }

    /**
     * A Vec of four integers.
     */
    public static record I4(int x, int y, int z, int w) implements Vec {

        /**
         * Create a I4 from an array of values, where {code x = data[0]; y =
         * data[1]; z = data[2]; w = data[3]}. The data array must contain at
         * least four values. Additional values are ignored.
         *
         * @param data array
         * @throws IndexOutOfBoundsException if array is too small
         */
        public I4(int[] data) {
            this(data[0], data[1], data[2], data[3]);
        }
    }

    /**
     * Mutable buffer for vector types wrapping a double array. Buffers can be
     * converted to and from lists of the equivalent Vec record types. The
     * element size of a buffer corresponds to the number of components in the
     * corresponding Vec type. Maximum capacity and count are given in terms of
     * number of elements. Various generator, iterator and transform functions
     * allow for in-place calculations via an element data holder.
     */
    public static sealed abstract class BufD {

        final double[] data;
        final int elementSize;
        final int capacity;

        int count;

        BufD(int elementSize, int capacity) {
            this.data = new double[elementSize * capacity];
            this.elementSize = elementSize;
            this.capacity = capacity;
            count = 0;
        }

        /**
         * Maximum capacity of the buffer in elements.
         *
         * @return maximum capacity
         */
        public final int capacity() {
            return capacity;
        }

        /**
         * Count of elements written to the buffer.
         *
         * @return element count
         */
        public final int count() {
            return count;
        }

        /**
         * Explicitly set the element count. The count must not be negative or
         * greater than the capacity.
         *
         * @param count element count
         */
        public final void count(int count) {
            if (count < 0 || count > capacity) {
                throw new IllegalArgumentException("Invalid count");
            }
            this.count = count;
        }

        /**
         * The element size of the corresponding Vec type.
         *
         * @return element size
         */
        public final int elementSize() {
            return elementSize;
        }

        /**
         * Access the underlying data array. Modifications to the buffer will be
         * reflected in the array, and vice-versa. The array is of length
         * {@code capacity() * elementSize()}.
         *
         * @return data array
         */
        public final double[] data() {
            return data;
        }

        /**
         * Reset the count to zero. The underlying array data is not modified.
         */
        public final void clear() {
            count = 0;
        }

        /**
         * Call the provided action for each element in the buffer. A single
         * {@link Data} holder is used across every call. The action will be
         * called {@link #count()} times.
         *
         * @param action action to perform for each element
         */
        public final void forEach(Consumer<Data> action) {
            forEachIndexed((d, idx) -> action.accept(d));
        }

        /**
         * Call the provided action for each element in the buffer. A single
         * {@link Data} holder is used across every call. The action will be
         * called {@link #count()} times. The index will be incremented for each
         * call, starting at zero.
         *
         * @param action action to perform for each element
         */
        public abstract void forEachIndexed(ObjIntConsumer<Data> action);

        /**
         * Generate elements to add to the buffer by calling the provided
         * generator function. A single {@link Data} holder is used across every
         * call to the generator. Elements are appended to those currently in
         * the buffer. There must be enough space between count and capacity to
         * fit in the provided amount of elements.
         *
         * @param amount number of elements to append
         * @param generator function to generate each element
         * @throws IndexOutOfBoundsException if there is not enough space in the
         * buffer
         */
        public final void generate(int amount, Consumer<Data> generator) {
            generateIndexed(amount, (d, idx) -> generator.accept(d));
        }

        /**
         * Generate elements to add to the buffer by calling the provided
         * generator function. A single {@link Data} holder is used across every
         * call to the generator. Elements are appended to those currently in
         * the buffer. There must be enough space between count and capacity to
         * fit in the provided amount of elements. The index will be incremented
         * for each call, starting at zero.
         *
         * @param amount number of elements to append
         * @param generator function to generate each element
         * @throws IndexOutOfBoundsException if there is not enough space in the
         * buffer
         */
        public abstract void generateIndexed(int amount, ObjIntConsumer<Data> generator);

        /**
         * Merge elements from another buffer into this one using the provided
         * mapper function. The mapper function is provided with the destination
         * data from this buffer and the corresponding element of the source
         * buffer, in that order. The destination data will be written back to
         * this buffer. The buffers may be of different element sizes. The count
         * of merged elements will be the lower of this buffer count and the
         * source buffer count.
         *
         * @param srcBuffer source buffer
         * @param mapper function accepting element data from this buffer and
         * the corresponding element of the source buffer
         * @return count of merged elements
         */
        public int merge(BufD srcBuffer, BiConsumer<Data, Data> mapper) {
            int mergeCount = Math.min(srcBuffer.count, count);
            if (mergeCount < 1) {
                return 0;
            }
            Data src = new Data();
            Data dst = new Data();
            double[] srcArray = srcBuffer.data();
            int srcSize = srcBuffer.elementSize();
            int dstSize = elementSize();
            for (int i = 0, srcPos = 0, dstPos = 0; i < mergeCount; i++) {
                src.x = srcArray[srcPos];
                src.y = srcArray[srcPos + 1];
                src.z = srcSize > 2 ? srcArray[srcPos + 2] : 0;
                src.w = srcSize > 3 ? srcArray[srcPos + 3] : 0;
                dst.x = data[dstPos];
                dst.y = data[dstPos + 1];
                dst.z = dstSize > 2 ? data[dstPos + 2] : 0;
                dst.w = dstSize > 3 ? data[dstPos + 3] : 0;
                mapper.accept(dst, src);
                switch (dstSize) {
                    case 2 -> {
                        data[dstPos] = dst.x;
                        data[dstPos + 1] = dst.y;
                    }
                    case 3 -> {
                        data[dstPos] = dst.x;
                        data[dstPos + 1] = dst.y;
                        data[dstPos + 2] = dst.z;
                    }
                    case 4 -> {
                        data[dstPos] = dst.x;
                        data[dstPos + 1] = dst.y;
                        data[dstPos + 2] = dst.z;
                        data[dstPos + 3] = dst.w;
                    }
                }
                srcPos += srcSize;
                dstPos += dstSize;
            }
            return mergeCount;
        }

        /**
         * Modify the buffer by calling the provided function for each element.
         * A single {@link Data} holder is used across every call. Modifications
         * to the data overwrite the element in the buffer. The transformer will
         * be called {@link #count()} times.
         *
         * @param transformer function to transform each element
         */
        public final void transform(Consumer<Data> transformer) {
            transformIndexed((d, idx) -> transformer.accept(d));
        }

        /**
         * Modify the buffer by calling the provided function for each element.
         * A single {@link Data} holder is used across every call. Modifications
         * to the data overwrite the element in the buffer. The transformer will
         * be called {@link #count()} times. The index will be incremented for
         * each call, starting at zero.
         *
         * @param transformer function to transform each element
         */
        public abstract void transformIndexed(ObjIntConsumer<Data> transformer);

        /**
         * Write the data into the provided {@link DoubleBuffer}. The data will
         * be written at the destination's position, and the destination must
         * have enough remaining space to write {code count() * elementSize()}
         * values.
         *
         * @param buffer destination
         * @throws BufferOverflowException if there is insufficient space in the
         * destination
         */
        public void writeTo(DoubleBuffer buffer) {
            buffer.put(data, 0, count * elementSize);
        }

        /**
         * Data holder used for modifying element data in buffers.
         */
        public static final class Data {

            public double x, y, z, w;

            private Data() {

            }

        }

    }

    /**
     * Buffer holding elements of two doubles, corresponding to {@link Vec.D2}.
     */
    public static final class BufD2 extends BufD {

        private BufD2(int capacity) {
            super(2, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufD2 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                transformer.accept(holder, i);
                pos += 2;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 2; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                transformer.accept(holder, i);
                data[pos++] = holder.x;
                data[pos++] = holder.y;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                transformer.accept(holder, i);
                data[pos] = holder.x;
                data[pos + 1] = holder.y;
                pos += 2;
            }
        }

        /**
         * Create a list of {@link Vec.D2} from the elements in this buffer.
         *
         * @return list of Vec.D2
         */
        public final List<D2> toVecList() {
            List<D2> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new D2(d.x, d.y));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufD2 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 2, copyCount * 2);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.D2} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(D2 vec) {
            int pos = count * 2;
            data[pos] = vec.x();
            data[pos + 1] = vec.y();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.D2} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<D2> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufD2 allocate(int capacity) {
            return new BufD2(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.D2}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufD2 fromVecList(List<D2> vecs) {
            BufD2 buffer = new BufD2(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Buffer holding elements of three doubles, corresponding to
     * {@link Vec.D3}.
     */
    public static final class BufD3 extends BufD {

        private BufD3(int capacity) {
            super(3, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufD3 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                transformer.accept(holder, i);
                pos += 3;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 3; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                holder.z = 0;
                transformer.accept(holder, i);
                data[pos++] = holder.x;
                data[pos++] = holder.y;
                data[pos++] = holder.z;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                transformer.accept(holder, i);
                data[pos] = holder.x;
                data[pos + 1] = holder.y;
                data[pos + 2] = holder.z;
                pos += 3;
            }
        }

        /**
         * Create a list of {@link Vec.D3} from the elements in this buffer.
         *
         * @return list of Vec.D3
         */
        public final List<D3> toVecList() {
            List<D3> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new D3(d.x, d.y, d.z));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufD3 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 3, copyCount * 3);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.D3} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(D3 vec) {
            int pos = count * 3;
            data[pos] = vec.x();
            data[pos + 1] = vec.y();
            data[pos + 2] = vec.z();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.D3} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<D3> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufD3 allocate(int capacity) {
            return new BufD3(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.D3}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufD3 fromVecList(List<D3> vecs) {
            BufD3 buffer = new BufD3(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Buffer holding elements of four doubles, corresponding to {@link Vec.D4}.
     */
    public static final class BufD4 extends BufD {

        private BufD4(int capacity) {
            super(4, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufD4 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                holder.w = data[pos + 3];
                transformer.accept(holder, i);
                pos += 4;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 4; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                holder.z = 0;
                holder.w = 0;
                transformer.accept(holder, i);
                data[pos++] = holder.x;
                data[pos++] = holder.y;
                data[pos++] = holder.z;
                data[pos++] = holder.w;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                holder.w = data[pos + 3];
                transformer.accept(holder, i);
                data[pos] = holder.x;
                data[pos + 1] = holder.y;
                data[pos + 2] = holder.z;
                data[pos + 3] = holder.w;
                pos += 4;
            }
        }

        /**
         * Create a list of {@link Vec.D4} from the elements in this buffer.
         *
         * @return list of Vec.D4
         */
        public final List<D4> toVecList() {
            List<D4> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new D4(d.x, d.y, d.z, d.w));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufD4 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 4, copyCount * 4);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.D4} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(D4 vec) {
            int pos = count * 4;
            data[pos] = vec.x();
            data[pos + 1] = vec.y();
            data[pos + 2] = vec.z();
            data[pos + 3] = vec.w();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.D4} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<D4> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufD4 allocate(int capacity) {
            return new BufD4(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.D4}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufD4 fromVecList(List<D4> vecs) {
            BufD4 buffer = new BufD4(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Mutable buffer for vector types wrapping a float array. Buffers can be
     * converted to and from lists of the equivalent VecD record types. The
     * element size of a buffer corresponds to the number of components in the
     * corresponding VecD type. Maximum capacity and count are given in terms of
     * number of elements. Various generator, iterator and transform functions
     * allow for in-place calculations via an element data holder.
     * <p>
     * The buffer data is stored as 32-bit single precision floating point
     * values for situations where data in that format is required. The
     * corresponding record types and data holder use double precision values.
     * Some precision will be lost when storing these values in a BufF buffer.
     */
    public static sealed abstract class BufF {

        final float[] data;
        final int elementSize;
        final int capacity;

        int count;

        BufF(int elementSize, int capacity) {
            this.data = new float[elementSize * capacity];
            this.elementSize = elementSize;
            this.capacity = capacity;
            count = 0;
        }

        /**
         * Maximum capacity of the buffer in elements.
         *
         * @return maximum capacity
         */
        public final int capacity() {
            return capacity;
        }

        /**
         * Count of elements written to the buffer.
         *
         * @return element count
         */
        public final int count() {
            return count;
        }

        /**
         * Explicitly set the element count. The count must not be negative or
         * greater than the capacity.
         *
         * @param count element count
         */
        public final void count(int count) {
            if (count < 0 || count > capacity) {
                throw new IllegalArgumentException("Invalid count");
            }
            this.count = count;
        }

        /**
         * The element size of the corresponding Vec type.
         *
         * @return element size
         */
        public final int elementSize() {
            return elementSize;
        }

        /**
         * Access the underlying data array. Modifications to the buffer will be
         * reflected in the array, and vice-versa. The array is of length
         * {@code capacity() * elementSize()}.
         *
         * @return data array
         */
        public final float[] data() {
            return data;
        }

        /**
         * Reset the count to zero. The underlying array data is not modified.
         */
        public final void clear() {
            count = 0;
        }

        /**
         * Call the provided action for each element in the buffer. A single
         * {@link Data} holder is used across every call. The action will be
         * called {@link #count()} times.
         *
         * @param action action to perform for each element
         */
        public final void forEach(Consumer<Data> action) {
            forEachIndexed((d, idx) -> action.accept(d));
        }

        /**
         * Call the provided action for each element in the buffer. A single
         * {@link Data} holder is used across every call. The action will be
         * called {@link #count()} times. The index will be incremented for each
         * call, starting at zero.
         *
         * @param action action to perform for each element
         */
        public abstract void forEachIndexed(ObjIntConsumer<Data> action);

        /**
         * Generate elements to add to the buffer by calling the provided
         * generator function. A single {@link Data} holder is used across every
         * call to the generator. Elements are appended to those currently in
         * the buffer. There must be enough space between count and capacity to
         * fit in the provided amount of elements.
         *
         * @param amount number of elements to append
         * @param generator function to generate each element
         * @throws IndexOutOfBoundsException if there is not enough space in the
         * buffer
         */
        public final void generate(int amount, Consumer<Data> generator) {
            generateIndexed(amount, (d, idx) -> generator.accept(d));
        }

        /**
         * Generate elements to add to the buffer by calling the provided
         * generator function. A single {@link Data} holder is used across every
         * call to the generator. Elements are appended to those currently in
         * the buffer. There must be enough space between count and capacity to
         * fit in the provided amount of elements. The index will be incremented
         * for each call, starting at zero.
         *
         * @param amount number of elements to append
         * @param generator function to generate each element
         * @throws IndexOutOfBoundsException if there is not enough space in the
         * buffer
         */
        public abstract void generateIndexed(int amount, ObjIntConsumer<Data> generator);

        /**
         * Merge elements from another buffer into this one using the provided
         * mapper function. The mapper function is provided with the destination
         * data from this buffer and the corresponding element of the source
         * buffer, in that order. The destination data will be written back to
         * this buffer. The buffers may be of different element sizes. The count
         * of merged elements will be the lower of this buffer count and the
         * source buffer count.
         *
         * @param srcBuffer source buffer
         * @param mapper function accepting element data from this buffer and
         * the corresponding element of the source buffer
         * @return count of merged elements
         */
        public int merge(BufF srcBuffer, BiConsumer<Data, Data> mapper) {
            int mergeCount = Math.min(srcBuffer.count, count);
            if (mergeCount < 1) {
                return 0;
            }
            Data src = new Data();
            Data dst = new Data();
            float[] srcArray = srcBuffer.data();
            int srcSize = srcBuffer.elementSize();
            int dstSize = elementSize();
            for (int i = 0, srcPos = 0, dstPos = 0; i < mergeCount; i++) {
                src.x = srcArray[srcPos];
                src.y = srcArray[srcPos + 1];
                src.z = srcSize > 2 ? srcArray[srcPos + 2] : 0;
                src.w = srcSize > 3 ? srcArray[srcPos + 3] : 0;
                dst.x = data[dstPos];
                dst.y = data[dstPos + 1];
                dst.z = dstSize > 2 ? data[dstPos + 2] : 0;
                dst.w = dstSize > 3 ? data[dstPos + 3] : 0;
                mapper.accept(dst, src);
                switch (dstSize) {
                    case 2 -> {
                        data[dstPos] = (float) dst.x;
                        data[dstPos + 1] = (float) dst.y;
                    }
                    case 3 -> {
                        data[dstPos] = (float) dst.x;
                        data[dstPos + 1] = (float) dst.y;
                        data[dstPos + 2] = (float) dst.z;
                    }
                    case 4 -> {
                        data[dstPos] = (float) dst.x;
                        data[dstPos + 1] = (float) dst.y;
                        data[dstPos + 2] = (float) dst.z;
                        data[dstPos + 3] = (float) dst.w;
                    }
                }
                srcPos += srcSize;
                dstPos += dstSize;
            }
            return mergeCount;
        }

        /**
         * Modify the buffer by calling the provided function for each element.
         * A single {@link Data} holder is used across every call. Modifications
         * to the data overwrite the element in the buffer. The transformer will
         * be called {@link #count()} times.
         *
         * @param transformer function to transform each element
         */
        public final void transform(Consumer<Data> transformer) {
            transformIndexed((d, idx) -> transformer.accept(d));
        }

        /**
         * Modify the buffer by calling the provided function for each element.
         * A single {@link Data} holder is used across every call. Modifications
         * to the data overwrite the element in the buffer. The transformer will
         * be called {@link #count()} times. The index will be incremented for
         * each call, starting at zero.
         *
         * @param transformer function to transform each element
         */
        public abstract void transformIndexed(ObjIntConsumer<Data> transformer);

        /**
         * Write the data into the provided {@link FloatBuffer}. The data will
         * be written at the destination's position, and the destination must
         * have enough remaining space to write {code count() * elementSize()}
         * values.
         *
         * @param buffer destination
         * @throws BufferOverflowException if there is insufficient space in the
         * destination
         */
        public void writeTo(FloatBuffer buffer) {
            buffer.put(data, 0, count * elementSize);
        }

        /**
         * Data holder used for modifying element data in buffers.
         */
        public static final class Data {

            public double x, y, z, w;

            private Data() {

            }

        }

    }

    /**
     * Buffer holding elements of two floats, a single precision equivalent to
     * {@link Vec.D2}.
     */
    public static final class BufF2 extends BufF {

        private BufF2(int capacity) {
            super(2, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufF2 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                transformer.accept(holder, i);
                pos += 2;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 2; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                transformer.accept(holder, i);
                data[pos++] = (float) holder.x;
                data[pos++] = (float) holder.y;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                transformer.accept(holder, i);
                data[pos] = (float) holder.x;
                data[pos + 1] = (float) holder.y;
                pos += 2;
            }
        }

        /**
         * Create a list of {@link Vec.D2} from the elements in this buffer.
         *
         * @return list of Vec.D2
         */
        public final List<D2> toVecList() {
            List<D2> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new D2(d.x, d.y));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufF2 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 2, copyCount * 2);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.D2} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(D2 vec) {
            int pos = count * 2;
            data[pos] = (float) vec.x();
            data[pos + 1] = (float) vec.y();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.D2} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<D2> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufF2 allocate(int capacity) {
            return new BufF2(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.D2}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufF2 fromVecList(List<D2> vecs) {
            BufF2 buffer = new BufF2(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Buffer holding elements of three floats, a single precision equivalent to
     * {@link Vec.D3}.
     */
    public static final class BufF3 extends BufF {

        private BufF3(int capacity) {
            super(3, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufF3 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                transformer.accept(holder, i);
                pos += 3;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 3; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                holder.z = 0;
                transformer.accept(holder, i);
                data[pos++] = (float) holder.x;
                data[pos++] = (float) holder.y;
                data[pos++] = (float) holder.z;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                transformer.accept(holder, i);
                data[pos] = (float) holder.x;
                data[pos + 1] = (float) holder.y;
                data[pos + 2] = (float) holder.z;
                pos += 3;
            }
        }

        /**
         * Create a list of {@link Vec.D3} from the elements in this buffer.
         *
         * @return list of Vec.D3
         */
        public final List<D3> toVecList() {
            List<D3> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new D3(d.x, d.y, d.z));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufF3 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 3, copyCount * 3);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.D3} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(D3 vec) {
            int pos = count * 3;
            data[pos] = (float) vec.x();
            data[pos + 1] = (float) vec.y();
            data[pos + 2] = (float) vec.z();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.D3} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<D3> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufF3 allocate(int capacity) {
            return new BufF3(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.D3}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufF3 fromVecList(List<D3> vecs) {
            BufF3 buffer = new BufF3(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Buffer holding elements of four floats, a single precision equivalent to
     * {@link Vec.D4}.
     */
    public static final class BufF4 extends BufF {

        private BufF4(int capacity) {
            super(4, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufF4 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                holder.w = data[pos + 3];
                transformer.accept(holder, i);
                pos += 4;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 4; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                holder.z = 0;
                holder.w = 0;
                transformer.accept(holder, i);
                data[pos++] = (float) holder.x;
                data[pos++] = (float) holder.y;
                data[pos++] = (float) holder.z;
                data[pos++] = (float) holder.w;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                holder.w = data[pos + 3];
                transformer.accept(holder, i);
                data[pos] = (float) holder.x;
                data[pos + 1] = (float) holder.y;
                data[pos + 2] = (float) holder.z;
                data[pos + 3] = (float) holder.w;
                pos += 4;
            }
        }

        /**
         * Create a list of {@link Vec.D4} from the elements in this buffer.
         *
         * @return list of Vec.D4
         */
        public final List<D4> toVecList() {
            List<D4> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new D4(d.x, d.y, d.z, d.w));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufF4 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 4, copyCount * 4);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.D4} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(D4 vec) {
            int pos = count * 4;
            data[pos] = (float) vec.x();
            data[pos + 1] = (float) vec.y();
            data[pos + 2] = (float) vec.z();
            data[pos + 3] = (float) vec.w();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.D4} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<D4> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufF4 allocate(int capacity) {
            return new BufF4(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.D4}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufF4 fromVecList(List<D4> vecs) {
            BufF4 buffer = new BufF4(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Mutable buffer for vector types wrapping an integer array. Buffers can be
     * converted to and from lists of the equivalent Vec record types. The
     * element size of a buffer corresponds to the number of components in the
     * corresponding Vec type. Maximum capacity and count are given in terms of
     * number of elements. Various generator, iterator and transform functions
     * allow for in-place calculations via an element data holder.
     */
    public static sealed abstract class BufI {

        final int[] data;
        final int elementSize;
        final int capacity;

        int count;

        BufI(int elementSize, int capacity) {
            this.data = new int[elementSize * capacity];
            this.elementSize = elementSize;
            this.capacity = capacity;
            count = 0;
        }

        /**
         * Maximum capacity of the buffer in elements.
         *
         * @return maximum capacity
         */
        public final int capacity() {
            return capacity;
        }

        /**
         * Count of elements written to the buffer.
         *
         * @return element count
         */
        public final int count() {
            return count;
        }

        /**
         * Explicitly set the element count. The count must not be negative or
         * greater than the capacity.
         *
         * @param count element count
         */
        public final void count(int count) {
            if (count < 0 || count > capacity) {
                throw new IllegalArgumentException("Invalid count");
            }
            this.count = count;
        }

        /**
         * The element size of the corresponding Vec type.
         *
         * @return element size
         */
        public final int elementSize() {
            return elementSize;
        }

        /**
         * Access the underlying data array. Modifications to the buffer will be
         * reflected in the array, and vice-versa. The array is of length
         * {@code capacity() * elementSize()}.
         *
         * @return data array
         */
        public final int[] data() {
            return data;
        }

        /**
         * Reset the count to zero. The underlying array data is not modified.
         */
        public final void clear() {
            count = 0;
        }

        /**
         * Call the provided action for each element in the buffer. A single
         * {@link Data} holder is used across every call. The action will be
         * called {@link #count()} times.
         *
         * @param action action to perform for each element
         */
        public final void forEach(Consumer<Data> action) {
            forEachIndexed((d, idx) -> action.accept(d));
        }

        /**
         * Call the provided action for each element in the buffer. A single
         * {@link Data} holder is used across every call. The action will be
         * called {@link #count()} times. The index will be incremented for each
         * call, starting at zero.
         *
         * @param action action to perform for each element
         */
        public abstract void forEachIndexed(ObjIntConsumer<Data> action);

        /**
         * Generate elements to add to the buffer by calling the provided
         * generator function. A single {@link Data} holder is used across every
         * call to the generator. Elements are appended to those currently in
         * the buffer. There must be enough space between count and capacity to
         * fit in the provided amount of elements.
         *
         * @param amount number of elements to append
         * @param generator function to generate each element
         * @throws IndexOutOfBoundsException if there is not enough space in the
         * buffer
         */
        public final void generate(int amount, Consumer<Data> generator) {
            generateIndexed(amount, (d, idx) -> generator.accept(d));
        }

        /**
         * Generate elements to add to the buffer by calling the provided
         * generator function. A single {@link Data} holder is used across every
         * call to the generator. Elements are appended to those currently in
         * the buffer. There must be enough space between count and capacity to
         * fit in the provided amount of elements. The index will be incremented
         * for each call, starting at zero.
         *
         * @param amount number of elements to append
         * @param generator function to generate each element
         * @throws IndexOutOfBoundsException if there is not enough space in the
         * buffer
         */
        public abstract void generateIndexed(int amount, ObjIntConsumer<Data> generator);

        /**
         * Merge elements from another buffer into this one using the provided
         * mapper function. The mapper function is provided with the destination
         * data from this buffer and the corresponding element of the source
         * buffer, in that order. The destination data will be written back to
         * this buffer. The buffers may be of different element sizes. The count
         * of merged elements will be the lower of this buffer count and the
         * source buffer count.
         *
         * @param srcBuffer source buffer
         * @param mapper function accepting element data from this buffer and
         * the corresponding element of the source buffer
         * @return count of merged elements
         */
        public int merge(BufI srcBuffer, BiConsumer<Data, Data> mapper) {
            int mergeCount = Math.min(srcBuffer.count, count);
            if (mergeCount < 1) {
                return 0;
            }
            Data src = new Data();
            Data dst = new Data();
            int[] srcArray = srcBuffer.data();
            int srcSize = srcBuffer.elementSize();
            int dstSize = elementSize();
            for (int i = 0, srcPos = 0, dstPos = 0; i < mergeCount; i++) {
                src.x = srcArray[srcPos];
                src.y = srcArray[srcPos + 1];
                src.z = srcSize > 2 ? srcArray[srcPos + 2] : 0;
                src.w = srcSize > 3 ? srcArray[srcPos + 3] : 0;
                dst.x = data[dstPos];
                dst.y = data[dstPos + 1];
                dst.z = dstSize > 2 ? data[dstPos + 2] : 0;
                dst.w = dstSize > 3 ? data[dstPos + 3] : 0;
                mapper.accept(dst, src);
                switch (dstSize) {
                    case 2 -> {
                        data[dstPos] = dst.x;
                        data[dstPos + 1] = dst.y;
                    }
                    case 3 -> {
                        data[dstPos] = dst.x;
                        data[dstPos + 1] = dst.y;
                        data[dstPos + 2] = dst.z;
                    }
                    case 4 -> {
                        data[dstPos] = dst.x;
                        data[dstPos + 1] = dst.y;
                        data[dstPos + 2] = dst.z;
                        data[dstPos + 3] = dst.w;
                    }
                }
                srcPos += srcSize;
                dstPos += dstSize;
            }
            return mergeCount;
        }

        /**
         * Modify the buffer by calling the provided function for each element.
         * A single {@link Data} holder is used across every call. Modifications
         * to the data overwrite the element in the buffer. The transformer will
         * be called {@link #count()} times.
         *
         * @param transformer function to transform each element
         */
        public final void transform(Consumer<Data> transformer) {
            transformIndexed((d, idx) -> transformer.accept(d));
        }

        /**
         * Modify the buffer by calling the provided function for each element.
         * A single {@link Data} holder is used across every call. Modifications
         * to the data overwrite the element in the buffer. The transformer will
         * be called {@link #count()} times. The index will be incremented for
         * each call, starting at zero.
         *
         * @param transformer function to transform each element
         */
        public abstract void transformIndexed(ObjIntConsumer<Data> transformer);

        /**
         * Write the data into the provided {@link IntBuffer}. The data will be
         * written at the destination's position, and the destination must have
         * enough remaining space to write {code count() * elementSize()}
         * values.
         *
         * @param buffer destination
         * @throws BufferOverflowException if there is insufficient space in the
         * destination
         */
        public void writeTo(IntBuffer buffer) {
            buffer.put(data, 0, count * elementSize);
        }

        /**
         * Data holder used for modifying element data in buffers.
         */
        public static final class Data {

            public int x, y, z, w;

            private Data() {

            }

        }

    }

    /**
     * Buffer holding elements of two doubles, corresponding to {@link Vec.D2}.
     */
    public static final class BufI2 extends BufI {

        private BufI2(int capacity) {
            super(2, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufI2 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                transformer.accept(holder, i);
                pos += 2;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 2; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                transformer.accept(holder, i);
                data[pos++] = holder.x;
                data[pos++] = holder.y;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                transformer.accept(holder, i);
                data[pos] = holder.x;
                data[pos + 1] = holder.y;
                pos += 2;
            }
        }

        /**
         * Create a list of {@link Vec.I2} from the elements in this buffer.
         *
         * @return list of Vec.I2
         */
        public final List<I2> toVecList() {
            List<I2> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new I2(d.x, d.y));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufI2 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 2, copyCount * 2);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.I2} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(I2 vec) {
            int pos = count * 2;
            data[pos] = vec.x();
            data[pos + 1] = vec.y();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.I2} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<I2> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufI2 allocate(int capacity) {
            return new BufI2(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.I2}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufI2 fromVecList(List<I2> vecs) {
            BufI2 buffer = new BufI2(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Buffer holding elements of three doubles, corresponding to
     * {@link Vec.I3}.
     */
    public static final class BufI3 extends BufI {

        private BufI3(int capacity) {
            super(3, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufI3 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                transformer.accept(holder, i);
                pos += 3;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 3; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                holder.z = 0;
                transformer.accept(holder, i);
                data[pos++] = holder.x;
                data[pos++] = holder.y;
                data[pos++] = holder.z;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                transformer.accept(holder, i);
                data[pos] = holder.x;
                data[pos + 1] = holder.y;
                data[pos + 2] = holder.z;
                pos += 3;
            }
        }

        /**
         * Create a list of {@link Vec.I3} from the elements in this buffer.
         *
         * @return list of Vec.I3
         */
        public final List<I3> toVecList() {
            List<I3> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new I3(d.x, d.y, d.z));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufI3 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 3, copyCount * 3);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.I3} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(I3 vec) {
            int pos = count * 3;
            data[pos] = vec.x();
            data[pos + 1] = vec.y();
            data[pos + 2] = vec.z();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.I3} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<I3> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufI3 allocate(int capacity) {
            return new BufI3(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.I3}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufI3 fromVecList(List<I3> vecs) {
            BufI3 buffer = new BufI3(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

    /**
     * Buffer holding elements of four doubles, corresponding to {@link Vec.I4}.
     */
    public static final class BufI4 extends BufI {

        private BufI4(int capacity) {
            super(4, capacity);
        }

        /**
         * Copy the provided buffer into this buffer. This buffer is first
         * cleared. This buffer must have the capacity to store the count of
         * elements in the provided buffer.
         *
         * @param buffer buffer to copy from
         * @throws IndexOutOfBoundsException if not enough capacity
         */
        public void copy(BufI4 buffer) {
            if (buffer.count() > capacity) {
                throw new IndexOutOfBoundsException("Not enough capacity");
            }
            clear();
            add(buffer);
        }

        @Override
        public void forEachIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                holder.w = data[pos + 3];
                transformer.accept(holder, i);
                pos += 4;
            }
        }

        @Override
        public void generateIndexed(int amount, ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            int space = capacity - count;
            if (amount > space) {
                throw new IndexOutOfBoundsException("Not enough remaining space");
            }
            for (int i = 0, pos = count * 4; i < amount; i++) {
                holder.x = 0;
                holder.y = 0;
                holder.z = 0;
                holder.w = 0;
                transformer.accept(holder, i);
                data[pos++] = holder.x;
                data[pos++] = holder.y;
                data[pos++] = holder.z;
                data[pos++] = holder.w;
                count++;
            }
        }

        @Override
        public void transformIndexed(ObjIntConsumer<Data> transformer) {
            Data holder = new Data();
            for (int i = 0, pos = 0; i < count; i++) {
                holder.x = data[pos];
                holder.y = data[pos + 1];
                holder.z = data[pos + 2];
                holder.w = data[pos + 3];
                transformer.accept(holder, i);
                data[pos] = holder.x;
                data[pos + 1] = holder.y;
                data[pos + 2] = holder.z;
                data[pos + 3] = holder.w;
                pos += 4;
            }
        }

        /**
         * Create a list of {@link Vec.I4} from the elements in this buffer.
         *
         * @return list of Vec.I4
         */
        public final List<I4> toVecList() {
            List<I4> list = new ArrayList<>(capacity);
            forEach(d -> {
                list.add(new I4(d.x, d.y, d.z, d.w));
            });
            return List.copyOf(list);
        }

        /**
         * Append as many of the provided buffer's elements as will fit into the
         * remaining capacity of this buffer.
         *
         * @param buffer source buffer
         * @return number of appended elements
         */
        public final int add(BufI4 buffer) {
            int remaining = capacity - count;
            int copyCount = Math.min(buffer.count(), remaining);
            System.arraycopy(buffer.data(), 0, data, count * 4, copyCount * 4);
            count += copyCount;
            return copyCount;
        }

        /**
         * Append the provided {@link Vec.I4} into this buffer.
         *
         * @param vec element to add
         */
        public final void add(I4 vec) {
            int pos = count * 4;
            data[pos] = vec.x();
            data[pos + 1] = vec.y();
            data[pos + 2] = vec.z();
            data[pos + 3] = vec.w();
            count++;
        }

        /**
         * Append as many of the provided list of {@link Vec.I4} as will fit
         * into the remaining capacity of this buffer.
         *
         * @param vecs list of elements
         * @return number of appended elements
         */
        public final int add(List<I4> vecs) {
            for (int i = 0; i < vecs.size(); i++) {
                if (count >= capacity) {
                    return i;
                }
                add(vecs.get(i));
            }
            return vecs.size();
        }

        /**
         * Allocate a buffer capable of holding the provided number of elements.
         *
         * @param capacity maximum element capacity
         * @return created buffer
         */
        public static BufI4 allocate(int capacity) {
            return new BufI4(capacity);
        }

        /**
         * Create a buffer from the provided list of {@link Vec.I4}.
         *
         * @param vecs list of elements
         * @return created buffer
         */
        public static BufI4 fromVecList(List<I4> vecs) {
            BufI4 buffer = new BufI4(vecs.size());
            buffer.add(vecs);
            return buffer;
        }

    }

}
