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

import java.util.Objects;
import java.util.function.IntConsumer;
import org.praxislive.code.CodeContext;
import org.praxislive.core.services.LogLevel;

/**
 * A field type for triggers (actions) - see {@link T @T}. The Trigger type
 * provides a {@link Linkable.Int} for listening for triggers, and maintains a
 * count of each time the trigger has been called (useful for sequencing). It is
 * also possible to connect Runnable functions to be called on each trigger.
 * <p>
 * A field of this type can also be used with the {@link Inject} annotation.
 * This is primarily for use with {@link Timer} for scheduling trigger events
 * when direct external triggering is not required.
 */
public abstract class Trigger {

    private final static long TO_NANO = 1_000_000_000;

    private final CodeContext.ClockListener clock;

    private Timer timer;
    private Link[] links;
    private int index;
    private int maxIndex;
    private CodeContext<?> context;

    protected Trigger() {
        this.clock = this::tick;
        this.links = new Link[0];
        maxIndex = Integer.MAX_VALUE;
    }

    protected void attach(CodeContext<?> context, Trigger previous) {
        this.context = context;
        if (previous != null) {
            index = previous.index;
            if (previous.timer != null) {
                timer = previous.timer;
                previous.timer = null;
                timer.attach(this);
            }
        }
    }

    /**
     * Clear all Linkables from this Trigger.
     *
     * @return this
     */
    public Trigger clearLinks() {
        links = new Link[0];
        return this;
    }

    /**
     * Run the provided Runnable each time this Trigger is triggered. This
     * method is shorthand for {@code on().link(i -> runnable.run());}.
     *
     * @param runnable function to run on trigger
     * @return this
     */
    public Trigger link(Runnable runnable) {
        Link l = new Link();
        l.link(i -> runnable.run());
        return this;
    }

    /**
     * Returns a new {@link Linkable.Int} for listening to each trigger. The int
     * passed to the created linkable will be the same as index, incrementing
     * each time, wrapping at maxIndex.
     *
     * @return new Linkable.Int for reacting to triggers
     */
    public Linkable.Int on() {
        return new Link();
    }

    /**
     * Set the current index. Must not be negative.
     *
     * @param idx new index
     * @return this
     */
    public Trigger index(int idx) {
        if (idx < 0) {
            throw new IllegalArgumentException("Index cannot be less than zero");
        }
        index = (idx % maxIndex);
        return this;
    }

    /**
     * Set the maximum index, at which the index will wrap back to zero.
     *
     * @param max maximum index
     * @return this
     */
    public Trigger maxIndex(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("Max index must be greater than 0");
        }
        maxIndex = max;
        index %= maxIndex;
        return this;
    }

    /**
     * Get the current index.
     *
     * @return current index
     */
    public int index() {
        return index;
    }

    /**
     * Get the current maximum index.
     *
     * @return maximum index
     */
    public int maxIndex() {
        return maxIndex;
    }

    /**
     * Access the {@link Timer} for this trigger to schedule one-off or repeat
     * triggering.
     *
     * @return timer for this trigger
     */
    public Timer timer() {
        if (timer == null) {
            timer = new Timer(this);
        }
        return timer;
    }

    /**
     * Check whether this trigger has a scheduled timer.
     *
     * @return scheduled timer
     */
    public boolean isScheduled() {
        return timer != null && timer.isActive();
    }

    /**
     * Manually trigger this Trigger. Useful for chaining this trigger to other
     * sources of input. Otherwise behaves as if externally called, incrementing
     * index and calling linkables.
     *
     * @return this
     */
    public Trigger trigger() {
        trigger(context.getTime());
        return this;
    }

    protected void trigger(long time) {
        if (hasLinks()) {
            triggerLinks();
        }
        incrementIndex();
    }

    protected boolean hasLinks() {
        return links.length > 0;
    }

    protected void triggerLinks() {
        for (Link l : links) {
            try {
                l.fire(index);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
        }
    }

    protected void incrementIndex() {
        index = (index + 1) % maxIndex;
    }

    protected void reset() {
        clearLinks();
        maxIndex(Integer.MAX_VALUE);
        if (timer != null) {
            timer.reset();
        }
    }

    private void startClock() {
        context.addClockListener(clock);
    }

    private void stopClock() {
        context.removeClockListener(clock);
    }

    private void tick() {
        if (timer == null) {
            assert false;
            return;
        }
        timer.tick();
    }

    private class Link implements Linkable.Int {

        private IntConsumer consumer;

        @Override
        public void link(IntConsumer consumer) {
            if (this.consumer != null) {
                throw new IllegalStateException("Cannot link multiple consumers in one chain");
            }
            this.consumer = Objects.requireNonNull(consumer);
            links = ArrayUtils.add(links, this);
        }

        private void fire(int value) {
            try {
                consumer.accept(value);
            } catch (Exception ex) {

            }
        }

    }

    /**
     * A timer used for scheduling one-off or repeat invocations of a
     * {@link Trigger}. Use {@link #timer()} to access the timer for a trigger.
     */
    public static final class Timer {

        private Trigger trigger;

        private long startTime;
        private long period;
        private boolean active;
        private boolean repeat;

        private Timer(Trigger trigger) {
            this.trigger = trigger;
        }

        /**
         * Check whether this timer is active, with either a one-shot or repeat
         * event scheduled.
         *
         * @return scheduled
         */
        public boolean isActive() {
            return active;
        }

        /**
         * Check whether this timer is active and set to repeat.
         *
         * @return active on repeat
         */
        public boolean isRepeat() {
            return active && repeat;
        }

        /**
         * Set the timer to trigger on repeat at the provided rate. If the timer
         * is not currently active on repeat, the start time will be set to the
         * current time. If the timer is already active on repeat, the previous
         * trigger time will continue to be the start time. Any scheduled
         * one-off event will be cancelled.
         *
         * @param seconds period of timer in seconds
         * @return this for chaining
         */
        public Timer repeat(double seconds) {
            period = calculatePeriod(seconds);
            if (!repeat) {
                startTime = trigger.context.getTime();
            }
            if (!active && !repeat) {
                trigger.startClock();
            }
            repeat = true;
            active = true;
            return this;
        }

        /**
         * Schedule a one-off trigger after the provided delay. Any repeat
         * schedule will be cancelled.
         *
         * @param seconds timer delay in seconds
         * @return this for chaining
         */
        public Timer schedule(double seconds) {
            period = calculatePeriod(seconds);
            startTime = trigger.context.getTime();
            if (!active) {
                trigger.startClock();
            }
            repeat = false;
            active = true;
            return this;
        }

        /**
         * Stop the timer.
         *
         * @return this for chaining
         */
        public Timer stop() {
            active = repeat = false;
            trigger.stopClock();
            return this;
        }

        private void attach(Trigger trigger) {
            this.trigger = trigger;
            if (active) {
                trigger.startClock();
                reset();
            }
        }

        private long calculatePeriod(double seconds) {
            long nanos = (long) (seconds * TO_NANO);
            if (nanos > 1) {
                return nanos;
            } else {
                return 1;
            }
        }

        private void reset() {
            if (repeat) {
                active = false;
            }
        }

        private void tick() {
            if (!active) {
                stop();
                return;
            }
            long now = trigger.context.getTime();
            long target = startTime + period;
            long diff = now - target;
            if (diff >= 0) {
                if (repeat) {
                    startTime = now - (diff % period);
                } else {
                    stop();
                }
                try {
                    trigger.trigger();
                } catch (Throwable t) {
                    stop();
                }
            }
        }

    }

}
