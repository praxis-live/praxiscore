/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2023 Neil C Smith.
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
package org.praxislive.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 * Builder utilities for creating {@link ComponentInfo} and related classes.
 */
public class Info {

    private Info() {
    }

    /**
     * Create a ComponentInfoBuilder
     *
     * @return builder
     */
    public static ComponentInfoBuilder component() {
        return new ComponentInfoBuilder();
    }

    /**
     * Apply the provided function to a new ComponentInfoBuilder and return the
     * resulting ComponentInfo.
     *
     * @param cmp function to modify builder
     * @return ComponentInfo from builder
     */
    public static ComponentInfo component(UnaryOperator<ComponentInfoBuilder> cmp) {
        return cmp.apply(Info.component()).build();
    }

    /**
     * Get a PortInfoChooser to choose a PortInfoBuilder
     *
     * @return builder chooser
     */
    public static PortInfoChooser port() {
        return new PortInfoChooser();
    }

    /**
     * Apply the provided function to a PortInfoChooser to choose and customize
     * a PortInfoBuilder and return the resulting PortInfo.
     *
     * @param p function to choose and configure builder
     * @return PortInfo from builder
     */
    public static PortInfo port(Function<PortInfoChooser, PortInfoBuilder> p) {
        return p.apply(Info.port()).build();
    }

    /**
     * Get a ControlInfoChooser to choose a ControlInfoBuilder
     *
     * @return builder chooser
     */
    public static ControlInfoChooser control() {
        return new ControlInfoChooser();
    }

    /**
     * Apply the provided function to a ControlInfoChooser to choose and
     * customize a ControlInfoBuilder and return the resulting ControlInfo.
     *
     * @param c function to choose and configure builder
     * @return ControlInfo from builder
     */
    public static ControlInfo control(Function<ControlInfoChooser, ControlInfoBuilder<?>> c) {
        return c.apply(Info.control()).build();
    }

    /**
     * Get an ArgumentInfoChooser to choose an ArgumentInfoBuilder.
     *
     * @return builder chooser
     */
    public static ArgumentInfoChooser argument() {
        return new ArgumentInfoChooser();
    }

    /**
     * Apply the provided function to an ArgumentInfoChooser to choose and
     * configure an ArgumentInfoBuilder and return the resulting ArgumentInfo.
     *
     * @param a function to choose and configure builder
     * @return ArgumentInfo from builder
     */
    public static ArgumentInfo argument(Function<ArgumentInfoChooser, ArgumentInfoBuilder<?>> a) {
        return a.apply(Info.argument()).build();
    }

    /**
     * ComponentInfoBuilder class
     */
    public final static class ComponentInfoBuilder {

        private static final List<String> RESERVED_KEYS = List.of(
                ComponentInfo.KEY_CONTROLS, ComponentInfo.KEY_PORTS,
                ComponentInfo.KEY_PROTOCOLS);

        private final Map<String, ControlInfo> controls;
        private final Map<String, PortInfo> ports;
        private final Set<String> protocols;
        private PMap.Builder properties;

        ComponentInfoBuilder() {
            controls = new LinkedHashMap<>();
            ports = new LinkedHashMap<>();
            protocols = new LinkedHashSet<>();
        }

        /**
         * Add control info.
         *
         * @param id control ID
         * @param info control info
         * @return this
         */
        public ComponentInfoBuilder control(String id, ControlInfo info) {
            controls.put(id, info);
            return this;
        }

        /**
         * Add control info by applying the supplied function to choose and
         * configure a builder.
         *
         * @param id control ID
         * @param ctrl function to choose and configure builder
         * @return this
         */
        public ComponentInfoBuilder control(String id,
                Function<ControlInfoChooser, ControlInfoBuilder<?>> ctrl) {
            control(id, ctrl.apply(new ControlInfoChooser()).build());
            return this;
        }

        /**
         * Add port info.
         *
         * @param id port ID
         * @param info port info
         * @return this
         */
        public ComponentInfoBuilder port(String id, PortInfo info) {
            ports.put(id, info);
            return this;
        }

        /**
         * Add port info by applying the supplied function to choose and
         * configure a builder.
         *
         * @param id port ID
         * @param p function to choose and configure builder
         * @return this
         */
        public ComponentInfoBuilder port(String id,
                Function<PortInfoChooser, PortInfoBuilder> p) {
            port(id, Info.port(p));
            return this;
        }

        /**
         * Add custom property.
         *
         * @param key String key
         * @param value Object value
         * @return this
         */
        public ComponentInfoBuilder property(String key, Object value) {
            if (RESERVED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Reserved key");
            }
            if (properties == null) {
                properties = PMap.builder();
            }
            properties.put(key, value);
            return this;
        }

        /**
         * Add a protocol.
         *
         * @param protocol Class extending Protocol
         * @return this
         */
        public ComponentInfoBuilder protocol(Class<? extends Protocol> protocol) {
            return protocol(Protocol.Type.of(protocol).name());
        }

        /**
         * Add a protocol.
         *
         * @param protocol protocol name
         * @return this
         */
        public ComponentInfoBuilder protocol(String protocol) {
            if (!protocols.contains(protocol)) {
                protocols.add(protocol);
            }
            return this;
        }

        /**
         * Merge all elements of the provided ComponentInfo.
         *
         * @param info ComponentInfo to merge
         * @return this
         */
        public ComponentInfoBuilder merge(ComponentInfo info) {
            for (String id : info.controls()) {
                controls.put(id, info.controlInfo(id));
            }
            for (String id : info.ports()) {
                ports.put(id, info.portInfo(id));
            }
            for (String key : info.properties().keys()) {
                if (!RESERVED_KEYS.contains(key)) {
                    property(key, info.properties().get(key));
                }
            }
            info.protocols().forEach(this::protocol);
            return this;
        }

        public ComponentInfo build() {
            return ComponentInfo.create(controls, ports, protocols,
                    properties == null ? PMap.EMPTY : properties.build());
        }

    }

    /**
     * Helper class for choosing a ControlInfoBuilder type.
     */
    public final static class ControlInfoChooser {

        ControlInfoChooser() {
        }

        /**
         * Create a PropertyInfoBuilder for a property.
         *
         * @return builder
         */
        public PropertyInfoBuilder property() {
            return new PropertyInfoBuilder();
        }

        /**
         * Create a ReadOnlyPropertyBuilder for a property.
         *
         * @return builder
         */
        public ReadOnlyPropertyInfoBuilder readOnlyProperty() {
            return new ReadOnlyPropertyInfoBuilder();
        }

        /**
         * Create a FunctionInfoBuilder
         *
         * @return builder
         */
        public FunctionInfoBuilder function() {
            return new FunctionInfoBuilder();
        }

        /**
         * Create an ActionInfoBuilder
         *
         * @return builder
         */
        public ActionInfoBuilder action() {
            return new ActionInfoBuilder();
        }

    }

    /**
     * Abstract base class for ControlInfo builders.
     *
     * @param <T> concrete builder type
     */
    public static abstract class ControlInfoBuilder<T extends ControlInfoBuilder<T>> {

        private static final List<String> RESERVED_KEYS = List.of(
                ControlInfo.KEY_TYPE, ControlInfo.KEY_INPUTS,
                ControlInfo.KEY_OUTPUTS, ControlInfo.KEY_DEFAULTS
        );

        private final ControlInfo.Type type;
        private PMap.Builder properties;

        List<ArgumentInfo> inputs;
        List<ArgumentInfo> outputs;
        List<Value> defaults;

        ControlInfoBuilder(ControlInfo.Type type) {
            this.type = type;
            inputs = List.of();
            outputs = List.of();
            defaults = List.of();
        }

        /**
         * Add custom property.
         *
         * @param key String key
         * @param value Object value
         * @return this
         */
        @SuppressWarnings("unchecked")
        public T property(String key, Object value) {
            if (RESERVED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Reserved key");
            }
            if (properties == null) {
                properties = PMap.builder();
            }
            properties.put(key, value);
            return (T) this;
        }

        public abstract ControlInfo build();

        PMap buildProperties() {
            return properties == null ? PMap.EMPTY : properties.build();
        }

    }

    /**
     * Builder for ControlInfo of property controls.
     */
    public static final class PropertyInfoBuilder extends ControlInfoBuilder<PropertyInfoBuilder> {

        PropertyInfoBuilder() {
            super(ControlInfo.Type.Property);
        }

        /**
         * Add input ArgumentInfo.
         *
         * @param info
         * @return this
         */
        public PropertyInfoBuilder input(ArgumentInfo info) {
            inputs = List.of(info);
            outputs = inputs;
            return this;
        }

        /**
         * Add input ArgumentInfo for the provided value type.
         *
         * @param type value type
         * @return this
         */
        public PropertyInfoBuilder input(Class<? extends Value> type) {
            return input(ArgumentInfo.of(type));
        }

        /**
         * Add input ArgumentInfo by applying the provided function to choose
         * and configure an ArgumentInfoBuilder.
         *
         * @param a function to choose and configure builder
         * @return this
         */
        public PropertyInfoBuilder input(Function<ArgumentInfoChooser, ArgumentInfoBuilder<?>> a) {
            return input(Info.argument(a));
        }

        /**
         * Add a default value for this property.
         *
         * @param value default value
         * @return this
         */
        public PropertyInfoBuilder defaultValue(Value value) {
            defaults = List.of(value);
            return this;
        }

        @Override
        public ControlInfo build() {
            return ControlInfo.createPropertyInfo(inputs, defaults, buildProperties());
        }

    }

    /**
     * Builder for ControlInfo of read-only properties.
     */
    public static final class ReadOnlyPropertyInfoBuilder extends ControlInfoBuilder<ReadOnlyPropertyInfoBuilder> {

        ReadOnlyPropertyInfoBuilder() {
            super(ControlInfo.Type.ReadOnlyProperty);
        }

        /**
         * Add output ArgumentInfo.
         *
         * @param info
         * @return this
         */
        public ReadOnlyPropertyInfoBuilder output(ArgumentInfo info) {
            outputs = List.of(info);
            return this;
        }

        /**
         * Add output ArgumentInfo for the provided value type.
         *
         * @param type value type
         * @return this
         */
        public ReadOnlyPropertyInfoBuilder output(Class<? extends Value> type) {
            return output(ArgumentInfo.of(type));
        }

        /**
         * Add output ArgumentInfo by applying the provided function to choose
         * and configure an ArgumentInfoBuilder.
         *
         * @param a function to choose and configure builder
         * @return this
         */
        public ReadOnlyPropertyInfoBuilder output(Function<ArgumentInfoChooser, ArgumentInfoBuilder<?>> a) {
            return output(Info.argument(a));
        }

        @Override
        public ControlInfo build() {
            return ControlInfo.createReadOnlyPropertyInfo(outputs, buildProperties());
        }

    }

    /**
     * Builder for ControlInfo for function controls.
     */
    public static final class FunctionInfoBuilder extends ControlInfoBuilder<FunctionInfoBuilder> {

        FunctionInfoBuilder() {
            super(ControlInfo.Type.Function);
        }

        /**
         * Add ArgumentInfo for function inputs.
         *
         * @param inputs info for inputs
         * @return this
         */
        public FunctionInfoBuilder inputs(ArgumentInfo... inputs) {
            this.inputs = List.of(inputs);
            return this;
        }

        /**
         * Add ArgumentInfo for function inputs by applying the provided
         * functions to choose and configure ArgumentInfoBuilders.
         *
         * @param inputs functions to choose and configure builders
         * @return this
         */
        @SafeVarargs
        public final FunctionInfoBuilder inputs(Function<ArgumentInfoChooser, ArgumentInfoBuilder<?>>... inputs) {
            return inputs(Stream.of(inputs).map(f -> Info.argument(f)).toArray(ArgumentInfo[]::new));
        }

        /**
         * Add ArgumentInfo for function outputs.
         *
         * @param outputs info for outputs
         * @return this
         */
        public FunctionInfoBuilder outputs(ArgumentInfo... outputs) {
            this.outputs = List.of(outputs);
            return this;
        }

        /**
         * Add ArgumentInfo for function outputs by applying the provided
         * functions to choose and configure ArgumentInfoBuilders.
         *
         * @param outputs functions to choose and configure builders
         * @return this
         */
        public FunctionInfoBuilder outputs(Function<ArgumentInfoChooser, ArgumentInfoBuilder<?>>... outputs) {
            return outputs(Stream.of(outputs).map(f -> Info.argument(f)).toArray(ArgumentInfo[]::new));
        }

        @Override
        public ControlInfo build() {
            return ControlInfo.createFunctionInfo(inputs, outputs, buildProperties());
        }

    }

    /**
     * Builder for ControlInfo of action controls.
     */
    public static final class ActionInfoBuilder extends ControlInfoBuilder<ActionInfoBuilder> {

        ActionInfoBuilder() {
            super(ControlInfo.Type.Action);
        }

        @Override
        public ControlInfo build() {
            return ControlInfo.createActionInfo(buildProperties());
        }

    }

    /**
     * Helper class for choosing an ArgumentInfoBuilder type.
     */
    public static final class ArgumentInfoChooser {

        ArgumentInfoChooser() {
        }

        /**
         * Create a ValueInfoBuilder for the provided value type.
         *
         * @param cls type of value
         * @return builder
         */
        public ValueInfoBuilder type(Class<? extends Value> cls) {
            return type(Value.Type.of(cls).name());
        }

        /**
         * Create a ValueInfoBuilder for the provided value type.
         *
         * @param type name of value type
         * @return builder
         */
        public ValueInfoBuilder type(String type) {
            return new ValueInfoBuilder(type);
        }

        /**
         * Create a NumberInfoBuilder for numeric values.
         *
         * @return builder
         */
        public NumberInfoBuilder number() {
            return new NumberInfoBuilder();
        }

        /**
         * Create a StringInfoBuilder for string values.
         *
         * @return builder
         */
        public StringInfoBuilder string() {
            return new StringInfoBuilder();
        }

    }

    /**
     * Abstract base class for ArgumentInfoBuilders.
     *
     * @param <T> concrete builder type
     */
    public static abstract class ArgumentInfoBuilder<T extends ArgumentInfoBuilder<T>> {

        private final String type;
        private PMap.Builder properties;

        ArgumentInfoBuilder(String type) {
            this.type = type;
        }

        /**
         * Add custom property.
         *
         * @param key String key
         * @param value Object value
         * @return this
         */
        @SuppressWarnings("unchecked")
        public T property(String key, Value value) {
            if (ArgumentInfo.KEY_TYPE.equals(key)) {
                throw new IllegalArgumentException("Reserved key");
            }
            if (properties == null) {
                properties = PMap.builder();
            }
            properties.put(key, value);
            return (T) this;
        }

        public ArgumentInfo build() {
            return ArgumentInfo.create(type,
                    properties == null ? PMap.EMPTY : properties.build());
        }

    }

    /**
     * Builder for ArgumentInfo of any Value type.
     */
    public static final class ValueInfoBuilder extends ArgumentInfoBuilder<ValueInfoBuilder> {

        ValueInfoBuilder(String type) {
            super(type);
        }

    }

    /**
     * Builder for ArgumentInfo of PNumber.
     */
    public static final class NumberInfoBuilder extends ArgumentInfoBuilder<NumberInfoBuilder> {

        NumberInfoBuilder() {
            super(PNumber.TYPE_NAME);
        }

        /**
         * Set minimum value property.
         *
         * @param min minimum value
         * @return this
         */
        public NumberInfoBuilder min(double min) {
            return property(PNumber.KEY_MINIMUM, PNumber.of(min));
        }

        /**
         * Set maximum value property.
         *
         * @param max maximum value
         * @return this
         */
        public NumberInfoBuilder max(double max) {
            return property(PNumber.KEY_MAXIMUM, PNumber.of(max));
        }

        /**
         * Set skew value property.
         *
         * @param skew skew value
         * @return this
         */
        public NumberInfoBuilder skew(double skew) {
            return property(PNumber.KEY_MINIMUM, PNumber.of(skew));
        }

    }

    /**
     * Builder for ArgumentInfo of PString.
     */
    public static final class StringInfoBuilder extends ArgumentInfoBuilder<StringInfoBuilder> {

        StringInfoBuilder() {
            super(PString.TYPE_NAME);
        }

        /**
         * Set allowed values property.
         *
         * @param values allowed values
         * @return this
         */
        public StringInfoBuilder allowed(String... values) {
            return property(ArgumentInfo.KEY_ALLOWED_VALUES,
                    Stream.of(values).map(PString::of).collect(PArray.collector()));
        }

        /**
         * Set suggested values property.
         *
         * @param values suggested values
         * @return this
         */
        public StringInfoBuilder suggested(String... values) {
            return property(ArgumentInfo.KEY_SUGGESTED_VALUES,
                    Stream.of(values).map(PString::of).collect(PArray.collector()));
        }

        /**
         * Set empty is default property.
         *
         * @return this
         */
        public StringInfoBuilder emptyIsDefault() {
            return property(ArgumentInfo.KEY_EMPTY_IS_DEFAULT, PBoolean.TRUE);
        }

        /**
         * Set the template property.
         *
         * @param template
         * @return this
         */
        public StringInfoBuilder template(String template) {
            return property(ArgumentInfo.KEY_TEMPLATE, PString.of(template));
        }

        /**
         * Set the mime type property.
         *
         * @param mime
         * @return this
         */
        public StringInfoBuilder mime(String mime) {
            return property(ArgumentInfo.KEY_MIME_TYPE, PString.of(mime));
        }

    }

    /**
     * Helper class to choose a PortInfoBuilder type.
     */
    public static final class PortInfoChooser {

        PortInfoChooser() {
        }

        /**
         * Create a PortInfoBuilder for input ports of the provided base type.
         *
         * @param type base Port type
         * @return builder
         */
        public PortInfoBuilder input(Class<? extends Port> type) {
            return input(Port.Type.of(type).name());
        }

        /**
         * Create a PortInfoBuilder for input ports of the provided base type.
         *
         * @param type base Port type name
         * @return builder
         */
        public PortInfoBuilder input(String type) {
            return new PortInfoBuilder(type, PortInfo.Direction.IN);
        }

        /**
         * Create a PortInfoBuilder for output ports of the provided base type.
         *
         * @param type base Port type
         * @return builder
         */
        public PortInfoBuilder output(Class<? extends Port> type) {
            return output(Port.Type.of(type).name());
        }

        /**
         * Create a PortInfoBuilder for output ports of the provided base type.
         *
         * @param type base Port type name
         * @return builder
         */
        public PortInfoBuilder output(String type) {
            return new PortInfoBuilder(type, PortInfo.Direction.OUT);
        }

    }

    /**
     * PortInfoBuilder
     */
    public static final class PortInfoBuilder {

        private final String type;
        private final PortInfo.Direction direction;
        private PMap.Builder properties;

        PortInfoBuilder(String type, PortInfo.Direction direction) {
            this.type = type;
            this.direction = direction;
        }

        /**
         * Add custom property.
         *
         * @param key String key
         * @param value Object value
         * @return this
         */
        public PortInfoBuilder property(String key, Object value) {
            if (properties == null) {
                properties = PMap.builder();
            }
            properties.put(key, value);
            return this;
        }

        public PortInfo build() {
            return PortInfo.create(type, direction,
                    properties == null ? PMap.EMPTY : properties.build());
        }

    }

}
