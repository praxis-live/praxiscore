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

import java.util.Optional;
import org.praxislive.core.types.PMap;

/**
 * Info object used to define the valid input and output arguments of a Control.
 * <p>
 * As well as giving the type of the argument, an ArgumentInfo can have an
 * optional set of properties. This might be used for defining the "minimum" and
 * "maximum" values of a PNumber argument, for example.
 *
 */
public final class ArgumentInfo extends PMap.MapBasedValue {

    /**
     * Value type name.
     */
    public static final String TYPE_NAME = "ArgumentInfo";

    /**
     * Map key for Value type name. The map value should be the String from
     * {@link Value.Type#name()}.
     */
    public static final String KEY_TYPE = "type";

    /**
     * Map key for optional list of allowed values.
     */
    public static final String KEY_ALLOWED_VALUES = "allowed-values";

    /**
     * Map key for optional list of suggested values. A user interface may show
     * these as values to select, but must also allow other values to be
     * entered.
     */
    public static final String KEY_SUGGESTED_VALUES = "suggested-values";

    /**
     * Map key for optional flag that an empty value will be accepted even when
     * the type has no empty representation.
     */
    public static final String KEY_ALLOW_EMPTY = "allow-empty";

    /**
     * Map key for optional flag that an empty value will be treated as a
     * default.
     */
    public static final String KEY_EMPTY_IS_DEFAULT = "empty-is-default";

    /**
     * Map key for optional text template. A user interface might show the
     * template when the value is otherwise empty.
     */
    public static final String KEY_TEMPLATE = "template";

    /**
     * Map key for optional mime type of the value.
     */
    public static final String KEY_MIME_TYPE = "mime-type";

    /**
     * Map key to mark an argument as optional. The value may be omitted in
     * input arguments or not returned in outputs arguments.
     */
    public static final String KEY_OPTIONAL = "optional";

    /**
     * Map key to mark an argument as varargs. The input or output arguments may
     * include any number of matching values.
     */
    public static final String KEY_VARARGS = "varargs";

    private final String type;

    private ArgumentInfo(String type, PMap data) {
        super(data);
        this.type = type;
    }

    /**
     * The value type name of the argument. See {@link Value.Type#name()}.
     *
     * @return value type name
     */
    public String argumentType() {
        return type;
    }

    /**
     * Access the map of properties. The map includes the value type, as well as
     * any custom or optional properties.
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
     * Create an ArgumentInfo from the Value class and optional PMap of
     * additional properties.
     *
     * @param argClass
     * @return ArgumentInfo
     */
    public static ArgumentInfo of(Class<? extends Value> argClass) {
        return of(argClass, null);

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
        return create(Value.Type.of(argClass).name(), properties);

    }

    static ArgumentInfo create(String argumentType, PMap properties) {
        PMap map = PMap.of(KEY_TYPE, argumentType);
        if (properties != null) {
            map = PMap.merge(map, properties, PMap.IF_ABSENT);
        }
        return new ArgumentInfo(argumentType, map);
    }

    /**
     * Coerce the provided Value into an ArgumentInfo if possible.
     *
     * @param arg value of unknown type
     * @return argument info or empty optional
     */
    public static Optional<ArgumentInfo> from(Value arg) {
        if (arg instanceof ArgumentInfo info) {
            return Optional.of(info);
        } else {
            try {
                return PMap.from(arg).map(ArgumentInfo::fromMap);
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * Parse the provided String into an ArgumentInfo if possible.
     *
     * @param string text to parse
     * @return argument info
     * @throws ValueFormatException if parsing fails
     */
    public static ArgumentInfo parse(String string) throws ValueFormatException {
        var data = PMap.parse(string);
        try {
            return fromMap(data);
        } catch (Exception ex) {
            throw new ValueFormatException(ex);
        }
    }

    private static ArgumentInfo fromMap(PMap data) {
        String argumentType = data.get(KEY_TYPE).toString();
        return new ArgumentInfo(argumentType, data);
    }

}
