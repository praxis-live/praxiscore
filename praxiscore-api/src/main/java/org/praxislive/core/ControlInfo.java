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
package org.praxislive.core;

import java.util.List;
import java.util.Optional;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;

/**
 * Information on the type, inputs, outputs and properties of a {@link Control}.
 */
public final class ControlInfo extends PMap.MapBasedValue {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "ControlInfo";

    /**
     * Map key for the control type.
     */
    public static final String KEY_TYPE = "type";

    /**
     * Map key for the inputs list of {@link ArgumentInfo}.
     */
    public static final String KEY_INPUTS = "inputs";

    /**
     * Map key for the outputs list of {@link ArgumentInfo}.
     */
    public static final String KEY_OUTPUTS = "outputs";

    /**
     * Map key for the list of default {@link Value}s. Only relevant for
     * properties.
     */
    public static final String KEY_DEFAULTS = "defaults";

    /**
     * Optional map key to mark a property as transient, and so not to be
     * included in any serialized representation of the component state.
     */
    public static final String KEY_TRANSIENT = "transient";

    /**
     * Optional map key to mark a control as deprecated.
     */
    public static final String KEY_DEPRECATED = "deprecated";

    /**
     * Optional map key to mark a control as relating to expert / advanced
     * usage. A user interface might choose to display such a control in a
     * different way.
     */
    public static final String KEY_EXPERT = "expert";

    /**
     * The types of a control.
     */
    public static enum Type {

        /**
         * A control that acts as a function, taking any number of input
         * arguments, and responding with any number of output arguments.
         */
        Function,
        /**
         * A control that triggers an action. An action control always has no
         * input or output arguments.
         */
        Action,
        /**
         * A control that acts as a property. A property control accepts an
         * optional single input argument to set the property, and always
         * responds with a single output argument with the value of the
         * property.
         * <p>
         * To query the property value without changing it, the control accepts
         * a call with no input arguments. The input {@link ArgumentInfo} should
         * always be treated as optional, and does not need to be marked with
         * {@link ArgumentInfo#KEY_OPTIONAL}.
         */
        Property,
        /**
         * A control that acts as a read-only property. A read-only property
         * control always has no input arguments, and a single output argument
         * representing the value of the property.
         */
        ReadOnlyProperty
    };

    private final Type type;
    private final List<ArgumentInfo> inputs;
    private final List<ArgumentInfo> outputs;
    private final List<Value> defaults;

    private ControlInfo(Type type,
            List<ArgumentInfo> inputs,
            List<ArgumentInfo> outputs,
            List<Value> defaults,
            PMap data) {
        super(data);
        this.inputs = inputs;
        this.outputs = outputs;
        this.defaults = defaults;
        this.type = type;
    }

    /**
     * The type of the control.
     *
     * @return control type
     */
    public Type controlType() {
        return type;
    }

    /**
     * Access the map of properties. The map includes the type, inputs, outputs
     * and defaults, as well as any custom or optional properties.
     * <p>
     * This method is equivalent to calling
     * {@link PMap.MapBasedValue#dataMap()}.
     *
     * @return property map
     */
    public PMap properties() {
        return dataMap();
    }

    /**
     * The list of default values. May be empty if not a property or not
     * specified.
     *
     * @return list of default values
     */
    public List<Value> defaults() {
        return defaults;
    }

    /**
     * The list of input {@link ArgumentInfo}.
     *
     * @return input argument info
     */
    public List<ArgumentInfo> inputs() {
        return inputs;
    }

    /**
     * The list of output {@link ArgumentInfo}.
     *
     * @return output argument info
     */
    public List<ArgumentInfo> outputs() {
        return outputs;
    }

    /**
     * Create ControlInfo for a function control.
     *
     * @param inputs list of input info
     * @param outputs list of output info
     * @param properties additional properties (may be null)
     * @return control info
     */
    public static ControlInfo createFunctionInfo(List<ArgumentInfo> inputs,
            List<ArgumentInfo> outputs, PMap properties) {
        List<ArgumentInfo> ins = inputs == null ? List.of() : List.copyOf(inputs);
        List<ArgumentInfo> outs = outputs == null ? List.of() : List.copyOf(outputs);
        PMap map = PMap.of(KEY_TYPE, Type.Function,
                KEY_INPUTS, PArray.of(ins),
                KEY_OUTPUTS, PArray.of(outs));
        if (properties != null) {
            map = PMap.merge(map, properties, PMap.IF_ABSENT);
        }
        return new ControlInfo(Type.Function, ins, outs, List.of(), map);
    }

    /**
     * Create ControlInfo for an action control.
     *
     * @param properties additional properties (may be null)
     * @return control info
     */
    public static ControlInfo createActionInfo(PMap properties) {
        PMap map = PMap.of(KEY_TYPE, Type.Action);
        if (properties != null) {
            map = PMap.merge(map, properties, PMap.IF_ABSENT);
        }
        return new ControlInfo(Type.Action, List.of(), List.of(), List.of(), map);
    }

    /**
     * Create ControlInfo for a property control.
     *
     * @param argument property value info
     * @param def default value
     * @param properties additional properties (may be null)
     * @return control info
     */
    public static ControlInfo createPropertyInfo(ArgumentInfo argument,
            Value def, PMap properties) {
        return createPropertyInfo(List.of(argument), List.of(def), properties);
    }

    public static ControlInfo createPropertyInfo(List<ArgumentInfo> arguments,
            List<Value> defaults, PMap properties) {
        List<ArgumentInfo> ins = arguments == null ? List.of() : List.copyOf(arguments);
        List<Value> defs = defaults == null ? List.of() : List.copyOf(defaults);
        PMap map = PMap.of(KEY_TYPE, Type.Property,
                KEY_INPUTS, PArray.of(ins),
                KEY_DEFAULTS, PArray.of(defs));
        if (properties != null) {
            map = PMap.merge(map, properties, PMap.IF_ABSENT);
        }
        return new ControlInfo(Type.Property, ins, ins, defs, map);
    }

    /**
     * Create ControlInfo for a read-only property control.
     *
     * @param argument property value info
     * @param properties additional properties (may be null)
     * @return control info
     */
    public static ControlInfo createReadOnlyPropertyInfo(ArgumentInfo argument,
            PMap properties) {
        return createReadOnlyPropertyInfo(List.of(argument), properties);
    }

    public static ControlInfo createReadOnlyPropertyInfo(List<ArgumentInfo> arguments,
            PMap properties) {
        List<ArgumentInfo> outs = arguments == null ? List.of() : List.copyOf(arguments);
        PMap map = PMap.of(KEY_TYPE, Type.ReadOnlyProperty,
                KEY_OUTPUTS, PArray.of(outs));
        if (properties != null) {
            map = PMap.merge(map, properties, PMap.IF_ABSENT);
        }
        return new ControlInfo(Type.ReadOnlyProperty, List.of(), outs, List.of(), map);
    }

    /**
     * Coerce the provided Value into a ControlInfo if possible.
     *
     * @param arg value of unknown type
     * @return control info or empty optional
     */
    public static Optional<ControlInfo> from(Value arg) {
        if (arg instanceof ControlInfo info) {
            return Optional.of(info);
        } else {
            try {
                return PMap.from(arg).map(ControlInfo::fromMap);
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * Parse the provided String into a ControlInfo if possible.
     *
     * @param string text to parse
     * @return control info
     * @throws ValueFormatException if parsing fails
     */
    public static ControlInfo parse(String string) throws ValueFormatException {
        var data = PMap.parse(string);
        try {
            return fromMap(data);
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }
    }

    private static ControlInfo fromMap(PMap data) {
        var type = Type.valueOf(data.getString(KEY_TYPE, ""));
        return switch (type) {
            case Action ->
                actionFromData(data);
            case Function ->
                functionFromData(data);
            case Property ->
                propertyFromData(data);
            case ReadOnlyProperty ->
                readOnlyPropertyFromData(data);
        };
    }

    private static ControlInfo actionFromData(PMap data) {
        if (data.asMap().containsKey(KEY_INPUTS)
                || data.asMap().containsKey(KEY_OUTPUTS)
                || data.asMap().containsKey(KEY_DEFAULTS)) {
            data = PMap.merge(data,
                    PMap.of(KEY_INPUTS, "",
                            KEY_OUTPUTS, "",
                            KEY_DEFAULTS, ""),
                    PMap.REPLACE);
        }
        return new ControlInfo(Type.Action, List.of(), List.of(), List.of(), data);
    }

    private static ControlInfo functionFromData(PMap data) {
        var inputs = PArray.from(data.asMap().getOrDefault(KEY_INPUTS, PArray.EMPTY))
                .map(a -> a.asListOf(ArgumentInfo.class))
                .orElseThrow(IllegalArgumentException::new);

        var outputs = PArray.from(data.asMap().getOrDefault(KEY_OUTPUTS, PArray.EMPTY))
                .map(a -> a.asListOf(ArgumentInfo.class))
                .orElseThrow(IllegalArgumentException::new);

        if (data.asMap().containsKey(KEY_DEFAULTS)) {
            data = PMap.merge(data, PMap.of(KEY_DEFAULTS, ""), PMap.REPLACE);
        }

        return createFunctionInfo(inputs, outputs, data);
    }

    private static ControlInfo propertyFromData(PMap data) {
        var inputs = PArray.from(data.asMap().getOrDefault(KEY_INPUTS, PArray.EMPTY))
                .map(a -> a.asListOf(ArgumentInfo.class))
                .orElseThrow(IllegalArgumentException::new);
        var defaults = PArray.from(data.asMap().getOrDefault(KEY_DEFAULTS, PArray.EMPTY))
                .map(a -> a.asList())
                .orElseThrow(IllegalArgumentException::new);

        if (data.asMap().containsKey(KEY_OUTPUTS)) {
            data = PMap.merge(data, PMap.of(KEY_OUTPUTS, ""), PMap.REPLACE);
        }

        return new ControlInfo(Type.Property, inputs, inputs, defaults, data);

    }

    private static ControlInfo readOnlyPropertyFromData(PMap data) {
        var outputs = PArray.from(data.asMap().getOrDefault(KEY_OUTPUTS, PArray.EMPTY))
                .map(a -> a.asListOf(ArgumentInfo.class))
                .orElseThrow(IllegalArgumentException::new);

        if (data.asMap().containsKey(KEY_INPUTS) || data.asMap().containsKey(KEY_DEFAULTS)) {
            data = PMap.merge(data, PMap.of(KEY_INPUTS, "", KEY_DEFAULTS, ""), PMap.REPLACE);
        }

        return new ControlInfo(Type.ReadOnlyProperty, List.of(), outputs, List.of(), data);

    }

}
