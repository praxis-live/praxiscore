
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

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;

/**
 * A {@link ForwardingJavaFileManager} that stores {@link JavaFileObject}s in
 * byte arrays, i.e. in memory (as opposed to the
 * {@link StandardJavaFileManager}, which stores them in files).
 *
 * @param <M>
 */
class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    private final Map<Location, Map<Kind, Map<String /*className*/, JavaFileObject>>> javaFiles = (new HashMap<>());

    public MemoryJavaFileManager(JavaFileManager delegate) {
        super(delegate);
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) {
        throw new UnsupportedOperationException("getFileForInput");
    }

    @Override
    public FileObject getFileForOutput(
            Location location,
            String packageName,
            String relativeName,
            FileObject sibling
    ) {
        throw new UnsupportedOperationException("getFileForInput");
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
        Map<Kind, Map<String, JavaFileObject>> locationJavaFiles = this.javaFiles.get(location);
        if (locationJavaFiles == null) {
            return null;
        }

        Map<String, JavaFileObject> kindJavaFiles = locationJavaFiles.get(kind);
        if (kindJavaFiles == null) {
            return null;
        }

        return kindJavaFiles.get(className);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
            Location location,
            final String className,
            Kind kind,
            FileObject sibling
    ) throws IOException {

        JavaFileObject fileObject = (kind == Kind.SOURCE
                ? new StringWriterJavaFileObject(className, kind)
                : new ByteArrayJavaFileObject(className, kind));
        getFileMap(location, kind).put(className, fileObject);
        return fileObject;
    }
    
    public JavaFileObject addSource(String className, String source) {
        JavaFileObject file = new StringJavaFileObject(className, Kind.SOURCE, source);
        getFileMap(StandardLocation.SOURCE_PATH, Kind.SOURCE).put(className, file);
        return file;
    }
    
    public JavaFileObject addExistingClass(String className, Supplier<InputStream> byteSource) {
        JavaFileObject file = new InputStreamFileObject(className, Kind.CLASS, byteSource);
        getFileMap(StandardLocation.CLASS_PATH, Kind.CLASS).put(className, file);
        return file;
    }
    
    private Map<String, JavaFileObject> getFileMap(Location location, Kind kind) {
        Map<Kind, Map<String, JavaFileObject>> locationJavaFiles = this.javaFiles.get(location);
        if (locationJavaFiles == null) {
            locationJavaFiles = new HashMap<>();
            this.javaFiles.put(location, locationJavaFiles);
        }
        Map<String, JavaFileObject> kindJavaFiles = locationJavaFiles.get(kind);
        if (kindJavaFiles == null) {
            kindJavaFiles = new TreeMap<>();
            locationJavaFiles.put(kind, kindJavaFiles);
        }
        return kindJavaFiles;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof MemoryJavaFileObject) {
            return ((MemoryJavaFileObject) file).binaryName;
        } else {
            return super.inferBinaryName(location, file);
        }

    }

    @Override
    public boolean hasLocation(Location location) {
        return javaFiles.containsKey(location) || super.hasLocation(location);
    }
    
    @Override
    public Iterable<JavaFileObject> list(
            Location location,
            String packageName,
            Set<Kind> kinds,
            boolean recurse
    ) throws IOException {
        Map<Kind, Map<String, JavaFileObject>> locationFiles = this.javaFiles.get(location);
        Iterable<JavaFileObject> stdList = super.list(location, packageName, kinds, recurse);

        if (locationFiles == null) {
            return stdList;
        }

        String prefix = packageName.length() == 0 ? "" : packageName + ".";
        int pl = prefix.length();
        List<JavaFileObject> result = new ArrayList<JavaFileObject>();
        for (Kind kind : kinds) {
            Map<String, JavaFileObject> kindFiles = locationFiles.get(kind);
            if (kindFiles == null) {
                continue;
            }
            for (Entry<String, JavaFileObject> e : kindFiles.entrySet()) {
                String className = e.getKey();
                if (!className.startsWith(prefix)) {
                    continue;
                }
                if (!recurse && className.indexOf('.', pl) != -1) {
                    continue;
                }
                result.add(e.getValue());
            }
        }
        return () -> Stream.concat(
                result.stream(),
                StreamSupport.stream(stdList.spliterator(), false)).iterator();
    }

    static abstract class MemoryJavaFileObject extends SimpleJavaFileObject {

        private final String binaryName;
        
        MemoryJavaFileObject(String protocol, String binaryName, Kind kind) {
            super(
                    URI.create(protocol + ":///" + binaryName.replace('.', '/') + kind.extension),
                    kind
            );
            this.binaryName = binaryName;
        }
        
    }
    
    
    /**
     * Byte array-based implementation of {@link JavaFileObject}.
     */
    static class ByteArrayJavaFileObject extends MemoryJavaFileObject {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public ByteArrayJavaFileObject(String binaryName, Kind kind) {
            super("bytearray", binaryName, kind);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return this.buffer;
        }

        /**
         * @return The bytes that were previously written to this
         * {@link JavaFileObject}.
         */
        public byte[] toByteArray() {
            return this.buffer.toByteArray();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return new ByteArrayInputStream(this.toByteArray());
        }
    }

    /**
     * {@link StringWriter}-based implementation of {@link JavaFileObject}.
     * <p>
     * Notice that {@link #getCharContent(boolean)} is much more efficient than {@link
     * ByteArrayJavaFileObject#getCharContent(boolean)}. However, memory
     * consumption is roughly double, and {@link #openInputStream()} and
     * {@link #openOutputStream()} are not available.
     */
    static class StringWriterJavaFileObject extends MemoryJavaFileObject {

        final StringWriter buffer = new StringWriter();

        public StringWriterJavaFileObject(String binaryName, Kind kind) {
            super("stringwriter", binaryName, kind);
        }

        @Override
        public Writer openWriter() throws IOException {
            return this.buffer;
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new StringReader(this.buffer.toString());
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.buffer.getBuffer();
        }
    }

    static class StringJavaFileObject extends MemoryJavaFileObject {

        final String source;

        public StringJavaFileObject(String binaryName, Kind kind, String source) {
            super("string", binaryName, kind);
            this.source = Objects.requireNonNull(source);
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new StringReader(source);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return source;
        }

    }
    
    static class InputStreamFileObject extends MemoryJavaFileObject {

        final Supplier<InputStream> streamSource;

        public InputStreamFileObject(String binaryName, Kind kind,
                Supplier<InputStream> streamSource) {
            super("bytestream", binaryName, kind); 
            this.streamSource = Objects.requireNonNull(streamSource);
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return streamSource.get();
        }

        

    }

    Map<String, byte[]> extractClassData() {
        Map<String, byte[]> bytecode = new TreeMap<>();
        Map<Kind, Map<String, JavaFileObject>> locationFiles
                = this.javaFiles.get(StandardLocation.CLASS_OUTPUT);
//        if (locationFiles == null) {
//            return Collections.emptyMap();
//        }
        Map<String, JavaFileObject> classFiles
                = locationFiles.get(Kind.CLASS);
//        if (classFiles == null) {
//            return Collections.emptyMap();
//        }
        classFiles.entrySet().stream().forEach((type) -> {
            bytecode.put(type.getKey(), ((ByteArrayJavaFileObject) type.getValue()).toByteArray());
        });

        return bytecode;
    }

}
