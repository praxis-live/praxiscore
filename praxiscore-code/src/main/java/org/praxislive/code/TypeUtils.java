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
package org.praxislive.code;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Objects;

/**
 * Various package private type utilities.
 */
class TypeUtils {

    private TypeUtils() {

    }

    /**
     * Check whether two types are equivalent - either equal or by type name.
     * This can be used eg. in port compatibility checks to cover mutually
     * changing shared types.
     *
     * @param type1
     * @param type2
     * @return
     */
    static boolean equivalent(Type type1, Type type2) {
        return Objects.equals(type1, type2) || Objects.equals(type1.toString(), type2.toString());
    }

    /**
     * Extract the type parameter of a field, also checking the raw type of the
     * field against the base type. eg. for a field of {@code Ref<List<String>>}
     * return the type of {@code List<String>}.
     *
     * @param field generic field definition
     * @param baseType expected raw field type
     * @return extracted type or null
     */
    static Type extractTypeParameter(Field field, Class<?> baseType) {
        if (field.getType().equals(baseType)) {
            return extractTypeParameter(field.getGenericType());
        }
        return null;
    }

    /**
     * Extract the type parameter from a type if it is a parameterized type with
     * single type parameter. eg. for a type of {@code Ref<List<String>>} return
     * the type of {@code List<String>}.
     *
     * @param type generic type
     * @return extracted parameter type or null
     */
    static Type extractTypeParameter(Type type) {
        if (type instanceof ParameterizedType paramType) {
            Type[] types = paramType.getActualTypeArguments();
            if (types.length == 1) {
                return types[0];
            }
        }
        return null;
    }

    /**
     * Extract the raw Class type from a type, if one exists. Supports Class or
     * ParameterizedType. Supports null input for chaining with other methods.
     *
     * @param type type or null
     * @return class or null
     */
    static Class<?> extractRawType(Type type) {
        if (type == null) {
            return null;
        }
        if (type instanceof Class cls) {
            return cls;
        }
        if (type instanceof ParameterizedType paramType) {
            Type raw = paramType.getRawType();
            if (raw instanceof Class cls) {
                return cls;
            }
        }
        return null;
    }

    /**
     * Build a simplified version of the type to use as a Port category. This
     * reflects the generic type signature but using
     * {@link Class#getSimpleName()} for all concrete types.
     *
     * @param type type to convert
     * @return port category as String
     */
    static String portCategory(Type type) {
        StringBuilder sb = new StringBuilder();
        buildSimpleName(sb, type);
        return sb.toString();
    }

    private static void buildSimpleName(StringBuilder sb, java.lang.reflect.Type type) {
        if (type instanceof Class) {
            sb.append(((Class<?>) type).getSimpleName());
        } else if (type instanceof ParameterizedType) {
            buildSimpleName(sb, ((ParameterizedType) type).getRawType());
            java.lang.reflect.Type[] parTypes = ((ParameterizedType) type).getActualTypeArguments();
            sb.append("<");
            for (int i = 0; i < parTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                buildSimpleName(sb, parTypes[i]);
            }
            sb.append(">");
        } else if (type instanceof WildcardType) {
            java.lang.reflect.Type[] bounds = ((WildcardType) type).getLowerBounds();
            if (bounds.length > 0) {
                sb.append("? super ");
            } else {
                bounds = ((WildcardType) type).getUpperBounds();
                if (bounds.length > 0 && !Object.class.equals(bounds[0])) {
                    sb.append("? extends ");
                } else {
                    sb.append("?");
                    return;
                }
            }
            for (int i = 0; i < bounds.length; i++) {
                if (i > 0) {
                    sb.append(" & ");
                }
                buildSimpleName(sb, bounds[i]);
            }
        } else {
            sb.append("?");
        }
    }

}
