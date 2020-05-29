/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2020 Neil C Smith.
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
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.praxislive.core.Clock;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.ContainerProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 *
 */
public class DefaultAudioRoot extends AbstractRootContainer {

    private static final Logger LOG = Logger.getLogger(DefaultAudioRoot.class.getName());
    private static final int MAX_CHANNELS = 16;
    private static final int MIN_SAMPLERATE = 2000;
    private static final int MAX_SAMPLERATE = 192000;
    private static final int DEFAULT_SAMPLERATE = 48000;
    private static final int MAX_BLOCKSIZE = 512;
    private static final int DEFAULT_BLOCKSIZE = 64;

    private Map<String, LibraryInfo> libraries;
    private AudioContext.InputClient inputClient;
    private AudioContext.OutputClient outputClient;
    private PipesAudioClient bus;
    private AudioDelegate delegate;
    private AudioServer server;

    // Permanent controls 
    private final CheckedIntProperty sampleRate;
    private final CheckedIntProperty blockSize;
    private final LibraryProperty audioLib;

    // Dynamic controls
    private CheckedIntProperty extBufferSize;
    private DeviceProperty deviceName;
    private DeviceProperty inputDeviceName;

    private final ComponentInfo baseInfo;
    private ComponentInfo info;

    private final AudioContext audioCtxt;
    private Lookup lookup;
    private long period = -1;

    public DefaultAudioRoot() {
        extractLibraryInfo();

        sampleRate = new CheckedIntProperty(MIN_SAMPLERATE, MAX_SAMPLERATE, DEFAULT_SAMPLERATE);
        registerControl("sample-rate", sampleRate);
        blockSize = new CheckedIntProperty(1, MAX_BLOCKSIZE, DEFAULT_BLOCKSIZE);
        registerControl("block-size", blockSize);
        audioLib = new LibraryProperty();
        registerControl("library", audioLib);

        baseInfo = Info.component(cmp -> cmp
                .merge(ComponentProtocol.API_INFO)
                .merge(ContainerProtocol.API_INFO)
                .merge(StartableProtocol.API_INFO)
                .control("sample-rate", c -> c
                .property()
                .defaultValue(PNumber.of(DEFAULT_SAMPLERATE))
                .input(a -> a
                .number().min(MIN_SAMPLERATE).max(MAX_SAMPLERATE)
                .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                ))
                .control("block-size", c -> c
                .property()
                .defaultValue(PNumber.of(DEFAULT_BLOCKSIZE))
                .input(a -> a
                .number().min(1).max(MAX_BLOCKSIZE)
                .property(PNumber.KEY_IS_INTEGER, PBoolean.TRUE)
                ))
                .control("library", c -> c
                .property()
                .defaultValue(PString.EMPTY)
                .input(a -> a
                .string()
                .emptyIsDefault()
                .allowed(
                        Stream.concat(Stream.of(""),
                                libraries.keySet().stream().sorted())
                                .toArray(String[]::new)
                )
                ))
        );

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
            LOG.log(Level.FINE, "Audio Library : {0}", lib.getLibraryName());
            devices.clear();
            inputDevices.clear();
            for (Device device : lib.findAll(Device.class)) {
                if (device.getMaxOutputChannels() > 0) {
                    LOG.log(Level.FINE, "-- Found device : {0}", device.getName());
                    devices.add(device);
                } else if (device.getMaxInputChannels() > 0) {
                    LOG.log(Level.FINE, "-- Found input device : {0}", device.getName());
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
        deviceName = null;
        unregisterControl("input-device");
        inputDeviceName = null;
        unregisterControl("ext-buffer-size");
        extBufferSize = null;

        if (lib.isEmpty()) {
            return;
        }

        LibraryInfo info = libraries.get(lib);
        if (info == null) {
            return;
        }

//        if (!"JACK".equals(lib)) {
//            deviceName = new DeviceProperty();
//            StringProperty devCtl = StringProperty.builder()
//                    .binding(deviceName)
//                    .defaultValue("")
//                    .emptyIsDefault()
//                    .suggestedValues(deviceNames(info.devices))
//                    .build();
//            registerControl("device", devCtl);
//            inputDeviceName = new DeviceProperty();
//            StringProperty inCtl = StringProperty.builder()
//                    .binding(inputDeviceName)
//                    .defaultValue("")
//                    .emptyIsDefault()
//                    .suggestedValues(deviceNames(info.inputDevices))
//                    .build();
//            registerControl("input-device", inCtl);
//            extBufferSize = new CheckedIntProperty(AudioSettings.getBuffersize());
//            IntProperty bsCtl = IntProperty.builder()
//                    .binding(extBufferSize)
//                    .suggestedValues(64, 128, 256, 512, 1024, 2048, 4096)
//                    .build();
//            registerControl("ext-buffer-size", bsCtl);
//        }
    }

    @Override
    public Lookup getLookup() {
        if (lookup == null) {
            lookup = Lookup.of(super.getLookup(), audioCtxt);
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
            Logger.getLogger(DefaultAudioRoot.class.getName()).log(Level.SEVERE, null, ex);
            setIdle();
        }

    }

    private AudioServer createServer(PipesAudioClient bus) throws Exception {
        float srate = sampleRate.value.toIntValue();
        int buffersize = getBuffersize();

        boolean usingDefault = false;
        LibraryInfo info = libraries.get(audioLib.value.value());
        if (info == null) {
            info = libraries.get(AudioSettings.getLibrary());
            if (info == null) {
                throw new IllegalStateException("Audio library not found");
            }
            usingDefault = true;
        }
        LOG.log(Level.FINE, "Found audio library {0}\n{1}", new Object[]{
            info.provider.getLibraryName(), info.provider.getLibraryDescription()
        });

        Device device = findDevice(info, usingDefault, false);
        if (device != null) {
            LOG.log(Level.FINE, "Found device : {0}", device.getName());
        }
        Device inputDevice = null;
        if (device != null && device.getMaxInputChannels() == 0 && bus.getSourceCount() > 0) {
            inputDevice = findDevice(info, usingDefault, true);
            if (inputDevice != null) {
                LOG.log(Level.FINE, "Found input device : {0}", inputDevice.getName());
            }
        }

        ClientID clientID = new ClientID("PraxisLIVE-" + getAddress().rootID());

        AudioConfiguration ctxt = new AudioConfiguration(srate,
                bus.getSourceCount(),
                bus.getSinkCount(),
                buffersize,
                createCheckedExts(device, inputDevice, clientID)
        );
        return info.provider.createServer(ctxt, bus);
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
        LOG.log(Level.FINE, "Requesting buffersize of : {0}", bsize);
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

    private String[] deviceNames(Device[] devices) {
        String[] names = new String[devices.length + 1];
        names[0] = "";
        for (int i = 0; i < devices.length; i++) {
            names[i + 1] = devices[i].getName();
        }
        return names;
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
        return baseInfo;
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
                if (!ok) {
                    server.shutdown();
                }
            } catch (Exception ex) {
                server.shutdown();
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
                    Logger.getLogger(DefaultAudioRoot.class.getName()).log(Level.SEVERE, null, ex);
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

    private class CheckedStringBinding extends AbstractProperty {

        private Value value;

        private CheckedStringBinding(Value initial) {
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
