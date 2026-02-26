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
 */
package org.praxislive.code;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.praxislive.code.userapi.Type;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.Info;
import org.praxislive.core.Value;
import org.praxislive.core.ValueMapper;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PResource;
import org.praxislive.core.types.PString;

/**
 * Various utilities for working with annotations on elements.
 */
class AnnotationUtils {

    private static final Set<Class<? extends Annotation>> TYPE_ANNOTATIONS
            = Set.of(Type.class, Type.Boolean.class, Type.Integer.class,
                    Type.Number.class, Type.Resource.class, Type.String.class);

    private AnnotationUtils() {
    }

    /**
     * Argument data derived from annotation information.
     *
     * @param mapper value mapper
     * @param defaultValue default value
     * @param info argument info
     * @param validator predicate to check target value is valid
     */
    record ArgumentData<T>(ValueMapper<T> mapper, Value defaultValue,
            ArgumentInfo info, Predicate<T> validator) {

    }

    static class TypeMismatchException extends Exception {

        private TypeMismatchException() {
        }

    }

    /**
     * Extract argument data based on annotations from {@link Type} on the
     * provided element.
     *
     * @param targetType the target type for the mapped
     * @param element annotatable element that may have type annotations
     * @return argument data or null
     */
    static <T> ArgumentData<T> extractArgumentData(ValueMapper<T> mapper,
            AnnotatedElement element) throws TypeMismatchException {
        return switch (findTypeAnnotation(element)) {
            case Type an ->
                extractTypeData(mapper, element, an);
            case Type.Number an ->
                extractNumberData(mapper, element, an);
            case Type.Integer an ->
                extractIntegerData(mapper, element, an);
            case Type.String an ->
                extractStringData(mapper, element, an);
            case Type.Boolean an ->
                extractBooleanData(mapper, element, an);
            case Type.Resource an ->
                extractResourceData(mapper, element, an);
            case null, default ->
                null;
        };
    }

    static DoublePredicate rangePredicate(Type.Number annotation) {
        double min = annotation.min();
        double max = annotation.max();
        return rangePredicate(min, max);
    }

    static IntPredicate rangePredicate(Type.Integer annotation) {
        int min = annotation.min();
        int max = annotation.max();
        return rangePredicate(min, max);
    }

    private static <T> ArgumentData<T> extractTypeData(ValueMapper<T> mapper,
            AnnotatedElement element, Type annotation) throws TypeMismatchException {
        Class<?> annotatedValueClass = Value.Type.of(annotation.value()).asClass();
        Class<?> mapperValueClass = mapper.valueType().asClass();
        if (annotatedValueClass != Value.class && annotatedValueClass != mapperValueClass) {
            throw new TypeMismatchException();
        }
        Value defaultValue;
        if (annotation.def().isEmpty()) {
            defaultValue = mapper.valueType().emptyValue()
                    .map(mapper::fromValue)
                    .map(mapper::toValue)
                    .orElse(PString.EMPTY);
        } else {
            try {
                T mapped = mapper.fromValue(PString.of(annotation.def()));
                defaultValue = mapper.toValue(mapped);
            } catch (Exception ex) {
                defaultValue = PString.EMPTY;
            }

        }

        ArgumentInfo info = mapper.createInfo();
        PMap properties = createPropertyMap(annotation.properties());
        if (!properties.isEmpty()) {
            PMap infoMap = PMap.merge(info.dataMap(), properties, PMap.IF_ABSENT);
            info = ArgumentInfo.from(infoMap).orElseGet(mapper::createInfo);
        }

        Predicate<T> validator = v -> true;
        return new ArgumentData(mapper, defaultValue, info, validator);
    }

    private static <T> ArgumentData<T> extractBooleanData(ValueMapper<T> mapper,
            AnnotatedElement element, Type.Boolean annotation)
            throws TypeMismatchException {
        Class<?> targetType = TypeUtils.extractRawType(mapper.type());
        if (targetType == boolean.class || targetType == Boolean.class
                || targetType == PBoolean.class) {
            return new ArgumentData<>(mapper, PBoolean.of(annotation.def()),
                    mapper.createInfo(), v -> true);
        } else {
            throw new TypeMismatchException();
        }
    }

    private static <T> ArgumentData<T> extractIntegerData(ValueMapper<T> mapper,
            AnnotatedElement element, Type.Integer annotation)
            throws TypeMismatchException {

        int min = annotation.min();
        int max = annotation.max();
        int def = annotation.def();
        int[] suggested = annotation.suggested();
        boolean ranged = min > PNumber.MIN_VALUE || max < PNumber.MAX_VALUE;

        Info.ValueInfoBuilder builder = Info.argument().type(PNumber.class);
        builder.property(PNumber.KEY_IS_INTEGER, true);
        if (ranged) {
            builder.property(PNumber.KEY_MINIMUM, min);
            builder.property(PNumber.KEY_MAXIMUM, max);
        }
        if (suggested.length > 0) {
            PArray vals = IntStream.of(suggested)
                    .mapToObj(PNumber::of)
                    .collect(PArray.collector());
            builder.property(ArgumentInfo.KEY_SUGGESTED_VALUES, vals);
        }

        ArgumentInfo info = builder.build();

        IntPredicate range = rangePredicate(min, max);
        Class<?> targetType = TypeUtils.extractRawType(mapper.type());
        if (targetType == int.class || targetType == Integer.class) {
            return new ArgumentData<>(mapper, PNumber.of(def),
                    info, i -> range.test((int) i));
        } else if (targetType == PNumber.class) {
            return new ArgumentData<>(mapper, PNumber.of(def),
                    info, v -> v instanceof PNumber n && range.test(n.toIntValue()));
        } else {
            throw new TypeMismatchException();
        }

    }

    private static <T> ArgumentData<T> extractNumberData(ValueMapper<T> mapper,
            AnnotatedElement element, Type.Number annotation)
            throws TypeMismatchException {

        double min = annotation.min();
        double max = annotation.max();
        double skew = annotation.skew();
        double def = annotation.def();
        boolean ranged = min > (PNumber.MIN_VALUE + 1)
                || max < (PNumber.MAX_VALUE - 1);
        boolean skewed = Math.abs(skew - 1) > 0.0001;

        Info.NumberInfoBuilder builder = Info.argument().number();
        if (ranged) {
            builder.min(min);
            builder.max(max);
        }
        if (skewed) {
            builder.skew(skew < 0.01 ? 0.01 : skew);
        }
        ArgumentInfo info = builder.build();

        DoublePredicate range = rangePredicate(min, max);
        Class<?> targetType = TypeUtils.extractRawType(mapper.type());
        if (targetType == double.class || targetType == Double.class) {
            return new ArgumentData<>(mapper, PNumber.of(def),
                    info, d -> range.test((double) d));
        } else if (targetType == float.class || targetType == Float.class) {
            return new ArgumentData<>(mapper, PNumber.of(def),
                    info, f -> range.test((float) f));
        } else if (targetType == PNumber.class) {
            return new ArgumentData<>(mapper, PNumber.of(def),
                    info, v -> v instanceof PNumber n && range.test(n.value()));
        } else {
            throw new TypeMismatchException();
        }

    }

    private static <T> ArgumentData<T> extractResourceData(ValueMapper<T> mapper,
            AnnotatedElement element, Type.Resource annotation)
            throws TypeMismatchException {
         Class<?> targetType = TypeUtils.extractRawType(mapper.type());
        if (targetType == PResource.class) {
            return new ArgumentData<>(mapper, PString.EMPTY,
                    mapper.createInfo(), v -> true);
        } else {
            throw new TypeMismatchException();
        }
    }

    private static <T> ArgumentData<T> extractStringData(ValueMapper<T> mapper,
            AnnotatedElement element, Type.String annotation)
            throws TypeMismatchException {
        String[] allowed = annotation.allowed();
        String def = annotation.def();
        boolean emptyIsDefault = annotation.emptyIsDefault();
        String mime = annotation.mime();
        String[] suggested = annotation.suggested();
        String template = annotation.template();

        Class<?> targetType = TypeUtils.extractRawType(mapper.type());

        if (targetType.isEnum()) {
            List<String> filter = Arrays.asList(allowed);
            List<String> values = Stream.of(targetType.getEnumConstants())
                    .map(Object::toString)
                    .filter(s -> filter.isEmpty() || filter.contains(s))
                    .toList();
            String checkedDef;
            if (values.contains(def)) {
                checkedDef = def;
            } else if (values.isEmpty()) {
                checkedDef = "";
            } else {
                checkedDef = values.getFirst();
            }
            ArgumentInfo info = Info.argument().string()
                    .allowed(values.toArray(String[]::new))
                    .build();
            return new ArgumentData<>(mapper, PString.of(checkedDef),
                    info, v -> true);
        } else {
            Info.StringInfoBuilder builder = Info.argument().string();
            if (allowed.length > 0) {
                builder.allowed(allowed);
            } else if (!mime.isEmpty()) {
                builder.mime(mime);
                if (!template.isEmpty()) {
                    builder.template(template);
                }
            } else if (suggested.length > 0) {
                builder.suggested(suggested);
            }
            if (emptyIsDefault) {
                builder.emptyIsDefault();
            }
            ArgumentInfo info = builder.build();
            return new ArgumentData<>(mapper, PString.of(def),
                    info, v -> true);
        }

    }

    private static PMap createPropertyMap(String... properties) {
        if (properties.length == 0) {
            return PMap.EMPTY;
        }
        if (properties.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        PMap.Builder bld = PMap.builder();
        for (int i = 0; i < properties.length; i += 2) {
            bld.put(properties[i], properties[i + 1]);
        }
        return bld.build();
    }

    private static Annotation findTypeAnnotation(AnnotatedElement element) {
        List<Annotation> typeAnnotations = Stream.of(element.getAnnotations())
                .filter(a -> TYPE_ANNOTATIONS.contains(a.annotationType()))
                .toList();
        if (typeAnnotations.size() == 1) {
            return typeAnnotations.getFirst();
        } else {
            return null;
        }
    }

    private static DoublePredicate rangePredicate(double min, double max) {
        return d -> d >= min && d <= max;
    }

    private static IntPredicate rangePredicate(int min, int max) {
        return i -> i >= min && i <= max;
    }

}
