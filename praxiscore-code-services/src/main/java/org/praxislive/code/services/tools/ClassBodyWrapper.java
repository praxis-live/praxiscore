
/*
 * Copyright 2021 Neil C Smith
 *
 * Forked from Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.praxislive.code.services.tools;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Wrap a provided class body with the specified class name, extended type,
 * implemented interfaces, and default imports.
 * <p>
 * Will parse out import statements in the body and add to default import
 * statements.
 * <p>
 * Derived from Janino's ClassBodyEvaluator and has similar limitations.
 */
/* To set up a {@link ClassBodyWrapper} object, proceed as described for {@link
 * IClassBodyEvaluator}. Alternatively, a number of "convenience constructors"
 * exist that execute the described steps instantly.
 * <p>
 * <b>Notice that this implementation of {@link IClassBodyEvaluator} is prone to
 * "Java injection", i.e. an application could get more than one class body
 * compiled by passing a bogus input document.</b>
 * <p>
 * <b>Also notice that the parsing of leading IMPORT declarations is heuristic
 * and has certain limitations; see
 * {@link #parseImportDeclarations(Reader)}.</b>
 */
public class ClassBodyWrapper {

    private static final String NEW_LINE = "\n";

    private List<String> defaultImports;
    private String className;
    private Class<?> extendedType;
    private List<Class<?>> implementedTypes;

    private ClassBodyWrapper() {
        defaultImports = List.of();
        className = ClassBodyCompiler.DEFAULT_CLASS_NAME;
        implementedTypes = List.of();
    }

    public ClassBodyWrapper className(String className) {
        this.className = Objects.requireNonNull(className);
        return this;
    }

    public ClassBodyWrapper defaultImports(List<String> defaultImports) {
        this.defaultImports = List.copyOf(defaultImports);
        return this;
    }

    public ClassBodyWrapper extendsType(Class<?> extendedType) {
        this.extendedType = extendedType;
        return this;
    }

    public ClassBodyWrapper implementsTypes(List<Class<?>> implementedTypes) {
        this.implementedTypes = List.copyOf(implementedTypes);
        return this;
    }

    public String wrap(String source) {
        StringBuilder sb = new StringBuilder();

        Map<Boolean, List<String>> partitionedSource = source.lines()
                .collect(Collectors.partitioningBy(IMPORT_STATEMENT_PATTERN.asPredicate()));

        List<String> sourceImports = partitionedSource.get(true);
        List<String> sourceBody = partitionedSource.get(false);

        // Break the class name up into package name and simple class name.
        String packageName; // null means default package.
        String simpleClassName;
        int idx = this.className.lastIndexOf('.');
        if (idx == -1) {
            packageName = "";
            simpleClassName = this.className;
        } else {
            packageName = this.className.substring(0, idx);
            simpleClassName = this.className.substring(idx + 1);
        }

        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";").append(NEW_LINE);
        }

        defaultImports.forEach(i
                -> sb.append("import ").append(i).append(";").append(NEW_LINE)
        );

        sourceImports.forEach(i -> sb.append(i).append(NEW_LINE));

        sb.append("public class ").append(simpleClassName);

        if (extendedType != null) {
            sb.append(" extends ").append(extendedType.getCanonicalName());
        }

        if (!implementedTypes.isEmpty()) {
            sb.append(" implements ");
            sb.append(implementedTypes.stream()
                    .map(Class::getName)
                    .collect(Collectors.joining(", ")));
        }

        sb.append(" {").append(NEW_LINE);

        sourceBody.forEach(line -> sb.append(line).append(NEW_LINE));

        sb.append("}").append(NEW_LINE);

        return sb.toString();

    }

    public static ClassBodyWrapper create() {
        return new ClassBodyWrapper();
    }

    private static final Pattern IMPORT_STATEMENT_PATTERN = Pattern.compile(
            "\\bimport\\s+"
            + "("
            + "(?:static\\s+)?"
            + "[\\p{javaLowerCase}\\p{javaUpperCase}_\\$][\\p{javaLowerCase}\\p{javaUpperCase}\\d_\\$]*"
            + "(?:\\.[\\p{javaLowerCase}\\p{javaUpperCase}_\\$][\\p{javaLowerCase}\\p{javaUpperCase}\\d_\\$]*)*"
            + "(?:\\.\\*)?"
            + ");"
    );

}
