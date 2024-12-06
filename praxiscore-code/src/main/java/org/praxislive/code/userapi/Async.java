/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2024 Neil C Smith.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import org.praxislive.core.Call;
import org.praxislive.core.Value;
import org.praxislive.core.ValueMapper;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;

/**
 * A lightweight holder for a future value, the result of an asynchronous
 * operation such as an actor call. An Async can also reflect the failure of
 * such an operation.<p>
 * An Async can be explicitly completed, with a value or error. Completion can
 * only happen once.
 * <p>
 * <strong>Async is not thread safe and is not designed for concurrent
 * operation.</strong> Use from a single thread, or protect appropriately.
 *
 * @param <T> result type
 */
public final class Async<T> {

    private T result;
    private PError error;
    private Link<T> link;

    /**
     * Construct an empty Async.
     */
    public Async() {

    }

    /**
     * Get the result of this Async if completed without error, otherwise null.
     *
     * @return result or null
     */
    public T result() {
        return result;
    }

    /**
     * Get the failure error or null.
     *
     * @return error or null
     */
    public PError error() {
        return error;
    }

    /**
     * Whether this Async has been completed, with or without error.
     *
     * @return true if done
     */
    public boolean done() {
        return !(result == null && error == null);
    }

    /**
     * Whether this Async completed with a failure.
     *
     * @return true if failed
     */
    public boolean failed() {
        return error != null;
    }

    /**
     * Complete the Async with the provided value. If this Async is already
     * completed, with or without failure, its state remains the same and this
     * method returns false.
     *
     * @param value value to complete the Async
     * @return true if completed
     */
    public boolean complete(T value) {
        if (done()) {
            return false;
        } else {
            result = Objects.requireNonNull(value);
            processLinkOnDone();
            return true;
        }
    }

    /**
     * Complete the Async unsuccessfully with the provided error. If this Async
     * is already completed, with or without failure, its state remains the same
     * and this method returns false.
     *
     * @param error error to complete the Async
     * @return true if completed
     */
    public boolean fail(PError error) {
        if (done()) {
            return false;
        } else {
            this.error = Objects.requireNonNull(error);
            processLinkOnDone();
            return true;
        }
    }

    private void processLinkOnDone() {
        if (link != null) {
            link.processDone(this);
            link = null;
        }
    }

    private void link(Link<T> link) {
        if (this.link == null) {
            this.link = link;
        } else if (this.link instanceof CompoundLink compound) {
            compound.addLink(link);
        } else {
            this.link = new CompoundLink<>(this.link, link);
        }

    }

    private void unlink(Link<T> link) {
        if (this.link == link) {
            this.link = null;
        } else if (this.link instanceof CompoundLink compound) {
            compound.removeLink(link);
        }
    }

    /**
     * Create an Async that will complete when the provided async call
     * completes, by extracting the first call argument and attempting to map to
     * the given type. The returned Async will complete with an error if the
     * call completes with an error, the argument isn't available, or the
     * argument cannot be mapped to the given type.
     *
     * @param <T> type of created async
     * @param asyncCall async call
     * @param type class type of created async
     * @return created async
     */
    public static <T> Async<T> extractArg(Async<Call> asyncCall, Class<T> type) {
        return extractArg(asyncCall, type, 0);
    }

    /**
     * Create an Async that will complete when the provided async call
     * completes, by extracting the indexed call argument and attempting to map
     * to the given type. The returned Async will complete with an error if the
     * call completes with an error, the argument isn't available, or the
     * argument cannot be mapped to the given type.
     *
     * @param <T> type of created async
     * @param asyncCall async call
     * @param type class type of created async
     * @param argIdx index of argument to extract
     * @return created async
     */
    public static <T> Async<T> extractArg(Async<Call> asyncCall, Class<T> type, int argIdx) {
        Objects.requireNonNull(type);
        Async<T> asyncValue = new Async<>();
        if (asyncCall.done()) {
            completeExtract(asyncCall, asyncValue, type, argIdx);
        } else {
            asyncCall.link(handler(
                    async -> completeExtract(async, asyncValue, type, argIdx)
            ));
        }
        return asyncValue;
    }

    /**
     * A utility method for linking an Async with a {@link CompletableFuture}
     * for passing to external APIs.
     * <p>
     * <strong>IMPORTANT : do not use completable futures returned from this
     * method inside component code.</strong> To react to Async completion from
     * within component code, use an {@link Async.Queue}.
     * <p>
     * The completable future will automatically complete with the result or
     * failure of the Async. The link is one way - the Async will not respond to
     * any changes to the future.
     *
     * @param <T> async and future type
     * @param async async to link to created future
     * @return created future
     */
    public static <T> CompletableFuture<T> toCompletableFuture(Async<T> async) {
        if (async.done()) {
            if (async.failed()) {
                return CompletableFuture.failedFuture(extractError(async));
            } else {
                return CompletableFuture.completedFuture(async.result());
            }
        } else {
            CompletableFuture<T> future = new CompletableFuture<>();
            async.link(handler(
                    a -> {
                        if (a.failed()) {
                            future.completeExceptionally(extractError(a));
                        } else {
                            future.complete(a.result());
                        }
                    }
            ));
            return future;
        }
    }

    private static <T> void completeExtract(Async<Call> asyncCall, Async<T> asyncValue, Class<T> type, int argIdx) {
        try {
            if (asyncCall.failed()) {
                asyncValue.fail(asyncCall.error());
            } else {
                Call call = asyncCall.result();
                List<Value> args = call.args();
                if (call.isError()) {
                    PError err;
                    if (args.isEmpty()) {
                        err = PError.of("Unknown error");
                    } else {
                        err = PError.from(args.get(0))
                                .orElseGet(() -> PError.of(args.get(0).toString()));
                    }
                    asyncValue.fail(err);
                } else {
                    Value value = args.get(argIdx);
                    T result;
                    if (Value.class == type) {
                        result = type.cast(value);
                    } else if (value instanceof PReference ref) {
                        if (PReference.class == type) {
                            result = type.cast(ref);
                        } else {
                            result = ref.as(type).orElseThrow(ClassCastException::new);
                        }
                    } else {
                        result = ValueMapper.find(type).fromValue(value);
                    }
                    asyncValue.complete(result);
                }
            }
        } catch (Exception ex) {
            asyncValue.fail(PError.of(ex));
        }
    }

    private static Throwable extractError(Async failed) {
        return failed.error().exception()
                .orElseGet(() -> new Exception(failed.error().toString()));
    }

    private static <T> Handler<T> handler(Consumer<Async<T>> consumer) {
        return new Handler<>(consumer);
    }

    /**
     * A task intended to be run asynchronously and outside of the main
     * component context. All data required to complete the task should be
     * passed in as input data, and implementations should be careful not to use
     * any other data from the component during execution.
     *
     * @param <T> type of the input data
     * @param <R> type of the result
     */
    @FunctionalInterface
    public static interface Task<T, R> {

        /**
         * Execute the task. This will run outside of the main component
         * context. All data required to complete the task should be passed in
         * as input. Implementations should not access (or capture) any other
         * data from the component during execution.
         *
         * @param input input data for task
         * @return result
         * @throws Exception on task error
         */
        public R execute(T input) throws Exception;

    }

    /**
     * A Link is something attached to an Async to react to its completion.
     *
     * @param <T> Async result type
     */
    static sealed abstract class Link<T> {

        Link() {

        }

        abstract void processDone(Async<T> async);

    }

    /**
     * A queue for handling Async instances. The queue can be polled for added
     * Async instances that have completed, or a handler can be attached to run
     * on completion.
     * <p>
     * A queue cannot be constructed directly. Use the {@link Inject} annotation
     * on a field. Queues will have handlers and limits automatically removed on
     * reset, and will be cleared on disposal.
     *
     * @param <T> async result type
     */
    public static final class Queue<T> extends Link<T> {

        private final Deque<Async<T>> deque;

        private int limit;
        private Consumer<Async<T>> onDoneHandler;

        Queue() {
            deque = new ArrayDeque<>();
            this.limit = Integer.MAX_VALUE;
        }

        /**
         * Retrieves and removes the next completed Async, if available. Returns
         * {@code null} if no completed Async is available. The caller should
         * check whether any returned Async has failed before extracting the
         * result or error.
         *
         * @return next completed Async or null
         */
        public Async<T> poll() {
            if (onDoneHandler == null && !deque.isEmpty()) {
                var itr = deque.iterator();
                while (itr.hasNext()) {
                    var async = itr.next();
                    if (async.done()) {
                        itr.remove();
                        async.unlink(this);
                        return async;
                    }
                }
            }
            return null;
        }

        /**
         * Add an Async to the queue. If the Async is already completed, and an
         * onDone handler has been attached, this will be executed before this
         * method returns.
         * <p>
         * If the queue size limit has been reached, the least recently added
         * Async will be evicted and returned to make space.
         *
         * @param async Async to add to queue
         * @return evicted Async or null
         */
        public Async<T> add(Async<T> async) {
            if (async.done() && onDoneHandler != null) {
                onDoneHandler.accept(async);
                return null;
            }
            deque.add(async);
            async.link(this);
            if (deque.size() > limit) {
                var cleared = deque.removeFirst();
                cleared.unlink(this);
                return cleared;
            } else {
                return null;
            }
        }

        /**
         * Remove the provided Async from the queue. If the provided Async is
         * not in this queue, this is a no-op.
         *
         * @param async Async to remove
         * @return true if an element was removed
         */
        public boolean remove(Async<T> async) {
            if (deque.remove(async)) {
                async.unlink(this);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Limit the queue to the provided size. If the current queue size is
         * above the limit, the least recently added Async will be evicted and
         * returned to make space.
         *
         * @param size requested size limit - at least 1.
         * @return evicted Async or empty list
         * @throws IllegalArgumentException if size is less than 1.
         */
        public List<Async<T>> limit(int size) {
            if (size < 1) {
                throw new IllegalArgumentException();
            }
            limit = size;
            if (deque.size() > size) {
                int count = deque.size() - size;
                List<Async<T>> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    var async = deque.removeFirst();
                    async.unlink(this);
                    list.add(async);
                }
                return list;
            } else {
                return List.of();
            }
        }

        /**
         * Query current number of Async in the queue.
         *
         * @return current number of Async
         */
        public int size() {
            return deque.size();
        }

        /**
         * Clear all Async from the queue. Removed Async will be returned.
         *
         * @return list of removed Async or empty list
         */
        public List<Async<T>> clear() {
            List<Async<T>> list = new ArrayList<>(deque);
            deque.clear();
            list.forEach(async -> async.unlink(this));
            return list;
        }

        /**
         * Attach a handler for completed Async. Only one handler may be
         * attached at a time. The handler will be run when any Async in the
         * queue completes. The handler should check for failure before
         * extracting any result or error. Any already completed Async in the
         * queue will be passed to the handler before this method returns. A
         * {@code null} value may be passed to remove the current handler.
         *
         * @param handler handler for completed Async, or null to remove
         */
        public void onDone(Consumer<Async<T>> handler) {
            this.onDoneHandler = handler;
            if (!deque.isEmpty() && handler != null) {
                var itr = deque.iterator();
                while (itr.hasNext()) {
                    var async = itr.next();
                    if (async.done()) {
                        itr.remove();
                        async.unlink(this);
                        handler.accept(async);
                    }
                }
            }
        }

        /**
         * Convenience method to link separate result and error handlers to
         * {@link #onDone(java.util.function.Consumer)}. This method does not
         * accept {@code null} values.
         *
         * @param resultHandler handler for succesful Async result
         * @param errorHandler handler for failed Async errors
         */
        public void onDone(Consumer<T> resultHandler, Consumer<PError> errorHandler) {
            Objects.requireNonNull(resultHandler);
            Objects.requireNonNull(errorHandler);
            onDone(async -> {
                if (async.failed()) {
                    errorHandler.accept(async.error());
                } else {
                    resultHandler.accept(async.result());
                }
            });
        }

        @Override
        void processDone(Async<T> async) {
            if (onDoneHandler != null && deque.remove(async)) {
                onDoneHandler.accept(async);
            }
        }

    }

    static final class Handler<T> extends Link<T> {

        private final Consumer<Async<T>> onDoneHandler;

        Handler(Consumer<Async<T>> onDoneHandler) {
            this.onDoneHandler = onDoneHandler;
        }

        @Override
        void processDone(Async<T> async) {
            onDoneHandler.accept(async);
        }

    }

    static final class CompoundLink<T> extends Link<T> {

        private final Set<Link<T>> links;

        CompoundLink(Link<T> link1, Link<T> link2) {
            links = new CopyOnWriteArraySet<>(List.of(link1, link2));
        }

        @Override
        void processDone(Async<T> async) {
            links.forEach(link -> link.processDone(async));
        }

        void addLink(Link<T> link) {
            links.add(link);
        }

        void removeLink(Link<T> link) {
            links.remove(link);
        }

    }
}
