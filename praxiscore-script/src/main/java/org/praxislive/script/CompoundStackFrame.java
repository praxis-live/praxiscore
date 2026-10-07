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
package org.praxislive.script;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;
import org.praxislive.core.Call;
import org.praxislive.core.Value;
import org.praxislive.core.types.PError;

/**
 *
 */
final class CompoundStackFrame implements StackFrame {

    private final Queue<Function<List<Value>, StackFrame>> queue;

    private StackFrame active;
    private State state;
    private List<Value> result;

    CompoundStackFrame(StackFrame base, Function<List<Value>, StackFrame> next) {
        queue = new ArrayDeque<>();
        queue.add(next);
        active = base;
        state = State.Incomplete;
    }

    @Override
    public State getState() {
        if (state != State.Incomplete) {
            return state;
        } else {
            return active.getState();
        }
    }

    @Override
    public void postResponse(Call call) {
        active.postResponse(call);
        checkActiveState();
    }

    @Override
    public void postResponse(State state, List<Value> args) {
        active.postResponse(state, args);
        checkActiveState();
    }

    @Override
    public StackFrame process(Env env) {
        while (state == State.Incomplete) {
            StackFrame frame = active.process(env);
            if (frame != null || active.getState() == State.Incomplete) {
                return frame;
            }
            checkActiveState();
        }
        return null;
    }

    @Override
    public List<Value> result() {
        if (result != null) {
            return result;
        } else {
            throw new IllegalStateException();
        }
    }

    void addStage(Function<List<Value>, StackFrame> stage) {
        queue.add(stage);
    }

    private void checkActiveState() {
        switch (active.getState()) {
            case Incomplete -> {
            }
            case OK ->
                nextOrComplete();
            default -> {
                state = active.getState();
                result = active.result();
                active = null;
                queue.clear();
            }
        }
    }

    private void nextOrComplete() {
        if (!queue.isEmpty()) {
            try {
                active = queue.remove().apply(active.result());
            } catch (Exception ex) {
                state = State.Error;
                result = List.of(PError.of(ex));
                active = null;
                queue.clear();
            }
        } else {
            state = State.OK;
            result = active.result();
            active = null;
        }
    }

    static class OnFail implements StackFrame {

        private final StackFrame primaryFrame;
        private final Function<List<Value>, StackFrame> catchFrameSupplier;

        private StackFrame catchFrame;

        OnFail(StackFrame primaryFrame,
                Function<List<Value>, StackFrame> catchFrameSupplier) {
            this.primaryFrame = primaryFrame;
            this.catchFrameSupplier = catchFrameSupplier;
        }

        @Override
        public State getState() {
            return catchFrame == null ? primaryFrame.getState() : catchFrame.getState();
        }

        @Override
        public StackFrame process(Env env) {
            if (catchFrame == null) {
                List<Value> result;
                try {
                    StackFrame sf = primaryFrame.process(env);
                    if (primaryFrame.getState() == State.Error) {
                        result = primaryFrame.result();
                    } else {
                        return sf;
                    }
                } catch (Exception ex) {
                    result = List.of(PError.of(ex));
                }
                catchFrame = catchFrameSupplier.apply(result);
                return catchFrame.process(env);
            } else {
                return catchFrame.process(env);
            }
        }

        @Override
        public void postResponse(Call call) {
            if (catchFrame == null) {
                try {
                    primaryFrame.postResponse(call);
                    if (primaryFrame.getState() == State.Error) {
                        catchFrame = catchFrameSupplier.apply(primaryFrame.result());
                    }
                } catch (Exception ex) {
                    catchFrame = catchFrameSupplier.apply(List.of(PError.of(ex)));
                }
            } else {
                catchFrame.postResponse(call);
            }
        }

        @Override
        public void postResponse(State state, List<Value> args) {
            if (catchFrame == null) {
                try {
                    primaryFrame.postResponse(state, args);
                    if (primaryFrame.getState() == State.Error) {
                        catchFrame = catchFrameSupplier.apply(primaryFrame.result());
                    }
                } catch (Exception ex) {
                    catchFrame = catchFrameSupplier.apply(List.of(PError.of(ex)));
                }
            } else {
                catchFrame.postResponse(state, args);
            }
        }

        @Override
        public List<Value> result() {
            return catchFrame == null ? primaryFrame.result() : catchFrame.result();
        }

    }

    static class Fixed implements StackFrame {

        private final State targetState;
        private final List<Value> result;

        private State state = State.Incomplete;

        Fixed(List<Value> result) {
            this.targetState = State.OK;
            this.result = List.copyOf(result);
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public void postResponse(Call call) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void postResponse(State state, List<Value> args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StackFrame process(Env env) {
            if (state == State.Incomplete) {
                state = targetState;
            }
            return null;
        }

        @Override
        public List<Value> result() {
            return state == State.Incomplete ? null : result;
        }
    }

    static class Inline implements StackFrame {

        private final Function<Env, List<Value>> function;

        private State state = State.Incomplete;
        private List<Value> result;

        Inline(Function<Env, List<Value>> function) {
            this.function = function;
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public void postResponse(Call call) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void postResponse(State state, List<Value> args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StackFrame process(Env env) {
            if (state == State.Incomplete) {
                try {
                    result = function.apply(env);
                    state = State.OK;
                } catch (Exception ex) {
                    result = List.of(PError.of(ex));
                    state = State.Error;
                }
            }
            return null;
        }

        @Override
        public List<Value> result() {
            return result;
        }
    }

}
