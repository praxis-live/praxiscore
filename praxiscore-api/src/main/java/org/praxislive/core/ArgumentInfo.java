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

import java.util.Objects;
import java.util.Optional;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PString;

/**
 * Info object used to define the valid input and output arguments of a Control.
 * 
 * As well as giving the type of the argument, an ArgumentInfo can have an optional
 * set of properties. This might be used for defining the "minimum" and "maximum"
 * values of a PNumber argument, for example.
 *
 */
public final class ArgumentInfo extends Value {

    public static final String KEY_ALLOWED_VALUES = "allowed-values";
    public static final String KEY_SUGGESTED_VALUES = "suggested-values";
    public static final String KEY_ALLOW_EMPTY = "allow-empty";
    public static final String KEY_EMPTY_IS_DEFAULT = "empty-is-default";
    public static final String KEY_TEMPLATE = "template";
    public static final String KEY_MIME_TYPE = "mime-type";
    public static final String KEY_OPTIONAL = "optional";
    public static final String KEY_VARARGS = "varargs";

    static enum Presence {

        Always, Optional, Variable
    }

    private final Value.Type<? extends Value> type;
    private final PMap properties;

    private volatile String string;

    ArgumentInfo(Value.Type<? extends Value> type,
            PMap properties,
            String string) {
        this.type = type;
        this.properties = properties;
        this.string = string;
    }
 
    public Value.Type<? extends Value> argumentType() {
        return type;
    }

    /**
     *
     * @return PMap properties
     */
    public PMap properties() {
        return properties;
    }
    

    @Override
    public String toString() {
        String str = string;
        if (str == null) {
            str = PArray.of(
                    PString.of(type.name()),
                    // @TODO remove when legacy parsing not required
                    PString.of(Presence.Always.name()),
                    properties
            
            ).toString();
            string = str;
        }
        return str;
    }
//
//    @Override
//    public boolean isEquivalent(Value arg) {
//        return equals(arg);
//    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArgumentInfo) {
            ArgumentInfo o = (ArgumentInfo) obj;
            return type.equals(o.type)
                    && properties.equals(o.properties);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + (this.type != null ? this.type.hashCode() : 0);
        hash = 53 * hash + (this.properties != null ? this.properties.hashCode() : 0);
        return hash;
    }

    /**
     * Create an ArgumentInfo from the Value class and optional PMap of
     * additional properties.
     *
     * @param argClass
     * @return ArgumentInfo
     */
    public static ArgumentInfo of(Class<? extends Value> argClass) {
        return create(argClass, PMap.EMPTY);

    }
    
    /**
     * Create an ArgumentInfo from the Value class and optional PMap of
     * additional properties.
     *
     * @param argClass
     * @param properties
     * @return ArgumentInfo
     */
    public static ArgumentInfo of(Class<? extends Value> argClass,
            PMap properties) {
        return create(argClass, properties);

    }

    /**
     * Create an ArgumentInfo from the Value class and optional PMap of
     * additional properties.
     *
     * @param argClass
     * @param properties
     * @return ArgumentInfo
     */
    private static ArgumentInfo create(Class<? extends Value> argClass,
            PMap properties) {
        Objects.requireNonNull(argClass);
        if (properties == null) {
            properties = PMap.EMPTY;
        }
        return new ArgumentInfo(Value.Type.of(argClass), properties, null);

    }

    /**
     * Coerce the given Value into an ArgumentInfo object.
     *
     * @param arg Value to be coerced.
     * @return ArgumentInfo
     * @throws ValueFormatException if Value cannot be coerced.
     */
    private static ArgumentInfo coerce(Value arg) throws ValueFormatException {
        if (arg instanceof ArgumentInfo) {
            return (ArgumentInfo) arg;
        } else {
            return parse(arg.toString());
        }
    }
    
    public static Optional<ArgumentInfo> from(Value arg) {
        try {
            return Optional.of(coerce(arg));
        } catch (ValueFormatException ex) {
            return Optional.empty();
        }
    }

    public static ArgumentInfo parse(String string) throws ValueFormatException {
        PArray arr = PArray.parse(string);
        try {
            if (arr.size() > 2) {
                // legacy format
                Value.Type<? extends Value> type =
                        Value.Type.fromName(arr.get(0).toString()).orElseThrow();
                Presence presence = Presence.valueOf(arr.get(1).toString());
                PMap properties = PMap.from(arr.get(2)).orElseThrow();
                if (presence != Presence.Always) {
                    var b = PMap.builder(properties.size() + 1);
                    for (var key : properties.keys()) {
                        b.put(key, properties.get(key));
                    }
                    if (presence == Presence.Optional) {
                        b.put(KEY_OPTIONAL, true);
                    } else {
                        b.put(KEY_VARARGS, true);
                    }
                    properties = b.build();
                }
                return new ArgumentInfo(type, properties, string);
            } else {
                Value.Type<? extends Value> type =
                        Value.Type.fromName(arr.get(0).toString()).orElseThrow();
                PMap properties = PMap.from(arr.get(1)).orElseThrow();
                return new ArgumentInfo(type, properties, string);
            }
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }
    }
    
}
