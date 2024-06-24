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
package org.praxislive.audio.impl.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.praxislive.audio.AudioContext;
import org.praxislive.audio.AudioSettings;
import org.praxislive.audio.ClientRegistrationException;
import org.praxislive.core.Lookup;
import org.jaudiolibs.audioservers.AudioConfiguration;
import org.jaudiolibs.audioservers.AudioServer;
import org.jaudiolibs.audioservers.AudioServerProvider;
import org.jaudiolibs.audioservers.ext.ClientID;
import org.jaudiolibs.audioservers.ext.Device;
import org.jaudiolibs.pipes.client.PipesAudioClient;
import org.praxislive.base.AbstractProperty;
import org.praxislive.base.AbstractRootContainer;
import org.praxislive.base.DefaultExecutionContext;
import org.praxislive.code.SharedCodeProperty;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Call;
import org.praxislive.core.Clock;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Info;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.services.LogService;
import org.praxislive.core.services.Services;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 *
 */
public class DefaultAudioRoot extends AbstractRootContainer {

    private static final System.Logger LOG = System.getLogger(DefaultAudioRoot.class.getName());
    private static final int MAX_CHANNELS = 16;
    private static final int MIN_SAMPLERATE = 2000;
    private static final int MAX_SAMPLERATE = 192000;
    private static final int DEFAULT_SAMPLERATE = 48000;
    private static final int MAX_BLOCKSIZE = 512;
    private static final int DEFAULT_BLOCKSIZE = 64;

    // Permanent controls 
    private final CheckedIntProperty sampleRate;
    private final CheckedIntProperty blockSize;
    private final LibraryProperty audioLib;
    private final CheckedStringProperty clientName;

    // Dynamic controls
    private final CheckedIntProperty extBufferSize;
    private final DeviceProperty deviceName;
    private final DeviceProperty inputDeviceName;
    private final TimingModeProperty timingMode;

    private final ComponentInfo baseInfo;
    private final AudioContext audioCtxt;
    private final SharedCodeProperty sharedCode;

    private ComponentInfo info;
    private Map<String, LibraryInfo> libraries;
    private AudioContext.InputClient inputClient;
    private AudioContext.OutputClient outputClient;
    private PipesAudioClient bus;
    private AudioDelegate delegate;
    private AudioServer server;
    private Lookup lookup;
    private long period = -1;

    public DefaultAudioRoot() {
        sharedCode = new SharedCodeProperty(this, this::handleLog);
        registerControl("shared-code", sharedCode);

        extractLibraryInfo();

        // permanent
        sampleRate = new CheckedIntProperty(MIN_SAMPLERATE, MAX_SAMPLERATE, DEFAULT_SAMPLERATE);
        registerControl("sample-rate", sampleRate);
        blockSize = new CheckedIntProperty(1, MAX_BLOCKSIZE, DEFAULT_BLOCKSIZE);
        registerControl("block-size", blockSize);
        clientName = new CheckedStringProperty(PString.EMPTY);
        registerControl("client-name", clientName);
        audioLib = new LibraryProperty();
        registerControl("library", audioLib);

        // dynamic
        deviceName = new DeviceProperty();
        inputDeviceName = new DeviceProperty();
        extBufferSize = new CheckedIntProperty(1, DEFAULT_SAMPLERATE, AudioSettings.getBuffersize());
        timingMode = new TimingModeProperty();

        baseInfo = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .control(ContainerProtocol.SUPPORTED_TYPES, ContainerProtocol.SUPPORTED_TYPES_INFO)
                .merge(StartableProtocol.API_INFO)
                .control("shared-code", SharedCodeProperty.INFO)
                .control("sample-rate", c -> c.property()
                    .defaultValue(PNumber.of(DEFAULT_SAMPLERATE))
                    .input(a -> a.number()
                        .min(MIN_SAMPLERATE).max(MAX_SAMPLERATE)
                    .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                ))
                .control("block-size", c -> c.property()
                    .defaultValue(PNumber.of(DEFAULT_BLOCKSIZE))
                    .input(a -> a.number()
                        .min(1).max(MAX_BLOCKSIZE)
                    .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                ))
                .control("client-name", c -> c.property()
                    .defaultValue(PString.EMPTY)
                    .input(a -> a.string())
                )
                .control("library", c -> c.property()
                    .defaultValue(PString.EMPTY)
                    .input(a -> a.string()
                    .emptyIsDefault()
                    .allowed(
                        Stream.concat(Stream.of(""),
                                libraries.keySet().stream().sorted())
                                .toArray(String[]::new))
                ))
                .property(ComponentInfo.KEY_DYNAMIC, PBoolean.TRUE)
                .property(ComponentInfo.KEY_COMPONENT_TYPE, ComponentType.of("root:audio"))
        );
        info = baseInfo;

        audioCtxt = new AudioCtxt();

    }
    
    private void extractLibraryInfo() {
        libraries = new LinkedHashMap<>();
        List<Device> devices = new ArrayList<>();
        List<Device> inputDevices = new ArrayList<>();
        AudioServerProvider[] providers
                = Lookup.SYSTEM.findAll(AudioServerProvider.class)
                        .toArray(AudioServerProvider[]::new);
        for (AudioServerProvider lib : providers) {
            LOG.log(System.Logger.Level.TRACE,
                    "Audio Library : {0}", lib.getLibraryName());
            devices.clear();
            inputDevices.clear();
            for (Device device : lib.findAll(Device.class)) {
                if (device.getMaxOutputChannels() > 0) {
                    LOG.log(System.Logger.Level.TRACE,
                            "-- Found device : {0}", device.getName());
                    devices.add(device);
                } else if (device.getMaxInputChannels() > 0) {
                    LOG.log(System.Logger.Level.TRACE,
                            "-- Found input device : {0}", device.getName());
                    inputDevices.add(device);
                }
            }
            libraries.put(lib.getLibraryName(),
                    new LibraryInfo(lib,
                            List.copyOf(devices),
                            List.copyOf(inputDevices)));
        }
    }

    private void updateLibrary(String lib) {
        unregisterControl("device");
        unregisterControl("input-device");
        unregisterControl("ext-buffer-size");
        unregisterControl("timing-mode");
        info = baseInfo;

        if (lib.isEmpty()) {
            return;
        }

        LibraryInfo libInfo = libraries.get(lib);
        if (libInfo == null) {
            return;
        }

        if (!"JACK".equals(lib)) {
            registerControl("device", deviceName);
            registerControl("input-device", inputDeviceName);
            registerControl("ext-buffer-size", extBufferSize);
            registerControl("timing-mode", timingMode);
            info = Info.component(cmp -> cmp
                    .merge(baseInfo)
                    .control("device", c -> c.property()
                    .defaultValue(PString.EMPTY)
                    .input(a -> a.string()
                    .suggested(deviceNames(libInfo.devices))
                    .emptyIsDefault()
                    )
                    )
                    .control("input-device", c -> c.property()
                    .defaultValue(PString.EMPTY)
                    .input(a -> a.string()
                    .suggested(deviceNames(libInfo.devices))
                    .emptyIsDefault()
                    )
                    )
                    .control("ext-buffer-size", c -> c.property()
                    .input(a -> a.number()
                    .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                    .property(ArgumentInfo.KEY_SUGGESTED_VALUES,
                            PArray.of(
                                    PNumber.of(64),
                                    PNumber.of(128),
                                    PNumber.of(256),
                                    PNumber.of(512),
                                    PNumber.of(1024),
                                    PNumber.of(2048),
                                    PNumber.of(4096)))
                    )
                    )
                    .control("timing-mode", c -> c.property()
                    .defaultValue(PString.of("Blocking"))
                    .input(a -> a.string()
                    .allowed("Blocking", "Estimated", "FramePosition"))
                    )
            );
        }
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), audioCtxt, sharedCode.getSharedCodeContext());
        }
        return lookup;
    }

    @Override
    protected DefaultExecutionContext createContext(long initialTime) {
        return new Context(initialTime);
    }

    @Override
    protected void starting() {
        try {
            if (outputClient == null) {
                setIdle();
            }
            bus = new PipesAudioClient(blockSize.value.toIntValue(),
                    inputClient == null ? 0 : inputClient.getInputCount(),
                    outputClient.getOutputCount());
            delegate = new AudioDelegate(getRootHub().getClock());
            bus.addListener(delegate);
            if (inputClient != null) {
                makeInputConnections();
            }
            makeOutputConnections();
            server = createServer(bus);
            attachDelegate(delegate);
            delegate.start();
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.ERROR, "", ex);
            setIdle();
        }

    }

    private AudioServer createServer(PipesAudioClient bus) throws Exception {
        float srate = sampleRate.value.toIntValue();
        int buffersize = getBuffersize();

        boolean usingDefault = false;
        LibraryInfo libInfo = libraries.get(audioLib.value.value());
        if (libInfo == null) {
            libInfo = libraries.get(AudioSettings.getLibrary());
            if (libInfo == null) {
                throw new IllegalStateException("Audio library not found");
            }
            usingDefault = true;
        }
        LOG.log(System.Logger.Level.TRACE,
                "Found audio library {0}\n{1}", new Object[]{
                    libInfo.provider.getLibraryName(), libInfo.provider.getLibraryDescription()
                });

        Device device = findDevice(libInfo, usingDefault, false);
        if (device != null) {
            LOG.log(System.Logger.Level.TRACE,
                    "Found device : {0}", device.getName());
        }
        Device inputDevice = null;
        if (device != null && device.getMaxInputChannels() == 0 && bus.getSourceCount() > 0) {
            inputDevice = findDevice(libInfo, usingDefault, true);
            if (inputDevice != null) {
                LOG.log(System.Logger.Level.TRACE,
                        "Found input device : {0}", inputDevice.getName());
            }
        }

        ClientID clientID;
        var id = clientName.value.toString();
        if (id.isBlank()) {
            clientID = new ClientID("PraxisCORE-" + getAddress().rootID());
        } else {
            clientID = new ClientID(id);
        }

        var timing = findTimingMode(this.timingMode.value.toString());

        AudioConfiguration ctxt = new AudioConfiguration(srate,
                bus.getSourceCount(),
                bus.getSinkCount(),
                buffersize,
                timing == null
                        ? createCheckedExts(device, inputDevice, clientID)
                        : createCheckedExts(device, inputDevice, clientID, timing)
        );
        return libInfo.provider.createServer(ctxt, bus);
    }

    private int getBuffersize() {
        int req = extBufferSize == null
                ? AudioSettings.getBuffersize()
                : extBufferSize.value.toIntValue();
        int block = blockSize.value.toIntValue();
        if (req < 1 || block < 1) {
            throw new IllegalArgumentException("Buffer / block values out of range");
        }
        if (block > req) {
            return block;
        }
        int bsize = block;
        while (bsize < req) {
            bsize += block;
        }
        LOG.log(System.Logger.Level.TRACE,
                "Requesting buffersize of : {0}", bsize);
        return bsize;
    }

    private Device findDevice(LibraryInfo info, boolean usingDefault, boolean input) {
        String name = null;

        if (usingDefault) {
            name = input ? AudioSettings.getInputDeviceName() : AudioSettings.getDeviceName();
        } else {
            if (input) {
                name = inputDeviceName == null ? null : inputDeviceName.value.value();
            } else {
                name = deviceName == null ? null : deviceName.value.value();
            }
        }

        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        List<Device> devices = input ? info.inputDevices : info.devices;
        for (Device device : devices) {
            if (device.getName().equals(name)) {
                return device;
            }
        }
        for (Device device : devices) {
            if (device.getName().contains(name)) {
                return device;
            }
        }
        return null;
    }

    private void validateDevices() {
        if (deviceName == null || inputDeviceName == null) {
            return;
        }
        LibraryInfo libInfo = libraries.get(audioLib.value.value());
        if (libInfo == null) {
            return;
        }
        Device primary = findDevice(libInfo, false, false);
        if (primary != null && primary.getMaxInputChannels() > 0) {
            inputDeviceName.value = PString.EMPTY;
        }
    }

    private String[] deviceNames(List<Device> devices) {
        String[] names = new String[devices.size() + 1];
        names[0] = "";
        for (int i = 0; i < devices.size(); i++) {
            names[i + 1] = devices.get(i).getName();
        }
        return names;
    }

    private Object findTimingMode(String mode) {
        try {
            Class<?> modeClass = Class.forName("org.jaudiolibs.audioservers.javasound.JSTimingMode");
            return Enum.valueOf(modeClass.asSubclass(Enum.class), mode);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object[] createCheckedExts(Object... exts) {
        List<Object> lst = new ArrayList<Object>(exts.length);
        for (Object o : exts) {
            if (o != null) {
                lst.add(o);
            }
        }
        return lst.toArray();
    }

    private void makeInputConnections() {
        int count = Math.min(inputClient.getInputCount(), bus.getSourceCount());
        for (int i = 0; i < count; i++) {
            inputClient.getInputSink(i).addSource(bus.getSource(i));
        }
    }

    private void makeOutputConnections() {
        int count = Math.min(outputClient.getOutputCount(), bus.getSinkCount());
        for (int i = 0; i < count; i++) {
            bus.getSink(i).addSource(outputClient.getOutputSource(i));
        }
    }

    @Override
    protected void stopping() {
        if (bus == null) {
            return;
        }
        server.shutdown();
        bus.disconnectAll();
        bus.removeListener(delegate);
        server = null;
        bus = null;
        delegate = null;
        interrupt();
    }

    @Override
    protected void terminating() {
        super.terminating();
        AudioServer s = server;
        server = null;
        if (s != null) {
            s.shutdown();
        }
        PipesAudioClient b = bus;
        bus = null;
        if (b != null) {
            b.disconnectAll();
        }
    }

    @Override
    public ComponentInfo getInfo() {
        return info;
    }

    @Override
    public void write(TreeWriter writer) {
        super.write(writer);
        PMap sharedCodeValue = sharedCode.getValue();
        if (!sharedCodeValue.isEmpty()) {
            writer.writeProperty("shared-code", sharedCodeValue);
        }
        if (sampleRate.value.toIntValue() != DEFAULT_SAMPLERATE) {
            writer.writeProperty("sample-rate", sampleRate.value);
        }
        if (blockSize.value.toIntValue() != DEFAULT_BLOCKSIZE) {
            writer.writeProperty("block-size", blockSize.value);
        }
        if (!clientName.value.isEmpty()) {
            writer.writeProperty("client-name", clientName.value);
        }
        String lib = audioLib.value.toString();
        if (!lib.isEmpty()) {
            writer.writeProperty("library", audioLib.value);
            if (!"JACK".equals(lib)) {
                if (!deviceName.value.isEmpty()) {
                    writer.writeProperty("device", deviceName.value);
                }
                if (!inputDeviceName.value.isEmpty()) {
                    writer.writeProperty("input-device", inputDeviceName.value);
                }
                if (extBufferSize.value.toIntValue() != AudioSettings.getBuffersize()) {
                    writer.writeProperty("ext-buffer-size", extBufferSize.value);
                }
                if (!"Blocking".equals(timingMode.value.toString())) {
                    writer.writeProperty("timing-mode", timingMode.value);
                }
            }
        }
    }

    private void handleLog(LogBuilder log) {
        if (log.isEmpty()) {
            return;
        }
        getLookup().find(Services.class)
                .flatMap(srv -> srv.locate(LogService.class))
                .ifPresent(logger -> {
                    var to = ControlAddress.of(logger, LogService.LOG);
                    var from = ControlAddress.of(getAddress(), "_log");
                    var call = Call.createQuiet(to,
                            from,
                            getExecutionContext().getTime(),
                            log.toList());
                    getRouter().route(call);
                });
    }

    private class AudioDelegate extends Delegate
            implements PipesAudioClient.Listener {

        private final long offset;

        private AudioDelegate(Clock clock) {
            offset = System.nanoTime() - clock.getTime();
        }

        @Override
        public void configure(AudioConfiguration context) throws Exception {
            float srate = context.getSampleRate();
            if (Math.round(srate) != sampleRate.value.toIntValue()) {
                sampleRate.value = PNumber.of(Math.round(srate));
            }
            period = (long) ((blockSize.value.value()
                    / srate) * 1000000000);
        }

        @Override
        public void process() {
            try {
                boolean ok = doUpdate(bus.getTime() - offset);
                if (!ok && server != null) {
                    server.shutdown();
                }
            } catch (Exception ex) {
//                server.shutdown();
            }
        }

        @Override
        public void shutdown() {
            period = -1;
        }

        private void start() {
            var runner = getThreadFactory().newThread(() -> {
                try {
                    server.run();
                } catch (Exception ex) {
                    LOG.log(System.Logger.Level.ERROR, "", ex);
                }
                setIdle();
                detachDelegate(this);
            });
            runner.start();
        }
    }

    private class AudioCtxt extends AudioContext {

        @Override
        public int registerAudioInputClient(AudioContext.InputClient client) throws ClientRegistrationException {
            if (inputClient == null) {
                inputClient = client;
            } else {
                throw new ClientRegistrationException();
            }
            return MAX_CHANNELS;
        }

        @Override
        public void unregisterAudioInputClient(AudioContext.InputClient client) {
            if (inputClient == client) {
                inputClient = null;
                if (bus != null) {
                    bus.disconnectAll();
                    makeOutputConnections();
                }
            }
        }

        @Override
        public int registerAudioOutputClient(AudioContext.OutputClient client) throws ClientRegistrationException {
            if (outputClient == null) {
                outputClient = client;
            } else {
                throw new ClientRegistrationException();
            }
            return MAX_CHANNELS;
        }

        @Override
        public void unregisterAudioOutputClient(AudioContext.OutputClient client) {
            if (outputClient == client) {
                outputClient = null;
                if (bus != null) {
                    bus.disconnectAll();
                    setIdle();
                }
            }
        }

        @Override
        public double getSampleRate() {
            return sampleRate.value.value();
        }

        @Override
        public int getBlockSize() {
            return blockSize.value.toIntValue();
        }
    }

    private class Context extends DefaultExecutionContext {

        private Context(long time) {
            super(time);
        }

        @Override
        public OptionalLong getPeriod() {
            return OptionalLong.of(period);
        }

    }

    private class CheckedIntProperty extends AbstractProperty {

        private final int MIN;
        private final int MAX;

        private PNumber value;

        private CheckedIntProperty(int min, int max, int initial) {
            this.value = PNumber.of(initial);
            this.MIN = min;
            this.MAX = max;
        }

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new IllegalStateException("Can't set value while active");
            }
            PNumber val = PNumber.from(arg).orElseThrow(IllegalArgumentException::new);
            if (!val.isInteger()) {
                val = PNumber.of(val.toIntValue());
            }
            if (val.toIntValue() < MIN || val.toIntValue() > MAX) {
                throw new IllegalArgumentException("Out of range");
            }
            this.value = val;
        }

        @Override
        protected Value get() {
            return value;
        }

    }

    private class CheckedStringProperty extends AbstractProperty {

        private Value value;

        private CheckedStringProperty(Value initial) {
            this.value = initial;
        }

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new IllegalStateException("Can't set value while active");
            }
            this.value = arg;
        }

        @Override
        protected Value get() {
            return value;
        }

    }

    private class LibraryProperty extends AbstractProperty {

        private PString value = PString.EMPTY;

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new IllegalStateException("Can't set value while active");
            }
            PString value = PString.from(arg).orElse(PString.EMPTY);
            if (this.value.equals(value)) {
                return;
            }
            if (value.isEmpty() || libraries.containsKey(value.toString())) {
                this.value = value;
                updateLibrary(value.toString());
            }
        }

        @Override
        protected Value get() {
            return value;
        }

    }

    private class DeviceProperty extends AbstractProperty {

        private PString value = PString.EMPTY;

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new IllegalStateException("Can't set value while active");
            }
            PString value = PString.from(arg).orElse(PString.EMPTY);
            if (this.value.equals(value)) {
                return;
            }
            this.value = value;
            validateDevices();
        }

        @Override
        protected Value get() {
            return value;
        }

    }

    private class TimingModeProperty extends AbstractProperty {

        private PString value = PString.of("Blocking");

        @Override
        protected void set(long time, Value arg) throws Exception {
            if (getState() == State.ACTIVE_RUNNING) {
                throw new IllegalStateException("Can't set value while active");
            }
            switch (arg.toString()) {
                case "Blocking":
                case "Estimated":
                case "FramePosition":
                    value = PString.of(arg);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown timing mode value " + arg);
            }
        }

        @Override
        protected Value get() {
            return value;
        }

    }

    private static class LibraryInfo {

        private final AudioServerProvider provider;
        private final List<Device> devices;
        private final List<Device> inputDevices;

        private LibraryInfo(AudioServerProvider provider,
                List<Device> devices, List<Device> inputDevices) {
            this.provider = provider;
            this.devices = devices;
            this.inputDevices = inputDevices;
        }

    }
}
