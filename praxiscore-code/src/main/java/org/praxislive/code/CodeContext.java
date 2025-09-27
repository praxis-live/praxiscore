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
package org.praxislive.code;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.praxislive.code.userapi.Async;
import org.praxislive.core.Call;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.Control;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.ExecutionContext;
import org.praxislive.core.Lookup;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Port;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.Services;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.services.TaskService;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PReference;

/**
 * A CodeContext wraps each {@link CodeDelegate}, managing state and the
 * transition from one iteration of delegate to the next on behalf of a
 * {@link CodeComponent}.
 *
 * @param <D> supported delegate type
 */
public abstract class CodeContext<D extends CodeDelegate> {

    private final Map<String, ControlDescriptor<?>> controls;
    private final Map<String, PortDescriptor<?>> ports;
    private final Map<String, ReferenceDescriptor<?>> refs;
    private final List<Descriptor> descriptors;
    private final ComponentInfo info;

    private final D delegate;
    private final LogBuilder log;
    private final Driver driver;
    private final boolean requireClock;
    private final List<ClockListener> clockListeners;
    private final AsyncHandler asyncHandler;

    private ExecutionContext execCtxt;
    private ExecutionContext.State execState = ExecutionContext.State.NEW;
    private CodeComponent<D> cmp;
    private long time;
    private ControlAddress asyncHandlerAddress;

    /**
     * Create a CodeContext by processing the provided {@link CodeConnector}
     * (containing CodeDelegate).
     *
     * @param connector code connector with delegate
     */
    protected CodeContext(CodeConnector<D> connector) {
        this(connector, false);
    }

    /**
     * Create a CodeContext by processing the provided {@link CodeConnector}
     * (containing CodeDelegate). This constructor takes a boolean to force
     * connecting the context to the execution clock, should the subtype always
     * require clock signals.
     *
     * @param connector code connector with delegate
     * @param requireClock true to force clock connection
     */
    protected CodeContext(CodeConnector<D> connector, boolean requireClock) {
        this.driver = new Driver();
        clockListeners = new CopyOnWriteArrayList<>();
        // @TODO what is maximum allowed amount a root can be behind system time?
        try {
            connector.process();
            controls = connector.extractControls();
            ports = connector.extractPorts();
            refs = connector.extractRefs();
            info = connector.extractInfo();
            delegate = connector.getDelegate();
            log = new LogBuilder(LogLevel.ERROR);
            this.requireClock = requireClock || connector.requiresClock();
            this.asyncHandler = Objects.requireNonNull((AsyncHandler) controls.get(AsyncHandler.ID));
            descriptors = new ArrayList<>(controls.values());
            descriptors.addAll(ports.values());
            descriptors.addAll(refs.values());
        } catch (Exception e) {
//            Logger.getLogger(CodeContext.class.getName()).log(Level.FINE, "", e);
            throw e;
        }
    }

    void setComponent(CodeComponent<D> cmp) {
        this.cmp = cmp;
        delegate.setContext(this);
    }

    void handleConfigure(CodeComponent<D> cmp, CodeContext<D> oldCtxt) {
        configureControls(oldCtxt);
        configurePorts(oldCtxt);
        configureRefs(oldCtxt);
        configure(cmp, oldCtxt);
    }

    /**
     * A hook method that will be called when the CodeContext is configured on a
     * component. It is called after controls, ports and refs have been
     * configured. Subclasses may override this to do additional configuration.
     * The default implementation does nothing.
     *
     * @param cmp component being attached to
     * @param oldCtxt previous context, or null if there was none
     */
    protected void configure(CodeComponent<D> cmp, CodeContext<D> oldCtxt) {
    }

    private void configureControls(CodeContext<D> oldCtxt) {
        Map<String, ControlDescriptor<?>> oldControls = oldCtxt == null
                ? Collections.EMPTY_MAP : oldCtxt.controls;
        controls.forEach((id, cd) -> cd.handleAttach(this, oldControls.remove(id)));
        oldControls.forEach((id, cd) -> cd.dispose());
    }

    private void configurePorts(CodeContext<D> oldCtxt) {
        Map<String, PortDescriptor<?>> oldPorts = oldCtxt == null
                ? Collections.EMPTY_MAP : oldCtxt.ports;
        ports.forEach((id, pd) -> pd.handleAttach(this, oldPorts.remove(id)));
        oldPorts.forEach((id, pd) -> {
            pd.dispose();
        });
    }

    private void configureRefs(CodeContext<D> oldCtxt) {
        Map<String, ReferenceDescriptor<?>> oldRefs = oldCtxt == null
                ? Collections.EMPTY_MAP : oldCtxt.refs;
        refs.forEach((id, ref) -> ref.handleAttach(this, oldRefs.remove(id)));
        oldRefs.forEach((id, ref) -> ref.dispose());
    }

    final void handleHierarchyChanged() {
        hierarchyChanged();

        LogLevel level = getLookup().find(LogLevel.class)
                .orElse(LogLevel.ERROR);
        log.setLevel(level);

        ExecutionContext ctxt = cmp == null ? null : cmp.getExecutionContext();
        if (execCtxt != ctxt) {
            if (execCtxt != null) {
                execCtxt.removeStateListener(driver);
                execCtxt.removeClockListener(driver);
            }
            execCtxt = ctxt;
            asyncHandlerAddress = null;
            if (ctxt != null) {
                asyncHandlerAddress = ControlAddress.of(cmp.getAddress(), AsyncHandler.ID);
                ctxt.addStateListener(driver);
                if (requireClock) {
                    ctxt.addClockListener(driver);
                }
                handleStateChanged(ctxt, false);
            }
        }
    }

    /**
     * Called when the hierarchy changes, which might be because the component
     * hierarchy has changed (see {@link Component#hierarchyChanged()}), the
     * context has been added or is being removed from the component, or for any
     * other reason that cached information should be invalidated (eg. anything
     * retrieved from the lookup). Subclasses may override this to handle such
     * events / invalidate lookup results. The default implementation does
     * nothing.
     */
    protected void hierarchyChanged() {
    }

    final void handleStateChanged(ExecutionContext source, boolean full) {
        if (execState == source.getState()) {
            return;
        }
        ExecutionContext.State oldExecState = execState;
        execState = source.getState();
        if (full && execState == ExecutionContext.State.IDLE
                && oldExecState != ExecutionContext.State.NEW) {
            onStop();
            descriptors.forEach(Descriptor::onStop);
        }
        onReset();
        descriptors.forEach(Descriptor::onReset);
        update(source.getTime());
        if (execState == ExecutionContext.State.ACTIVE) {
            descriptors.forEach(Descriptor::onInit);
            if (full) {
                descriptors.forEach(Descriptor::onStart);
            }
            onInit();
            if (full) {
                onStart();
            }
        }
        flush();
    }

    /**
     * Hook called when the code context becomes active, either as a result of
     * the execution context becoming active or the code context being attached
     * to a component within an active execution context.
     */
    protected void onInit() {

    }

    /**
     * Hook called when the execution context becomes active. The
     * {@link #onInit()} hook will always have been called before this hook.
     */
    protected void onStart() {

    }

    /**
     * Hook called when the execution context is stopping.
     */
    protected void onStop() {

    }

    /**
     * Hook called when the context is being reset.
     * <p>
     * Code inside this hook should not invoke code on the delegate.
     */
    protected void onReset() {

    }

    final void handleTick(ExecutionContext source) {
        update(source.getTime());
        tick(source);
        flush();
    }

    /**
     * Hook called by the clock listener on the execution context. The default
     * implementation does nothing.
     *
     * @param source execution context
     */
    protected void tick(ExecutionContext source) {
    }

    /**
     * Reset and (if active) reinitialize all control, port and reference
     * descriptors. This does not call the {@link #onReset()} or
     * {@link #onInit()} hooks.
     */
    protected final void resetAndInitialize() {
        descriptors.forEach(Descriptor::onReset);
        if (execCtxt.getState() == ExecutionContext.State.ACTIVE) {
            descriptors.forEach(Descriptor::onInit);
        }
    }

    final void handleDispose() {
        refs.values().forEach(ReferenceDescriptor::dispose);
        refs.clear();
        controls.values().forEach(ControlDescriptor::dispose);
        controls.clear();
        ports.values().forEach(PortDescriptor::dispose);
        ports.clear();
        dispose();
        cmp = null;
        handleHierarchyChanged();
    }

    /**
     * Hook called during disposal of code context. The default implementation
     * does nothing.
     */
    protected void dispose() {
    }

    /**
     * Get the code component this code context is attached to, if there is one.
     *
     * @return code component, or null
     */
    public CodeComponent<D> getComponent() {
        return cmp;
    }

    /**
     * Get the delegate this context wraps.
     *
     * @return delegate
     */
    public D getDelegate() {
        return delegate;
    }

    /**
     * Get the control to handle the specified ID, or null if there isn't one.
     *
     * @param id control ID
     * @return control or null
     */
    protected Control getControl(String id) {
        ControlDescriptor cd = controls.get(id);
        return cd == null ? null : cd.control();
    }

    /**
     * Get the control descriptor for the specified ID, or null if there isn't
     * one.
     *
     * @param id control ID
     * @return control descriptor or null
     */
    protected ControlDescriptor getControlDescriptor(String id) {
        return controls.get(id);
    }

    /**
     * Get all the available control IDs.
     *
     * @return control IDs
     */
    protected Stream<String> controlIDs() {
        return controls.keySet().stream();
    }

    /**
     * Get the port with the specified ID, or null if there isn't one.
     *
     * @param id port ID
     * @return port or null
     */
    protected Port getPort(String id) {
        PortDescriptor pd = ports.get(id);
        return pd == null ? null : pd.port();
    }

    /**
     * Get the port descriptor for the specified ID, or null if there isn't one.
     *
     * @param id port ID
     * @return port descriptor or null
     */
    protected PortDescriptor getPortDescriptor(String id) {
        return ports.get(id);
    }

    /**
     * Get the available port IDs.
     *
     * @return port IDs
     */
    protected Stream<String> portIDs() {
        return ports.keySet().stream();
    }

    /**
     * Get component info.
     *
     * @return component info
     */
    protected ComponentInfo getInfo() {
        return info;
    }

    /**
     * Find the address of the passed in control, or null if it does not have
     * one.
     *
     * @param control control to find address for
     * @return control address or null
     */
    protected ControlAddress getAddress(Control control) {
        ComponentAddress ad = cmp == null ? null : cmp.getAddress();
        if (ad != null) {
            for (Map.Entry<String, ControlDescriptor<?>> ce : controls.entrySet()) {
                if (ce.getValue().control() == control) {
                    return ControlAddress.of(ad, ce.getKey());
                }
            }
        }
        return null;
    }

    /**
     * Get lookup.
     *
     * @return lookup
     */
    public Lookup getLookup() {
        return cmp == null ? Lookup.EMPTY : cmp.getLookup();
    }

    /**
     * Locate the provided service type, if available.
     *
     * @param type service to lookup
     * @return optional service address
     */
    public Optional<ComponentAddress> locateService(Class<? extends Service> type) {
        return getLookup().find(Services.class).flatMap(s -> s.locate(type));
    }

    /**
     * Get current time in nanoseconds.
     *
     * @return time in nanoseconds
     */
    public long getTime() {
        return time;
    }

    /**
     * Add a clock listener. Resources used inside code delegates should add a
     * clock listener rather than listen directly on the execution context.
     *
     * @param listener clock listener
     */
    public void addClockListener(ClockListener listener) {
        clockListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Remove a clock listener.
     *
     * @param listener to remove
     */
    public void removeClockListener(ClockListener listener) {
        clockListeners.remove(listener);
    }

    /**
     * Get the execution context, or null if not attached.
     *
     * @return execution context, or null
     */
    protected ExecutionContext getExecutionContext() {
        return cmp == null ? null : cmp.getExecutionContext();
    }

    /**
     * Check whether the CodeContext is running inside an ExecutionContext with
     * active state. If the execution context is active, but a transition to
     * active has not yet been handled in this code context, the state
     * transition will be triggered.
     *
     * @return true if active
     */
    protected boolean checkActive() {
        if (execState == ExecutionContext.State.ACTIVE) {
            return true;
        }
        if (execCtxt != null) {
            if (execCtxt.getState() == ExecutionContext.State.ACTIVE) {
                var parent = getComponent().getParent();
                if (parent instanceof CodeComponent) {
                    ((CodeComponent) parent).getCodeContext().checkActive();
                }
                handleStateChanged(execCtxt, true);
                return execState == ExecutionContext.State.ACTIVE;
            }
        }
        return false;
    }

    /**
     * Update the time in this context to the specified time. A value the same
     * or behind the current value will be ignored. This method will call all
     * clock listeners.
     *
     * @param time updated time
     */
    protected void update(long time) {
        if (time - this.time > 0) {
            this.time = time;
            clockListeners.forEach(ClockListener::tick);
        }
    }

    /**
     * Invoke the provided task, if the context is active, and after updating
     * the clock to the specified time (if later). Any exception will be caught
     * and logged, and the context will be flushed.
     *
     * @param time new clock time
     * @param task runnable task to execute
     */
    public void invoke(long time, Runnable task) {
        if (checkActive()) {
            update(time);
            try {
                task.run();
            } catch (Exception ex) {
                log.log(LogLevel.ERROR, ex);
            }
            flush();
        }
    }

    /**
     * Invoke the provided task and return the result, if the context is active,
     * and after updating the clock to the specified time (if later). Any
     * exception will be logged and rethrown, and the context flushed. Throws an
     * {@link IllegalStateException} if {@link #checkActive()} returns
     * <code>false</code>.
     *
     * @param <V> the result type of method call
     * @param time new clock time
     * @param task runnable task to execute
     * @return result
     * @throws Exception if unable to compute a result
     */
    public <V> V invokeCallable(long time, Callable<V> task) throws Exception {
        if (checkActive()) {
            update(time);
            try {
                return task.call();
            } catch (Exception ex) {
                log.log(LogLevel.ERROR, ex);
                throw ex;
            } finally {
                flush();
            }
        } else {
            throw new IllegalStateException("Component not active");
        }
    }

    void invoke(long time, Method method, Object... params) {
        if (checkActive()) {
            update(time);
            try {
                method.invoke(getDelegate(), params);
            } catch (Exception ex) {
                if (ex instanceof InvocationTargetException) {
                    Throwable t = ex.getCause();
                    ex = t instanceof Exception ? (Exception) t : ex;
                }
                StringBuilder sb = new StringBuilder("Exception thrown from ");
                sb.append(method.getName());
                sb.append('(');
                Class<?>[] types = method.getParameterTypes();
                for (int i = 0; i < types.length; i++) {
                    sb.append(types[i].getSimpleName());
                    if (i < (types.length - 1)) {
                        sb.append(',');
                    }
                }
                sb.append(')');
                log.log(LogLevel.ERROR, ex, sb.toString());
            }
            flush();
        }
    }

    /**
     * Flush the code context. By default this message checks for pending log
     * messages and delivers to the log.
     */
    protected void flush() {
        if (!log.isEmpty()) {
            log(log.toList());
            log.clear();
        }
    }

    /**
     * Get the log builder for writing log messages.
     *
     * @return log builder
     */
    public LogBuilder getLog() {
        return log;
    }

    /**
     * Get the active log level.
     *
     * @return active log level
     */
    protected LogLevel getLogLevel() {
        return log.getLevel();
    }

    /**
     * Process and send messages from an external log builder.
     *
     * @param log external log builder
     */
    protected void log(LogBuilder log) {
        if (log.isEmpty()) {
            return;
        }
        log(log.toList());
    }

    private void log(List<Value> args) {
        PacketRouter router = cmp.getPacketRouter();
        ControlAddress to = cmp.getLogToAddress();
        ControlAddress from = asyncHandlerAddress;
        if (router == null || to == null) {
            return;
        }
        router.route(Call.createQuiet(to, from, time, args));
    }

    final void tell(ControlAddress destination, Value value) {
        Call call = Call.createQuiet(destination, asyncHandlerAddress, getTime(), value);
        getComponent().getPacketRouter().route(call);
    }

    final void tellIn(double seconds, ControlAddress destination, Value value) {
        long timeCode = getTime() + ((long) (seconds * 1_000_000_000));
        Call call = Call.createQuiet(destination, asyncHandlerAddress, timeCode, value);
        getComponent().getPacketRouter().route(call);
    }

    final Async<Call> ask(ControlAddress destination, List<Value> args) {
        Call call = Call.create(destination, asyncHandlerAddress, time, args);
        getComponent().getPacketRouter().route(call);
        Async<Call> async = new Async<>();
        asyncHandler.register(call, async);
        return async;
    }

    final void timeoutAsync(double seconds, Async<?> async) {
        tellIn(seconds, asyncHandlerAddress, PReference.of(async));
    }

    final <T, R> Async<R> async(T input, Async.Task<T, R> task) {
        Async<R> async = new Async<>();
        try {
            ControlAddress to = locateService(TaskService.class)
                    .map(c -> ControlAddress.of(c, TaskService.SUBMIT))
                    .orElseThrow(ServiceUnavailableException::new);
            TaskService.Task wrapper = () -> PReference.of(task.execute(input));
            Call call = Call.create(to, asyncHandlerAddress, time, PReference.of(wrapper));
            getComponent().getPacketRouter().route(call);
            asyncHandler.register(call, async, c -> {
                @SuppressWarnings("unchecked")
                R result = (R) PReference.from(c.args().get(0))
                        .flatMap(ref -> ref.as(Object.class))
                        .orElseThrow(IllegalStateException::new);
                return result;
            });
        } catch (Exception ex) {
            async.fail(PError.of(ex));
        }
        return async;
    }

    final void writeDescriptors(TreeWriter writer) {
        descriptors.forEach(d -> d.write(writer));
    }

    /**
     * Listener for responding to time changes inside the context.
     */
    public static interface ClockListener {

        /**
         * Time has changed.
         */
        public void tick();

    }

    private class Driver implements ExecutionContext.StateListener,
            ExecutionContext.ClockListener {

        @Override
        public void stateChanged(ExecutionContext source) {
            handleStateChanged(source, true);
        }

        @Override
        public void tick(ExecutionContext source) {
            handleTick(source);
        }

    }

}
