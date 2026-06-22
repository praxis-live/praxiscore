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
package org.praxislive.code;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.code.userapi.Config;
import org.praxislive.code.userapi.OnChange;
import org.praxislive.code.userapi.OnError;
import org.praxislive.code.userapi.P;
import org.praxislive.code.userapi.Type;
import org.praxislive.core.Value;
import org.praxislive.core.Control;
import org.praxislive.core.Lookup;
import org.praxislive.core.Port;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.Info;
import org.praxislive.core.PortInfo;
import org.praxislive.core.TreeWriter;
import org.praxislive.core.services.TaskService;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBytes;

/**
 *
 */
public final class ResourceProperty<V> extends AbstractAsyncProperty<V> {

    private final Loader<V> loader;
    private Field field;
    private Method onChange;
    private Method onError;
    private CodeContext<?> context;

    private ResourceProperty(Loader<V> loader) {
        super(PString.EMPTY, loader.getType(), null);
        this.loader = loader;
    }

    private void attach(CodeContext<?> context,
            Field field, Method onChange, Method onError) {
        super.attach(context);
        this.context = context;
        this.field = field;
        setFieldValue();
        this.onChange = onChange;
        this.onError = onError;
    }

    private void setFieldValue() {
        try {
            V v = getValue();
            field.set(context.getDelegate(), v == null ? loader.getEmptyValue() : v);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            context.getLog().log(LogLevel.ERROR, ex);
        }
    }

    @Override
    protected TaskService.Task createTask(Value key) throws Exception {
        if (key.isEmpty()) {
            return null;
        }
        Lookup lkp = context.getLookup();
        return new Task(loader, lkp, PResource.from(key)
                .orElseThrow(IllegalArgumentException::new));
    }

    @Override
    protected void valueChanged(long time) {
        setFieldValue();
        if (onChange != null) {
            context.invoke(time, onChange);
        }
    }

    @Override
    protected void taskError(long time, PError error) {
        if (onError != null) {
            context.invoke(time, onError);
        }
    }

    private static class Task implements TaskService.Task {

        private final PResource resource;
        private final Lookup lookup;
        private final Loader<?> loader;

        private Task(Loader<?> loader, Lookup lookup, PResource resource) {
            this.loader = loader;
            this.lookup = lookup;
            this.resource = resource;
        }

        @Override
        public Value execute() throws Exception {
            List<URI> uris = resource.resolve(lookup);
            Exception caughtException = null;
            for (URI uri : uris) {
                try {
                    if ("file".equals(uri.getScheme())) {
                        if (!new File(uri).exists()) {
                            continue;
                        }
                    }
                    Object ret = loader.load(uri);
                    if (ret instanceof Value) {
                        return (Value) ret;
                    } else {
                        return PReference.of(ret);
                    }
                } catch (Exception exception) {
                    caughtException = exception;
                }
            }
            if (caughtException == null) {
                caughtException = new IOException("Unknown resource");
            }
            throw caughtException;
        }

    }

    public static abstract class Loader<V> {

        private final Class<V> type;

        protected Loader(Class<V> type) {
            this.type = Objects.requireNonNull(type);
        }

        public final Class<V> getType() {
            return type;
        }

        public abstract V load(URI uri) throws IOException;

        public V getEmptyValue() {
            return null;
        }

    }

    public static Loader<String> getStringLoader() {
        return StringLoader.INSTANCE;
    }

    public static Loader<PBytes> getBytesLoader() {
        return PBytesLoader.INSTANCE;
    }

    // @TODO in v7 use readAllAsString - keep existing line delimiters
    private static class StringLoader extends Loader<String> {

        private static final StringLoader INSTANCE = new StringLoader();

        private StringLoader() {
            super(String.class);
        }

        @Override
        public String load(URI uri) throws IOException {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(uri.toURL().openStream(), StandardCharsets.UTF_8)
            )) {
                return br.lines().collect(Collectors.joining("\n"));
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        @Override
        public String getEmptyValue() {
            return "";
        }

    }

    private static class PBytesLoader extends Loader<PBytes> {

        private static final PBytesLoader INSTANCE = new PBytesLoader();

        private PBytesLoader() {
            super(PBytes.class);
        }

        @Override
        public PBytes load(URI uri) throws IOException {
            try (BufferedInputStream bis = new BufferedInputStream(
                    uri.toURL().openStream())) {
                PBytes.OutputStream bos = new PBytes.OutputStream();
                bis.transferTo(bos);
                return bos.toBytes();
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        @Override
        public PBytes getEmptyValue() {
            return PBytes.EMPTY;
        }

    }

    public static class Descriptor extends ControlDescriptor<Descriptor> {

        private final Loader<?> loader;
        private final Field field;
        private final Method onChange, onError;
        private final ControlInfo info;

        private ResourceProperty<?> control;

        private Descriptor(
                String id,
                int index,
                Field field,
                Loader<?> loader,
                Method onChange,
                Method onError
        ) {
            super(Descriptor.class, id, Category.Property, index);
            this.loader = loader;
            this.field = field;
            this.onChange = onChange;
            this.onError = onError;
            PArray mimeTypes = Optional.ofNullable(field.getAnnotation(Type.Resource.class))
                    .map(ann -> {
                        String[] mimes = ann.mime();
                        if (mimes.length == 0) {
                            return PArray.EMPTY;
                        } else {
                            return Stream.of(mimes)
                                    .map(PString::of)
                                    .collect(PArray.collector());
                        }
                    }).orElse(PArray.EMPTY);
            Info.PropertyInfoBuilder infoBld = Info.control().property()
                    .input(a -> {
                        Info.ValueInfoBuilder bld = a.type(PResource.class)
                                .property(PResource.KEY_ALLOW_EMPTY, true);
                        if (!mimeTypes.isEmpty()) {
                            bld.property(PResource.KEY_MIME_TYPES, mimeTypes);
                        }
                        return bld;
                    })
                    .defaultValue(PString.EMPTY);

            boolean preferred = field.isAnnotationPresent(Config.Preferred.class);
            if (preferred) {
                infoBld.property("preferred", true);
            }
            this.info = infoBld.build();
        }

        @Override
        public ControlInfo controlInfo() {
            return info;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void attach(CodeContext<?> context, Descriptor previous) {
            if (previous != null && previous.loader.getType() == loader.getType()) {
                control = previous.control;
            } else {
                if (previous != null) {
                    previous.dispose();
                }
                control = new ResourceProperty<>(loader);
            }
            control.attach(context, field, onChange, onError);
        }

        @Override
        public Control control() {
            return control;
        }

        @Override
        public void write(TreeWriter writer) {
            Value v = control.getKey();
            if (!v.isEmpty()) {
                writer.writeProperty(id(), v);
            }
        }

        public PortDescriptor createPortDescriptor() {
            return new PortDescImpl(id(), index(), this);
        }

        public static Descriptor create(CodeConnector<?> connector, P ann,
                Field field, Loader<?> loader) {
            if (!field.getType().isAssignableFrom(loader.getType())) {
                return null;
            }
            field.setAccessible(true);
            String id = connector.findID(field);
            int index = ann.value();
            Method onChange = null;
            Method onError = null;
            OnChange onChangeAnn = field.getAnnotation(OnChange.class);
            if (onChangeAnn != null) {
                onChange = extractMethod(connector, onChangeAnn.value());
            }
            OnError onErrorAnn = field.getAnnotation(OnError.class);
            if (onErrorAnn != null) {
                onError = extractMethod(connector, onErrorAnn.value());
            }
            return new Descriptor(id, index, field, loader, onChange, onError);
        }

        private static Method extractMethod(CodeConnector<?> connector, String methodName) {
            try {
                Method m = connector.getDelegate().getClass().getDeclaredMethod(methodName);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException | SecurityException ex) {
                connector.getLog().log(LogLevel.WARNING, ex);
                return null;
            }
        }

    }

    private static class PortDescImpl extends PortDescriptor<PortDescImpl>
            implements ControlInput.Link {

        private final Descriptor dsc;

        private ControlInput port;

        private PortDescImpl(String id, int index, Descriptor dsc) {
            super(PortDescImpl.class, id, PortDescriptor.Category.Property, index);
            this.dsc = dsc;
        }

        @Override
        public void attach(CodeContext<?> context, PortDescImpl previous) {
            if (previous != null) {
                port = previous.port;
                port.setLink(this);
            } else {
                if (previous != null) {
                    previous.dispose();
                }
                port = new ControlInput(this);
            }
        }

        @Override
        public Port port() {
            assert port != null;
            return port;
        }

        @Override
        public PortInfo portInfo() {
            return ControlInput.INFO;
        }

        @Override
        public void receive(long time, double value) {
            receive(time, PNumber.of(value));
        }

        @Override
        public void receive(long time, Value value) {
            dsc.control.portInvoke(time, value);
        }

    }

}
