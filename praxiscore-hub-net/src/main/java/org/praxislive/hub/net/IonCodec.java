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
package org.praxislive.hub.net;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PBytes;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 *
 */
class IonCodec {

    private static final IonCodec DEFAULT = new IonCodec();

    private static final String SEND = "Send";
    private static final String SERVICE = "Service";
    private static final String REPLY = "Reply";
    private static final String ERROR = "Error";
    private static final String SYSTEM = "System";

    private static final String TYPE_MAP = PMap.TYPE_NAME;
    private static final String TYPE_ARRAY = PArray.TYPE_NAME;

    private static final String FIELD_MATCH_ID = "matchID";
    private static final String FIELD_TO = "to";
    private static final String FIELD_FROM = "from";
    private static final String FIELD_ARGS = "args";
    private static final String FIELD_QUIET = "quiet";
    private static final String FIELD_SERVICE = "service";
    private static final String FIELD_CONTROL = "control";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DATA = "data";

    private final IonSystem system;

    private IonCodec() {
        system = IonSystemBuilder.standard().build();
    }

    void readMessages(InputStream in, Consumer<Message> out) throws IOException {
        try (IonReader reader = system.newReader(in)) {
            IonType type;
            while ((type = reader.next()) != null) {
                out.accept(readMessage(reader));
            }
        }
    }

    List<Message> readMessages(byte[] data) throws IOException {
        List<Message> list = new ArrayList<>();
        try (var input = new ByteArrayInputStream(data)) {
            readMessages(input, list::add);
        }
        return list;
    }

    void writeMessages(List<Message> messages, OutputStream out) throws IOException {
        try (IonWriter writer = system.newBinaryWriter(out)) {
            for (Message message : messages) {
                if (message instanceof Message.Send send) {
                    writeSend(writer, send);
                } else if (message instanceof Message.Service service) {
                    writeService(writer, service);
                } else if (message instanceof Message.Reply reply) {
                    writeReply(writer, reply);
                } else if (message instanceof Message.Error error) {
                    writeError(writer, error);
                } else if (message instanceof Message.System sys) {
                    writeSystem(writer, sys);
                }
            }
        }
    }

    byte[] writeMessages(List<Message> messages) throws IOException {
        var bos = new ByteArrayOutputStream();
        writeMessages(messages, bos);
        return bos.toByteArray();
    }

    private Message readMessage(IonReader reader) throws IOException {
        if (reader.getType() != IonType.STRUCT) {
            throw new IOException("Not an Ion Struct");
        }
        var annotations = reader.getTypeAnnotations();
        if (annotations.length != 1) {
            throw new IOException("Invalid annotations on message struct");
        }
        try {
            return switch (annotations[0]) {
                case SEND ->
                    readSendMessage(reader);
                case SERVICE ->
                    readServiceMessage(reader);
                case REPLY ->
                    readReplyMessage(reader);
                case ERROR ->
                    readErrorMessage(reader);
                case SYSTEM ->
                    readSystemMessage(reader);
                default ->
                    throw new IOException("Unknown message type");
            };

        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    private Message.Send readSendMessage(IonReader reader) throws Exception {
        Integer matchID = null;
        ControlAddress to = null;
        ControlAddress from = null;
        List<Value> args = List.of();
        PMap data = PMap.EMPTY;
        reader.stepIn();
        IonType type;
        while ((type = reader.next()) != null) {
            switch (reader.getFieldName()) {
                case FIELD_MATCH_ID -> {
                    matchID = reader.intValue();
                }
                case FIELD_TO -> {
                    to = ControlAddress.of(reader.stringValue());
                }
                case FIELD_FROM -> {
                    from = ControlAddress.of(reader.stringValue());
                }
                case FIELD_ARGS -> {
                    args = readValues(reader);
                }
                case FIELD_DATA -> {
                    data = readMap(reader);
                }
            }
        }
        reader.stepOut();
        return new Message.Send(matchID, to, from, args, data);
    }

    private Message.Service readServiceMessage(IonReader reader) throws Exception {
        Integer matchID = null;
        String service = null;
        String control = null;
        ControlAddress from = null;
        List<Value> args = List.of();
        PMap data = PMap.EMPTY;
        reader.stepIn();
        IonType type;
        while ((type = reader.next()) != null) {
            switch (reader.getFieldName()) {
                case FIELD_MATCH_ID -> {
                    matchID = reader.intValue();
                }
                case FIELD_SERVICE -> {
                    service = reader.stringValue();
                }
                case FIELD_CONTROL -> {
                    control = reader.stringValue();
                }
                case FIELD_FROM -> {
                    from = ControlAddress.of(reader.stringValue());
                }
                case FIELD_ARGS -> {
                    args = readValues(reader);
                }
                case FIELD_DATA -> {
                    data = readMap(reader);
                }
            }
        }
        reader.stepOut();
        return new Message.Service(matchID, service, control, from, args, data);
    }

    private Message.Reply readReplyMessage(IonReader reader) throws Exception {
        Integer matchID = null;
        List<Value> args = List.of();
        PMap data = PMap.EMPTY;
        reader.stepIn();
        IonType type;
        while ((type = reader.next()) != null) {
            switch (reader.getFieldName()) {
                case FIELD_MATCH_ID -> {
                    matchID = reader.intValue();
                }
                case FIELD_ARGS -> {
                    args = readValues(reader);
                }
                case FIELD_DATA -> {
                    data = readMap(reader);
                }
            }
        }
        reader.stepOut();
        return new Message.Reply(matchID, args, data);
    }

    private Message.Error readErrorMessage(IonReader reader) throws Exception {
        Integer matchID = null;
        List<Value> args = List.of();
        PMap data = PMap.EMPTY;
        reader.stepIn();
        IonType type;
        while ((type = reader.next()) != null) {
            switch (reader.getFieldName()) {
                case FIELD_MATCH_ID -> {
                    matchID = reader.intValue();
                }
                case FIELD_ARGS -> {
                    args = readValues(reader);
                }
                case FIELD_DATA -> {
                    data = readMap(reader);
                }
            }
        }
        reader.stepOut();
        return new Message.Error(matchID, args, data);
    }

    private Message.System readSystemMessage(IonReader reader) throws Exception {
        Integer matchID = null;
        String msgType = null;
        PMap data = PMap.EMPTY;
        reader.stepIn();
        IonType type;
        while ((type = reader.next()) != null) {
            switch (reader.getFieldName()) {
                case FIELD_MATCH_ID -> {
                    matchID = reader.intValue();
                }
                case FIELD_TYPE -> {
                    msgType = reader.stringValue();
                }
                case FIELD_DATA -> {
                    data = readMap(reader);
                }
            }
        }
        reader.stepOut();
        return new Message.System(matchID, msgType, data);
    }

    private List<Value> readValues(IonReader reader) throws Exception {
        if (reader.getType() != IonType.LIST) {
            throw new IllegalArgumentException("Not a list");
        }
        List<Value> list = new ArrayList<>();
        reader.stepIn();
        while (reader.next() != null) {
            list.add(readValue(reader));
        }
        reader.stepOut();
        return list;
    }

    private Value readValue(IonReader reader) throws Exception {
        return switch (reader.getType()) {
            case BLOB ->
                PBytes.valueOf(reader.newBytes());
            case BOOL ->
                PBoolean.of(reader.booleanValue());
            case FLOAT ->
                PNumber.of(reader.doubleValue());
            case INT ->
                PNumber.of(reader.intValue());
            case LIST -> {
                String[] annotations = reader.getTypeAnnotations();
                if (isMap(annotations)) {
                    yield readMapValue(annotations, reader);
                } else {
                    yield PArray.of(readValues(reader));
                }
            }
            default ->
                PString.of(reader.stringValue());
        };
    }

    private boolean isMap(String[] annotations) {
        for (String annotation : annotations) {
            if (PMap.TYPE_NAME.equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    private Value readMapValue(String[] annotations, IonReader reader) throws Exception {
        Value.Type<?> type = null;
        if (annotations.length > 1) {
            for (String annotation : annotations) {
                if (PMap.TYPE_NAME.equals(annotation)) {
                    continue;
                }
                var vt = Value.Type.fromName(annotation).orElse(null);
                if (vt != null && PMap.MapBasedValue.class.isAssignableFrom(vt.asClass())) {
                    type = vt;
                    break;
                }
            }
        }
        PMap map = readMap(reader);
        if (type != null) {
            Value v = type.converter().apply(map).orElse(null);
            return v == null ? map : v;
        } else {
            return map;
        }
    }

    private PMap readMap(IonReader reader) throws Exception {
        if (reader.getType() != IonType.LIST) {
            throw new IllegalArgumentException("Not a list");
        }
        var b = PMap.builder();
        reader.stepIn();
        while (reader.next() != null) {
            var key = reader.stringValue();
            reader.next();
            var value = readValue(reader);
            b.put(key, value);
        }
        reader.stepOut();
        return b.build();
    }

    private void writeValues(IonWriter writer, List<Value> values) throws IOException {
        writer.stepIn(IonType.LIST);
        for (Value value : values) {
            writeValue(writer, value);
        }
        writer.stepOut();
    }

    private void writeValue(IonWriter writer, Value value) throws IOException {
        if (value instanceof PNumber n) {
            writeNumber(writer, n);
        } else if (value instanceof PArray a) {
            writeArray(writer, a);
        } else if (value instanceof PBytes b) {
            writeBytes(writer, b);
        } else if (value instanceof PBoolean b) {
            writer.writeBool(b.value());
        } else if (value instanceof PMap m) {
            writeMap(writer, m, TYPE_MAP);
        } else if (value instanceof PMap.MapBasedValue v) {
            writeMap(writer, v.dataMap(), v.type().name(), TYPE_MAP);
        } else {
            writer.writeString(value.toString());
        }
    }

    private void writeNumber(IonWriter writer, PNumber number) throws IOException {
        if (number.isInteger()) {
            writer.writeInt(number.toIntValue());
        } else {
            writer.writeFloat(number.value());
        }
    }

    private void writeMap(IonWriter writer, PMap map, String ... annotations) throws IOException {
        writer.setTypeAnnotations(annotations);
        writer.stepIn(IonType.LIST);
        for (var key : map.keys()) {
            writer.writeString(key);
            writeValue(writer, map.get(key));
        }
        writer.stepOut();
    }

    private void writeArray(IonWriter writer, PArray array) throws IOException {
        writer.setTypeAnnotations(TYPE_ARRAY);
        writer.stepIn(IonType.LIST);
        for (var value : array) {
            writeValue(writer, value);
        }
        writer.stepOut();
    }

    private void writeBytes(IonWriter writer, PBytes bytes) throws IOException {
        byte[] tmp = new byte[bytes.size()];
        bytes.read(tmp);
        writer.writeBlob(tmp);
    }

    private void writeSend(IonWriter writer, Message.Send message) throws IOException {
        writer.addTypeAnnotation(SEND);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(FIELD_MATCH_ID);
        writer.writeInt(message.matchID());
        writer.setFieldName(FIELD_TO);
        writer.writeString(message.to().toString());
        writer.setFieldName(FIELD_FROM);
        writer.writeString(message.from().toString());
        var args = message.args();
        if (!args.isEmpty()) {
            writer.setFieldName(FIELD_ARGS);
            writeValues(writer, message.args());
        }
        var data = message.data();
        if (!data.isEmpty()) {
            writer.setFieldName(FIELD_DATA);
            writeMap(writer, message.data());
        }
        writer.stepOut();
    }

    private void writeService(IonWriter writer, Message.Service message) throws IOException {
        writer.addTypeAnnotation(SERVICE);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(FIELD_MATCH_ID);
        writer.writeInt(message.matchID());
        writer.setFieldName(FIELD_SERVICE);
        writer.writeString(message.service());
        writer.setFieldName(FIELD_CONTROL);
        writer.writeString(message.control());
        writer.setFieldName(FIELD_FROM);
        writer.writeString(message.from().toString());
        var args = message.args();
        if (!args.isEmpty()) {
            writer.setFieldName(FIELD_ARGS);
            writeValues(writer, message.args());
        }
        var data = message.data();
        if (!data.isEmpty()) {
            writer.setFieldName(FIELD_DATA);
            writeMap(writer, message.data());
        }
        writer.stepOut();
    }

    private void writeReply(IonWriter writer, Message.Reply message) throws IOException {
        writer.addTypeAnnotation(REPLY);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(FIELD_MATCH_ID);
        writer.writeInt(message.matchID());
        var args = message.args();
        if (!args.isEmpty()) {
            writer.setFieldName(FIELD_ARGS);
            writeValues(writer, message.args());
        }
        var data = message.data();
        if (!data.isEmpty()) {
            writer.setFieldName(FIELD_DATA);
            writeMap(writer, message.data());
        }
        writer.stepOut();
    }

    private void writeError(IonWriter writer, Message.Error message) throws IOException {
        writer.addTypeAnnotation(ERROR);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(FIELD_MATCH_ID);
        writer.writeInt(message.matchID());
        var args = message.args();
        if (!args.isEmpty()) {
            writer.setFieldName(FIELD_ARGS);
            writeValues(writer, message.args());
        }
        var data = message.data();
        if (!data.isEmpty()) {
            writer.setFieldName(FIELD_DATA);
            writeMap(writer, message.data());
        }
        writer.stepOut();
    }

    private void writeSystem(IonWriter writer, Message.System message) throws IOException {
        writer.addTypeAnnotation(SYSTEM);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(FIELD_MATCH_ID);
        writer.writeInt(message.matchID());
        writer.setFieldName(FIELD_TYPE);
        writer.writeString(message.type());
        var data = message.data();
        if (!data.isEmpty()) {
            writer.setFieldName(FIELD_DATA);
            writeMap(writer, message.data());
        }
        writer.stepOut();
    }

    static IonCodec getDefault() {
        return DEFAULT;
    }

}
