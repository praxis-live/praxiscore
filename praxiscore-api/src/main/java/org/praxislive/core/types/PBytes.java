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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.praxislive.core.Value;
import org.praxislive.core.ValueFormatException;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.DataObject;

/**
 * A value type wrapping an immutable array of bytes. The String representation
 * of a PBytes is Base64 encoded.
 */
public final class PBytes extends Value {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "Bytes";

    /**
     * An empty PBytes.
     */
    public static final PBytes EMPTY = new PBytes(new byte[0], "");

    private final byte[] bytes;
    private String str;

    private PBytes(byte[] bytes, String str) {
        this.bytes = bytes;
        this.str = str;
    }

    @Override
    public String toString() {
        if (str == null) {
            if (bytes.length == 0) {
                str = "";
            } else {
                str = Base64.getMimeEncoder().encodeToString(bytes);
            }
        }
        return str;
    }

    /**
     * Copy the bytes data into a new array.
     *
     * @return copy of data
     */
    public byte[] copyBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Copy a range of the bytes data into a new array.
     *
     * @param from initial index to copy, inclusive
     * @param to final index to copy, exclusive
     * @return copy of data
     * @throws ArrayIndexOutOfBoundsException if {@code from < 0} or
     * {@code from > size()}
     * @throws IllegalArgumentException if {@code from > to}
     */
    public byte[] copyBytes(int from, int to) {
        return Arrays.copyOfRange(bytes, from, to);
    }

    /**
     * Copy the data from this PBytes into the provided array. If the
     * destination is a different size to this PBytes the minimum of
     * {@code dst.length} or {@link #size()} bytes will be written.
     *
     * @param dst destination array
     */
    public void read(byte[] dst) {
        System.arraycopy(bytes, 0, dst, 0, Math.min(bytes.length, dst.length));
    }

    /**
     * Copy the data from this PBytes into the provided array, reading from
     * index in the PBytes data. The data is written into the start of the
     * destination. If the destination is a different size to the available data
     * in this PBytes the minimum of {@code dst.length} or
     * {@code size() - index} will be written.
     *
     * @param index starting read index
     * @param dst destination array
     * @throws IndexOutOfBoundsException if index is less than 0 or greater than
     * size
     */
    public void read(int index, byte[] dst) {
        System.arraycopy(bytes, index, dst, 0, Math.min(bytes.length - index, dst.length));
    }

    /**
     * Copy length amount of data from this PBytes into the provided array,
     * reading from index in the PBytes data and writing from offset in the
     * destination array.
     *
     * @param index starting read index
     * @param dst destination array
     * @param offset starting write index
     * @param length number of bytes to read
     * @throws IndexOutOfBoundsException if the index or offset are out of
     * bounds, or there is not length amount of data to read or capacity to
     * write in the destination.
     */
    public void read(int index, byte[] dst, int offset, int length) {
        System.arraycopy(bytes, index, dst, offset, length);
    }

    /**
     * Create a {@link ByteBuffer} to access the data in this PBytes. The
     * returned buffer is read-only.
     *
     * @return created read-only ByteBuffer
     */
    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    /**
     * Create an {@link InputStream} to read the data from this PBytes.
     *
     * @return created input stream
     */
    public InputStream asInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    /**
     * The size of this PBytes in bytes.
     *
     * @return size in bytes
     */
    public int size() {
        return bytes.length;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PBytes) {
            final PBytes other = (PBytes) obj;
            return Arrays.equals(this.bytes, other.bytes);
        }
        return false;
    }

    @Override
    public boolean equivalent(Value arg) {
        try {
            if (arg == this) {
                return true;
            }
            PBytes other = PBytes.coerce(arg);
            return Arrays.equals(bytes, other.bytes);
        } catch (ValueFormatException ex) {
            return false;
        }
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    /**
     * Extract serialized object from data. Will throw an exception if this
     * PBytes doesn't contain a valid object of the correct type.
     *
     * @param <T>
     * @param type class of expected object
     * @return deserialized object
     * @throws IOException
     */
    @Deprecated(forRemoval = true)
    public <T extends Serializable> T deserialize(Class<T> type) throws IOException {
        ObjectInputStream ois = new ObjectInputStream(asInputStream());
        try {
            Object obj = ois.readObject();
            if (type.isInstance(obj)) {
                return type.cast(obj);
            }
            throw new IOException("PBytes contains a different class");
        } catch (ClassNotFoundException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * Iterate through data by decoding into provided container DataObject and
     * calling consumer. Changes to the container are ignored outside of the
     * consumer.
     *
     * @param <T> DataObject sub-type
     * @param container
     * @param consumer
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public <T extends DataObject> void forEachIn(T container, Consumer<T> consumer) {
        Spliterator<T> splitr = new StreamableSpliterator<>(new ByteArrayInputStream(bytes), () -> container);
        splitr.forEachRemaining(consumer);
    }

    /**
     * Transform data by iterating into provided container and calling provided
     * consumer before writing container into new PBytes
     *
     * @param <T> DataObject sub-type
     * @param container
     * @param transformer
     * @return transformed data
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public <T extends DataObject> PBytes transformIn(T container, Consumer<T> transformer) {
        OutputStream os = new OutputStream(size());
        DataOutputStream dos = new DataOutputStream(os);
        forEachIn(container, s -> {
            transformer.accept(s);
            try {
                s.writeTo(dos);
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        });
        try {
            dos.flush();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
        return os.toBytes();
    }

    /**
     * Create a Stream over the data by decoding into DataObjects provided by
     * supplier
     *
     * @param <T>
     * @param supplier of DataObject
     * @return Stream of DataObject
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public <T extends DataObject> Stream<T> streamOf(Supplier<T> supplier) {
        return isEmpty() ? Stream.empty()
                : StreamSupport.stream(new StreamableSpliterator<>(
                        new ByteArrayInputStream(bytes), supplier), false);
    }

    /**
     * Create a Stream over the data by decoding into count number of
     * DataObjects provided by supplier. Extra DataObjects with default values
     * will be generated if required to reach count.
     *
     * @param <T>
     * @param count
     * @param supplier
     * @return Stream of DataObject
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public <T extends DataObject> Stream<T> streamOf(int count, Supplier<T> supplier) {
        return Stream.concat(streamOf(supplier), Stream.generate(supplier)).limit(count);
    }

    /**
     * Collector to take Stream of DataObject subclasses and write into new
     * PBytes.
     *
     * @param <T>
     * @return collector
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public static <T extends DataObject> Collector<T, ?, PBytes> collector() {
        return new StreamableCollector<>();
    }

    /**
     * Create a PBytes out of the provided byte array. The array will be copied.
     *
     * @param bytes source bytes
     * @return created PBytes
     */
    public static PBytes valueOf(byte[] bytes) {
        return new PBytes(Arrays.copyOf(bytes, bytes.length), null);
    }

    /**
     * Create a PBytes out of a range of the provided byte array. The range will
     * be copied.
     * <p>
     * The range is copied using {@link Arrays#copyOfRange(byte[], int, int)}.
     * The final index may lie outside the provided bytes, in which case range
     * is padded with zeroes.
     *
     * @param bytes source bytes
     * @param from the initial index of the range, inclusive
     * @param to the final index of the range, exclusive.
     * @return created PBytes
     */
    public static PBytes valueOf(byte[] bytes, int from, int to) {
        return new PBytes(Arrays.copyOfRange(bytes, from, to), null);
    }

    /**
     * Create a PBytes from a {@link ByteBuffer}. The resulting PBytes will
     * contain the data from the buffer position to its limit. After this method
     * all the remaining data in the buffer will have been consumed.
     *
     * @param buffer source bytebuffer
     * @return created PBytes
     */
    public static PBytes valueOf(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new PBytes(bytes, null);
    }

    /**
     * Parse the provided String as a PBytes.
     *
     * @param bytestring bytes as string
     * @return created PBytes
     * @throws ValueFormatException if string is invalid
     */
    public static PBytes parse(String bytestring) throws ValueFormatException {
        if (bytestring.trim().isEmpty()) {
            return PBytes.EMPTY;
        }
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(bytestring);
            return new PBytes(bytes, bytestring);
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }
    }

    /**
     * Encode the provided List of DataObject subclasses into a new PBytes
     *
     * @param list
     * @return PBytes of data
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public static PBytes of(List<? extends DataObject> list) {
        if (list.isEmpty()) {
            return PBytes.EMPTY;
        }
        try {
            OutputStream os = new OutputStream();
            DataOutputStream dos = new DataOutputStream(os);
            for (DataObject s : list) {
                s.writeTo(dos);
            }
            dos.flush();
            return os.toBytes();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Create a PBytes of the serialized form of the provided object.
     *
     * @param obj
     * @return PBytes of serialized data
     * @throws IOException
     */
    @Deprecated(forRemoval = true)
    public static PBytes serialize(Serializable obj) throws IOException {
        OutputStream os = new OutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(obj);
            oos.flush();
        }
        return os.toBytes();
    }

    private static PBytes coerce(Value arg) throws ValueFormatException {
        if (arg instanceof PBytes pBytes) {
            return pBytes;
        } else {
            return parse(arg.toString());
        }
    }

    /**
     * Cast or convert the provided value into a PBytes, wrapped in an Optional.
     * If the value is already a PBytes, the Optional will wrap the existing
     * value. If the value is not a PBytes and cannot be converted into one, an
     * empty Optional is returned.
     *
     * @param value value
     * @return optional PBytes
     */
    public static Optional<PBytes> from(Value value) {
        try {
            return Optional.of(coerce(value));
        } catch (ValueFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * Utility method to create an {@link ArgumentInfo} for arguments of type
     * PBytes.
     *
     * @return argument info
     */
    public static ArgumentInfo info() {
        return ArgumentInfo.of(PBytes.class, PMap.EMPTY);
    }

    /**
     * An OutputStream for creating a PBytes.
     */
    public static class OutputStream extends ByteArrayOutputStream {

        /**
         * Create an OutputStream with the default buffer size.
         */
        public OutputStream() {
        }

        /**
         * Create an OutputStream with the provided buffer size.
         *
         * @param size buffer size
         */
        public OutputStream(int size) {
            super(size);
        }

        /**
         * Create a PBytes from the output data. The OutputStream will no longer
         * be usable after this method has been called.
         *
         * @return created PBytes
         */
        public synchronized PBytes toBytes() {
            byte[] bytes = buf;
            int size = count;
            buf = null;
            count = 0;
            if (bytes.length == size) {
                return new PBytes(bytes, null);
            } else {
                return new PBytes(Arrays.copyOf(bytes, size), null);
            }
        }

    }

    private static class DataOutputImpl extends DataOutputStream {

        private final OutputStream out;

        public DataOutputImpl(OutputStream out) {
            super(out);
            this.out = out;
        }

    }

    @SuppressWarnings("removal")
    private static class StreamableCollector<T extends DataObject> implements Collector<T, DataOutputImpl, PBytes> {

        @Override
        public Supplier<DataOutputImpl> supplier() {
            return () -> new DataOutputImpl(new OutputStream());
        }

        @Override
        public BiConsumer<DataOutputImpl, T> accumulator() {
            return (stream, data) -> {
                try {
                    data.writeTo(stream);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            };
        }

        @Override
        public BinaryOperator<DataOutputImpl> combiner() {
            return (stream1, stream2) -> {
                try {
                    stream2.flush();
                    stream2.out.writeTo(stream1);
                    stream2.close();
                    return stream1;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            };
        }

        @Override
        public Function<DataOutputImpl, PBytes> finisher() {
            return stream -> {
                try {
                    stream.flush();
                    stream.close();
                    return stream.out.toBytes();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            };
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.noneOf(Characteristics.class);
        }

    }

    @SuppressWarnings("removal")
    private static class StreamableSpliterator<T extends DataObject> implements Spliterator<T> {

        private final ByteArrayInputStream is;
        private final DataInputStream dis;
        private final Supplier<T> supplier;

        private StreamableSpliterator(ByteArrayInputStream is, Supplier<T> supplier) {
            this.is = is;
            this.dis = new DataInputStream(is);
            this.supplier = supplier;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            try {
                int available = is.available();
                if (available > 0) {
                    T streamable = supplier.get();
                    streamable.readFrom(dis);
                    if (available == is.available()) {
                        throw new IllegalArgumentException("DataObject not reading from data");
                    }
                    action.accept(streamable);
                    return true;
                } else {
                    return false;
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return IMMUTABLE | NONNULL | ORDERED;
        }

    }

}
