/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Neil C Smith.
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
import org.praxislive.code.userapi.Data;
import org.praxislive.core.Lookup;
import org.praxislive.core.services.LogLevel;

/**
 *
 *
 */
class DataSink<T> extends Data.Sink<T> {

    @Override
    public Lookup getLookup() {
        return Lookup.EMPTY;
    }

    static class Descriptor extends ReferenceDescriptor<Descriptor> {

        private final Field sinkField;

        private CodeContext<?> context;
        private DataSink<?> sink;

        public Descriptor(String id, Field sinkField) {
            super(Descriptor.class, id);
            this.sinkField = sinkField;
        }

        @Override
        public void attach(CodeContext<?> context, Descriptor previous) {

            this.context = context;

            if (previous != null) {
                if (isCompatible(previous)) {
                    sink = previous.sink;
                    previous.sink = null;
                } else {
                    previous.dispose();
                }
            }

            if (sink == null) {
                sink = new DataSink<>();
            }

            sink.attach(context);

            try {
                sinkField.set(context.getDelegate(), sink);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }

        }

        private boolean isCompatible(Descriptor other) {
            return sinkField.getGenericType().equals(other.sinkField.getGenericType());
        }

        @Override
        public void init() {
//                if (sink != null) {
//                    // dispose?
//                }
            sink = new DataSink<>();
            sink.attach(context);
            try {
                sinkField.set(context.getDelegate(), sink);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }
        }

        @Override
        public void reset() {
            if (sink != null) {
                sink.reset();
            }
        }

        static Descriptor create(CodeConnector<?> connector, Field field) {
            if (Data.Sink.class.equals(field.getType())
                    && field.getGenericType() instanceof ParameterizedType) {
                field.setAccessible(true);
                return new Descriptor(field.getName(), field);
            } else {
                return null;
            }
        }

    }

}
