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
package org.praxislive.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 *
 */
public class ControlInfo extends Value {

    public final static String KEY_TRANSIENT = "transient";
    public final static String KEY_DEPRECATED = "deprecated";
    public final static String KEY_EXPERT = "expert";
//    public final static String KEY_DUPLICATES = "duplicates";

    public static enum Type {

        Function, Action, Property, ReadOnlyProperty
    };

    private final static List<ArgumentInfo> EMPTY_INFO = List.of();
    private final static List<Value> EMPTY_DEFAULTS = List.of();

    private final List<ArgumentInfo> inputs;
    private final List<ArgumentInfo> outputs;
    private final List<Value> defaults;
    private final PMap properties;
    private final Type type;

    private volatile String string;

    ControlInfo(List<ArgumentInfo> inputs,
            List<ArgumentInfo> outputs,
            List<Value> defaults,
            Type type,
            PMap properties,
            String string
    ) {

        this.inputs = inputs;
        this.outputs = outputs;
        this.defaults = defaults;
        this.type = type;
        this.properties = properties;
        this.string = string;
    }

    @Override
    public String toString() {
        String str = string;
        if (str == null) {
            str = buildString();
            string = str;
        }
        return str;
    }

    private String buildString() {
        
        switch (type) {
            case Action:
                return PArray.of(
                        PString.of(type),
                        properties)
                        .toString();
            case Function:
                return PArray.of(PString.of(type),
                        PArray.of(inputs),
                        PArray.of(outputs),
                        properties)
                        .toString();
            default:
                return PArray.of(PString.of(type),
                        PArray.of(outputs),
                        PArray.of(defaults),
                        properties)
                        .toString();
                
        }
        
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.inputs);
        hash = 37 * hash + Objects.hashCode(this.outputs);
        hash = 37 * hash + Objects.hashCode(this.defaults);
        hash = 37 * hash + Objects.hashCode(this.properties);
        hash = 37 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ControlInfo other = (ControlInfo) obj;
        if (!Objects.equals(this.inputs, other.inputs)) {
            return false;
        }
        if (!Objects.equals(this.outputs, other.outputs)) {
            return false;
        }
        if (!Objects.equals(this.defaults, other.defaults)) {
            return false;
        }
        if (!Objects.equals(this.properties, other.properties)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        return true;
    }


    public Type controlType() {
        return type;
    }
    
    public PMap properties() {
        return properties;
    }
    
    public List<Value> defaults() {
        return defaults;
    }

    public List<ArgumentInfo> inputs() {
        return inputs;
    }

    public List<ArgumentInfo> outputs() {
        return outputs;
    }
    
    public static ControlInfo createFunctionInfo(List<ArgumentInfo> inputs,
            List<ArgumentInfo> outputs, PMap properties) {
        return create(inputs, outputs, null, Type.Function, properties);
    }

    public static ControlInfo createActionInfo(PMap properties) {
        return create(null, null, null, Type.Action, properties);
    }

    public static ControlInfo createPropertyInfo(ArgumentInfo argument,
            Value def, PMap properties) {
        return createPropertyInfo(List.of(argument), List.of(def), properties);
    }
    
    public static ControlInfo createPropertyInfo(List<ArgumentInfo> arguments,
            List<Value> defaults, PMap properties) {
        return create(arguments, arguments, defaults, Type.Property, properties);
    }

    public static ControlInfo createReadOnlyPropertyInfo(ArgumentInfo argument,
            PMap properties) {
        return createReadOnlyPropertyInfo(List.of(argument), properties);
    }
    
    public static ControlInfo createReadOnlyPropertyInfo(List<ArgumentInfo> arguments,
            PMap properties) {
        return create(null, arguments, null, Type.ReadOnlyProperty, properties);
    }

    private static ControlInfo create(List<ArgumentInfo> inputs,
            List<ArgumentInfo> outputs,
            List<Value> defaults,
            Type type,
            PMap properties) {

        List<ArgumentInfo> ins = inputs == null ? EMPTY_INFO : List.copyOf(inputs);
        List<ArgumentInfo> outs;
        if (outputs == inputs) {
            // property - make same as inputs
            outs = ins;
        } else {
            outs = outputs == null ? EMPTY_INFO : List.copyOf(outputs);
        }
        List<Value> def;
        if (defaults != null) {
            def = List.copyOf(defaults);
        } else {
            def = EMPTY_DEFAULTS;
        }
        if (properties == null) {
            properties = PMap.EMPTY;
        }

        return new ControlInfo(ins, outs, def, type, properties, null);

    }

    private static ControlInfo coerce(Value arg) throws ValueFormatException {
        if (arg instanceof ControlInfo) {
            return (ControlInfo) arg;
        } else {
            return parse(arg.toString());
        }
    }

    public static Optional<ControlInfo> from(Value arg) {
        try {
            return Optional.of(coerce(arg));
        } catch (ValueFormatException ex) {
            return Optional.empty();
        }
    }
    
    public static ControlInfo parse(String string) throws ValueFormatException {
        try {
            PArray arr = PArray.parse(string);
            Type type = Type.valueOf(arr.get(0).toString());
            switch (type) {
                case Function :
                    return parseFunction(string, arr);
                case Action :
                    return parseAction(string, arr);
                default : 
                    return parseProperty(string, type, arr);
            }
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }
        
    }
    
    private static ControlInfo parseFunction(String string, PArray array) throws Exception {
        // array(1) is inputs
        PArray args = PArray.from(array.get(1)).orElseThrow();
        ArgumentInfo[] inputs = new ArgumentInfo[args.size()];
        for (int i=0; i<inputs.length; i++) {
            inputs[i] = ArgumentInfo.from(args.get(i)).orElseThrow();
        }
        // array(2) is outputs
        args = PArray.from(array.get(2)).orElseThrow();
        ArgumentInfo[] outputs = new ArgumentInfo[args.size()];
        for (int i=0; i<outputs.length; i++) {
            outputs[i] = ArgumentInfo.from(args.get(i)).orElseThrow();
        }
        // optional array(3) is properties
        PMap properties;
        if (array.size() > 3) {
            properties = PMap.from(array.get(3)).orElseThrow();
        } else {
            properties = PMap.EMPTY;
        }
        return new ControlInfo(List.of(inputs), List.of(outputs), EMPTY_DEFAULTS, Type.Function, properties, string);
    }
    
    private static ControlInfo parseAction(String string, PArray array) throws Exception {
        // optional array(1) is properties
        PMap properties;
        if (array.size() > 1) {
            properties = PMap.from(array.get(1)).orElseThrow();
        } else {
            properties = PMap.EMPTY;
        }
        return new ControlInfo(EMPTY_INFO, EMPTY_INFO, EMPTY_DEFAULTS, Type.Action, properties, string);
    }
    
    private static ControlInfo parseProperty(String string, Type type, PArray array) throws Exception {
        // array(1) is outputs
        PArray args = PArray.from(array.get(1)).orElseThrow();
        ArgumentInfo[] outputs = new ArgumentInfo[args.size()];
        for (int i=0; i<outputs.length; i++) {
            outputs[i] = ArgumentInfo.from(args.get(i)).orElseThrow();
        }
        ArgumentInfo[] inputs = type == Type.ReadOnlyProperty ?
                new ArgumentInfo[0] : outputs;
        // array(2) is defaults
        args = PArray.from(array.get(2)).orElseThrow();
        Value[] defs = new Value[args.size()];
        for (int i=0; i<defs.length; i++) {
            defs[i] = PString.from(args.get(i)).orElseThrow();
        }
        // optional array(3) is properties
        PMap properties;
        if (array.size() > 3) {
            properties = PMap.from(array.get(3)).orElseThrow();
        } else {
            properties = PMap.EMPTY;
        }
        return new ControlInfo(List.of(inputs), List.of(outputs), List.of(defs), type, properties, string);
    }

}
