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
package org.praxislive.code.userapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.praxislive.code.CodeContext;
import org.praxislive.core.Lookup;

/**
 * Support for creating data pipes to work with data of any type. All data
 * chains are driven by a Data.Sink. Input and output ports of type Data.In and
 * Data.Out can be created. Only pipes and ports of the identical generic type
 * can be connected together.
 */
public class Data {

    private Data() {
    }

    /**
     * Create a pipe that applies the function to every type T passing through.
     * The function may return the supplied input or another instance of type T.
     *
     * @param <T> type of data
     * @param function function to apply to data
     * @return pipe
     */
    public static final <T> Pipe<T> apply(Function<? super T, ? extends T> function) {
        Objects.requireNonNull(function);
        return new Pipe<T>() {
            @Override
            protected void process(List<Packet<T>> data) {
                data.forEach(p -> p.apply(function));
            }
        };
    }

    /**
     * Create a pipe that applies the combiner function to every type T passing
     * through. The function may return the supplied input or another instance
     * of type T. The first argument of the function corresponds to the first
     * source of the pipe, if there is one, else a cleared T. The second
     * argument is a list of T corresponding to any additional sources.
     *
     * @param <T> type of data
     * @param combiner combination function to apply to data
     * @return pipe
     */
    public static final <T> Pipe<T> combine(BiFunction<T, List<T>, ? extends T> combiner) {
        Objects.requireNonNull(combiner);
        return new Pipe<T>() {

            private final List<T> srcs = new ArrayList<>();

            @Override
            protected void process(List<Packet<T>> data) {
                srcs.clear();
                for (int i = 1; i < data.size(); i++) {
                    srcs.add(data.get(i).data());
                }
                data.get(0).apply(dst -> combiner.apply(dst, srcs));
                srcs.clear();
            }

            @Override
            protected void writeOutput(List<Packet<T>> data, Packet<T> output, int sinkIndex) {
                if (data.isEmpty()) {
                    output.clear();
                } else {
                    output.accumulate(List.of(data.get(0)));
                }
            }
        };
    }

    /**
     * Create a pipe that applies the combiner function to every type T passing
     * through. The first argument of the function corresponds to the first
     * source of the pipe, if there is one, else a cleared T. The second
     * argument is a list of T corresponding to any additional sources. The
     * function should combine data into the first argument T, which assumes
     * that T is mutable. To combine into a different instance of T, use
     * {@link #combine(java.util.function.BiFunction)}.
     *
     * @param <T> type of data
     * @param combiner combination function to apply to data
     * @return pipe
     */
    public static final <T> Pipe<T> combineWith(BiConsumer<T, List<T>> combiner) {
        Objects.requireNonNull(combiner);
        return combine((dst, srcs) -> {
            combiner.accept(dst, srcs);
            return dst;
        });
    }

    /**
     * Create a pipe that applies no additional processing to every type T
     * passing through. The pipe will use the clear and accumulate functions
     * defined on the sink.
     * <p>
     * This pipe is useful where you need a placeholder element, a clear source,
     * or to combine sources using the default accumulation.
     *
     * @param <T> type of data
     * @return pipe
     */
    public static final <T> Pipe<T> identity() {
        return new IdentityPipe<>();
    }

    /**
     * Link provided Data.Pipes together.
     *
     * @param <T> common type of data supported by pipes
     * @param pipes pipes to connect
     * @return last pipe, for convenience
     */
    @SafeVarargs
    public static final <T> Pipe<T> link(Pipe<T>... pipes) {
        if (pipes.length < 2) {
            throw new IllegalArgumentException();
        }
        for (int i = pipes.length - 1; i > 0; i--) {
            pipes[i].addSource(pipes[i - 1]);
        }
        return pipes[pipes.length - 1];
    }

    /**
     * Create a pipe that supplies new instances of type T. This pipe does not
     * support sources.
     *
     * @param <T> type of data to supply
     * @param supplier function to supply instance of T
     * @return pipe
     */
    public static final <T> Pipe<T> supply(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return new Pipe<T>() {
            @Override
            protected void process(List<Packet<T>> data) {
                data.forEach(p -> p.apply(t -> supplier.get()));
            }

            @Override
            protected void registerSource(Pipe<T> source) {
                throw new UnsupportedOperationException();
            }

        };
    }

    /**
     * Create a pipe that applies the consumer to every type T passing through.
     * This assumes that either the data type is mutable or that its contents
     * will be used but not changed. To map the type to a different instance of
     * T, use {@link #apply(java.util.function.Function)}.
     *
     * @param <T> type of data
     * @param consumer consumer function to apply to data of type T
     * @return pipe
     */
    public static final <T> Pipe<T> with(Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer);
        return apply(d -> {
            consumer.accept(d);
            return d;
        });
    }

    /**
     * Data sink to drive pipe graph.
     * <p>
     * Use {@code @Inject Sink<TYPE> sink;} to create a sink.
     * <p>
     * By default the sink passes the same instance of T through the pipe graph.
     * To create new type T, accumulate values, validate values, etc. provide
     * the related functions.
     * <p>
     * Use input() to get a Data.Pipe to link to the sink.
     * <p>
     * Use process() every time you want to process a graph of T.
     *
     * @param <T> type of data
     */
    public static abstract class Sink<T> implements Lookup.Provider {

        private final Pipe<T> input;
        private final SinkPacket<T> packet;

        private UnaryOperator<T> creator;
        private UnaryOperator<T> clearer;
        private BinaryOperator<T> accumulator;
        private BiPredicate<T, T> validator;
        private Consumer<T> disposer;

        private long pass;

        public Sink() {
            packet = new SinkPacket<>(this, null);
            defaultFunctions();
            input = new Pipe<T>() {
                @Override
                protected void process(List<Packet<T>> data) {
                }

                @Override
                protected void registerSink(Pipe<T> sink) {
                    throw new UnsupportedOperationException();
                }

                @Override
                protected boolean isOutputRequired(Pipe<T> source, long pass) {
                    return true;
                }

            };
        }

        private void defaultFunctions() {
            creator = UnaryOperator.identity();
            clearer = UnaryOperator.identity();
            accumulator = (dst, src) -> src;
            validator = (dst, src) -> true;
            disposer = t -> {
                if (t instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) t).close();
                    } catch (Exception ex) {
                        log(ex);
                    }
                }
            };
        }

        @Deprecated
        protected void attach(CodeContext<?> context) {
        }

        /**
         * Reset all functions and disconnect all sources.
         */
        public void reset() {
            defaultFunctions();
            input.disconnectSources();
        }

        /**
         * Get the input pipe for this sink. The input pipe only supports the
         * addition of sources - it cannot be used as a source.
         *
         * @return input pipe
         */
        public Pipe<T> input() {
            return input;
        }

        /**
         * Process an instance of type T through the data graph. The data
         * returned may not be the same as the data provided, depending on how
         * you have configured the sink, whether you use Data.supply() /
         * Data.apply(), etc.
         *
         * @param data instance of T to process
         * @return data of type T (may or may not be the input data)
         */
        public T process(T data) {
            packet.data = Objects.requireNonNull(data);
            try {
                if (input.sources.size() == 1) {
                    input.processInPlace(packet, true, ++pass);
                } else {
                    input.processCached(packet, true, ++pass);
                    input.writeOutput(input.dataPackets, packet, 0);
                }
            } catch (Exception ex) {
                log(ex);
            }
            return packet.data;
        }

        /**
         * Function to get an instance of T when a new data packet is being
         * created. This function is not required to return a new instance. The
         * default onCreate function returns the provided value.
         *
         * @param creator function to get an instance of T
         * @return this sink for chaining
         */
        public Sink<T> onCreate(UnaryOperator<T> creator) {
            this.creator = Objects.requireNonNull(creator);
            return this;
        }

        /**
         * Function to clear an instance of T when required, at the head of a
         * pipe chain, prior to accumulation, etc. This might eg. zero out an
         * array or empty a list. The default onClear function does nothing.
         *
         * @param clearer function to clear an instance of T
         * @return this sink for chaining
         */
        public Sink<T> onClear(UnaryOperator<T> clearer) {
            this.clearer = Objects.requireNonNull(clearer);
            return this;
        }

        /**
         * Function to accumulate instances of T. The first argument is the
         * existing destination instance, the second is the source instance. The
         * function may modify and return the existing destination, or return a
         * different instance of T. If a different instance of T is returned,
         * the existing destination will be disposed of.
         * <p>
         * The default implementation returns the source instance of T -
         * {@code (dst, src) -> src}.
         *
         * @param accumulator function to accumulate instances of T
         * @return this sink for chaining
         */
        public Sink<T> onAccumulate(BinaryOperator<T> accumulator) {
            this.accumulator = Objects.requireNonNull(accumulator);
            return this;
        }

        /**
         * Function to validate a source Data.Packet value against a destination
         * Data.Packet value. The first argument is the destination, the second
         * the existing source value. If this function returns false then the
         * onCreate function will be called to create a new value for the source
         * Data.Packet
         * <p>
         * Packets from different sinks are always treated as invalid.
         * <p>
         * The default function always returns true.
         *
         * @param validator function to validate source T against destination T
         * @return this sink for chaining
         */
        public Sink<T> onValidate(BiPredicate<T, T> validator) {
            this.validator = Objects.requireNonNull(validator);
            return this;
        }

        /**
         * Function to dispose of an instance of T.
         * <p>
         * The default function will call {@code close()} if T is an instance of
         * {@link AutoCloseable}. Otherwise it is a no-op.
         *
         * @param disposer function to dispose of instance of T
         * @return this sink for chaining
         */
        public Sink<T> onDispose(Consumer<T> disposer) {
            this.disposer = Objects.requireNonNull(disposer);
            return this;
        }

        protected void log(Exception ex) {
        }

    }

    private static class SinkPacket<T> implements Packet<T> {

        private final Sink<T> sink;
        private T data;

        private SinkPacket(Sink<T> sink, T data) {
            this.sink = sink;
            this.data = data;
        }

        @Override
        public T data() {
            return data;
        }

        @Override
        public void clear() {
            apply(sink.clearer);
        }

        @Override
        public void apply(Function<? super T, ? extends T> operator) {
            try {
                T cur = Objects.requireNonNull(data);
                data = Objects.requireNonNull(operator.apply(data));
                if (data != cur) {
                    sink.disposer.accept(cur);
                }
            } catch (Exception ex) {
                sink.log(ex);
            }

        }

        @Override
        public void accumulate(List<Packet<T>> packets) {
            if (!packets.contains(this)) {
                clear();
            }
            try {
                packets.forEach(src -> {
                    if (src != this) {
                        T cur = data;
                        data = sink.accumulator.apply(data, src.data());
                        if (data != cur) {
                            sink.disposer.accept(cur);
                        }
                    }
                });
            } catch (Exception ex) {
                sink.log(ex);
            }

        }

        @Override
        public boolean isCompatible(Packet<T> packet) {
            try {
                if (packet == this) {
                    return true;
                } else if (packet instanceof SinkPacket<T> other) {
                    if (sink != other.sink) {
                        return false;
                    }
                    T otherData = other.data();
                    if (otherData == null) {
                        return false;
                    } else {
                        return sink.validator.test(data, otherData);
                    }
                } else {
                    return false;
                }
            } catch (Exception ex) {
                sink.log(ex);
                return false;
            }
        }

        @Override
        public Packet<T> createPacket() {
            return new SinkPacket<>(this.sink, sink.creator.apply(data));
        }

        @Override
        public void dispose() {
            try {
                Objects.requireNonNull(data);
                sink.disposer.accept(data);
                data = null;
            } catch (Exception ex) {
                sink.log(ex);
            }
        }

        @Override
        public Lookup getLookup() {
            return sink.getLookup();
        }

    }

    /**
     * Input port pipe.
     * <p>
     * Create using eg. {@code @In Data.In<TYPE> in;}
     *
     * @param <T> data type
     */
    public static abstract class In<T> extends IdentityPipe<T> {
    }

    /**
     * Output port pipe.
     * <p>
     * Create using eg. {@code @Out Data.Out<TYPE> in;}
     *
     * @param <T> data type
     */
    public static abstract class Out<T> extends IdentityPipe<T> {
    }

    /**
     * The base type of pipes that can be connected to form processing graphs.
     * Generally use the various factory methods (eg. Data.with() ) or Data.In /
     * Data.Out
     *
     * @param <T> data type of Pipe
     */
    public static abstract class Pipe<T> {

        private final List<Pipe<T>> sources;
        private final List<Pipe<T>> sinks;
        private final List<Packet<T>> dataPackets;
        private long pass;
        private long renderReqPass;
        private boolean renderReqCache;
        private int renderIdx = 0;

        /**
         * Base constructor for pipes.
         */
        public Pipe() {
            this.sources = new ArrayList<>();
            this.sinks = new ArrayList<>();
            this.dataPackets = new ArrayList<>();
        }

        /**
         * Add a source for this pipe. Will register this pipe as a sink on the
         * source.
         *
         * @param source source pipe
         */
        public final void addSource(Pipe<T> source) {
            source.registerSink(this);
            try {
                registerSource(source);
            } catch (RuntimeException ex) {
                source.unregisterSink(this);
                throw ex;
            }
        }

        /**
         * Remove a source from this pipe.
         *
         * @param source source pipe
         */
        public final void removeSource(Pipe<T> source) {
            source.unregisterSink(this);
            unregisterSource(source);
        }

        /**
         * Remove all sources from this pipe.
         */
        public final void disconnectSources() {
            for (int i = sources.size(); i > 0; i--) {
                removeSource(sources.get(i - 1));
            }
        }

        /**
         * Remove all sinks from this pipe.
         */
        public final void disconnectSinks() {
            for (int i = sinks.size(); i > 0; i--) {
                sinks.get(i - 1).removeSource(this);
            }
        }

        /**
         * Get an immutable snapshot of the currently attached sources.
         *
         * @return current sources
         */
        public final List<Pipe<T>> sources() {
            return List.copyOf(sources);
        }

        /**
         * Convenience method to add multiple sources in one call. See
         * {@link #addSource(org.praxislive.code.userapi.Data.Pipe)}.
         *
         * @param sources sources to add
         * @return this for chaining
         */
        @SafeVarargs
        public final Pipe<T> withSources(Pipe<T>... sources) {
            for (Pipe<T> source : sources) {
                addSource(source);
            }
            return this;
        }

        /**
         * Convenience method to link provided pipes. This pipe will be added as
         * a source of the first provided pipe, with any additional pipes linked
         * in order.
         *
         * @param pipes pipes to link to
         * @return this for chaining
         */
        @SafeVarargs
        public final Pipe<T> linkTo(Pipe<T>... pipes) {
            Pipe<T> source = this;
            for (Pipe<T> pipe : pipes) {
                pipe.addSource(source);
                source = pipe;
            }
            return this;
        }

        /**
         * Get an immutable snapshot of the currently attached sinks.
         *
         * @return current sinks
         */
        public final List<Pipe<T>> sinks() {
            return List.copyOf(sinks);
        }

        /**
         * Clear any cached data.
         */
        protected final void clearCaches() {
            dataPackets.forEach(Packet::dispose);
            dataPackets.clear();
        }

        /**
         * Process data through this pipe. This method is called by sink pipes
         * to process data. It is generally not necessary to call or override
         * this method.
         * <p>
         * The pass parameter is checked against any previous call to check
         * whether sources need to be called or cached data is invalid.
         *
         * @param sink sink calling process - must be registered
         * @param packet data packet to process
         * @param pass pass count
         */
        protected void process(Pipe<T> sink, Packet<T> packet, long pass) {
            int sinkIndex = sinks.indexOf(sink);

            if (sinkIndex < 0) {
                // throw exception?
                return;
            }
            boolean inPlace = sinks.size() == 1 && sources.size() < 2;

            if (this.pass != pass) {
                boolean outputRequired = isOutputRequired(pass);
                this.pass = pass;
                if (inPlace) {
                    processInPlace(packet, outputRequired, pass);
                } else {
                    processCached(packet, outputRequired, pass);
                }
            }

            if (!inPlace) {
                writeOutput(dataPackets, packet, sinkIndex);
            }
        }

        private void processInPlace(Packet<T> packet, boolean outputRequired, long pass) {
            if (!dataPackets.isEmpty()) {
                dataPackets.forEach(Packet::dispose);
                dataPackets.clear();
            }
            if (sources.isEmpty()) {
                packet.clear();
            } else {
                sources.get(0).process(this, packet, pass);
            }
            if (outputRequired) {
                dataPackets.add(packet);
                process(dataPackets);
                dataPackets.clear();
            }
        }

        private void processCached(Packet<T> packet, boolean outputRequired, long pass) {
            boolean hasSources = !sources.isEmpty();
            int requiredPackets = hasSources ? sources.size() : 1;
            while (dataPackets.size() > requiredPackets) {
                dataPackets.removeLast().dispose();
            }
            if (hasSources) {
                for (int i = 0; i < sources.size(); i++) {
                    Packet<T> in;
                    if (i < dataPackets.size()) {
                        in = dataPackets.get(i);
                        if (!packet.isCompatible(in)) {
                            in.dispose();
                            in = packet.createPacket();
                            dataPackets.set(i, in);
                        }
                    } else {
                        in = packet.createPacket();
                        dataPackets.add(in);
                    }
                    sources.get(i).process(this, in, pass);
                }
            } else {
                if (dataPackets.isEmpty()) {
                    dataPackets.add(packet.createPacket());
                } else {
                    Packet<T> cached = dataPackets.get(0);
                    if (!packet.isCompatible(cached)) {
                        cached.dispose();
                        dataPackets.set(0, packet.createPacket());
                    } else if (outputRequired) {
                        cached.clear();
                    }
                }
            }
            if (outputRequired) {
                process(dataPackets);
            }
        }

        /**
         * Process the data.
         * <p>
         * If there is only one sink, and zero or one sources, the packet will
         * be the one provided by the sink. In other cases the data is cached,
         * and written to the sink(s) in
         * {@link #writeOutput(java.util.List, org.praxislive.code.userapi.Data.Packet, int)}.
         *
         * @param data data packets to process
         */
        protected abstract void process(List<Packet<T>> data);

        /**
         * Write the data to the output. The default implementation calls
         * {@link Packet#accumulate(java.util.List)} to combine all the data
         * from sources using the accumulation function provided by the
         * pipeline.
         *
         * @param data list of processed data
         * @param output output data to write to
         * @param sinkIndex sink index of the output
         */
        protected void writeOutput(List<Packet<T>> data, Packet<T> output, int sinkIndex) {
            output.accumulate(data);
        }

        /**
         * Check whether this pipe requires output from the registered source.
         * By default this method checks whether the output of this pipe is
         * required by any registered sink.
         * <p>
         * This method may be overridden to provide additional checks.
         *
         * @param source registered source
         * @param pass process pass
         * @return true if source must provide output
         */
        protected boolean isOutputRequired(Pipe<T> source, long pass) {
            return isOutputRequired(pass);
        }

        /**
         * Check whether at least one registered sink requires this pipe to
         * produce output. This method handles cycles in the pipe graph.
         * <p>
         * This method may be overridden to provide additional checks.
         *
         * @param pass process pass
         * @return true if at least one sink requires output from this pipe
         */
        protected boolean isOutputRequired(long pass) {
            if (sinks.size() == 1) {
                return simpleOutputCheck(pass);
            } else {
                return multipleOutputCheck(pass);
            }
        }

        /**
         * Register a source. This method is called by
         * {@link #addSource(org.praxislive.code.userapi.Data.Pipe)}.
         * <p>
         * May be overridden to validate source or throw an exception.
         *
         * @param source source to register
         * @throws IllegalArgumentException if source is already registered
         */
        protected void registerSource(Pipe<T> source) {
            if (source == null) {
                throw new NullPointerException();
            }
            if (sources.contains(source)) {
                throw new IllegalArgumentException();
            }
            sources.add(source);
        }

        /**
         * Unregister a source. This method is called by
         * {@link #removeSource(org.praxislive.code.userapi.Data.Pipe)}.
         *
         * @param source source to unregister
         */
        protected void unregisterSource(Pipe<T> source) {
            sources.remove(source);
        }

        /**
         * Register a sink. This method is called by
         * {@link #addSource(org.praxislive.code.userapi.Data.Pipe)} on the
         * sink.
         * <p>
         * May be overridden to validate sink or throw an exception.
         *
         * @param sink sink to register
         */
        protected void registerSink(Pipe<T> sink) {
            if (sink == null) {
                throw new NullPointerException();
            }
            if (sinks.contains(sink)) {
                throw new IllegalArgumentException();
            }
            sinks.add(sink);
        }

        /**
         * Unregister a sink. This method is called by
         * {@link #removeSource(org.praxislive.code.userapi.Data.Pipe)} on the
         * sink.
         *
         * @param sink sink to unregister
         */
        protected void unregisterSink(Pipe<T> sink) {
            sinks.remove(sink);
            if (sinks.isEmpty()) {
                clearCaches();
            }
        }

        private boolean simpleOutputCheck(long pass) {
            if (pass != renderReqPass) {
                renderReqPass = pass;
                renderReqCache = sinks.get(0).isOutputRequired(this, pass);
            }
            return renderReqCache;
        }

        private boolean multipleOutputCheck(long pass) {
            if (renderIdx > 0) {
                while (renderIdx < sinks.size()) {
                    if (sinks.get(renderIdx++).isOutputRequired(this, pass)) {
                        renderIdx = 0;
                        return true;
                    }
                }
                return false;
            } else {
                if (renderReqPass != pass) {
                    renderReqPass = pass;
                    renderReqCache = false;
                    while (renderIdx < sinks.size()) {
                        if (sinks.get(renderIdx++).isOutputRequired(this, pass)) {
                            renderReqCache = true;
                            break;
                        }
                    }
                    renderIdx = 0;
                }
                return renderReqCache;
            }
        }

    }

    /**
     * A data holder used to wrap data of type T to be passed around a Pipe
     * graph.
     * <p>
     * Implementations of this interface are provided by the Data.Sink.
     *
     * @param <T> type of wrapped data
     */
    public static interface Packet<T> extends Lookup.Provider {

        /**
         * Access the data in the packet.
         *
         * @return data
         */
        public T data();

        /**
         * Clear the data in the packet. This will use any clear function
         * configured for the pipeline.
         *
         * @see Data.Sink#onClear(java.util.function.UnaryOperator)
         */
        public void clear();

        /**
         * Apply a function to the data in the packet. If the function returns a
         * different instance of data, the packet data will be replaced and the
         * old data disposed. Disposal will be handled by any disposal function
         * set on the pipeline.
         *
         * @param operator data function
         * @see Data.Sink#onDispose(java.util.function.Consumer)
         */
        public void apply(Function<? super T, ? extends T> operator);

        /**
         * Accumulate data from the provided packets into this packet. If the
         * list contains this packet it will be ignored. Accumulation will be
         * done using any accumulate function set on the pipeline.
         *
         * @param packets source packets
         * @see Data.Sink#onAccumulate(java.util.function.BinaryOperator)
         */
        public void accumulate(List<Packet<T>> packets);

        /**
         * Check whether a packet is compatible with this packet. If a packet is
         * not compatible, it will be disposed and a new packet created.
         * Compatibility will be checked using any compatibility function set on
         * the pipeline.
         *
         * @param packet other packet
         * @return true if compatible
         * @see Data.Sink#onValidate(java.util.function.BiPredicate)
         */
        public boolean isCompatible(Packet<T> packet);

        /**
         * Create a packet compatible with the data in this packet.
         *
         * @return newly created packet
         */
        public Packet<T> createPacket();

        /**
         * Dispose of the data in this packet. The packet will not be usable
         * after disposal. Disposal will be handled using any disposal function
         * set on the pipeline.
         *
         * @see Data.Sink#onDispose(java.util.function.Consumer)
         */
        public void dispose();

    }

    static class IdentityPipe<T> extends Pipe<T> {

        @Override
        protected void process(List<Packet<T>> data) {
            // no op
        }

    }

}
