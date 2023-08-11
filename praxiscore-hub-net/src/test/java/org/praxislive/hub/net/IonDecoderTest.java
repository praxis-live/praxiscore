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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.praxislive.core.types.PMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class IonDecoderTest {

    @Test
    public void testDecoding() throws Exception {
        Message.System msg = new Message.System(12345, "TEST", PMap.of("key1", "value1"));
        byte[] data = IonCodec.getDefault().writeMessages(List.of(msg));
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(data.length);
        buf.writeBytes(data);
        EmbeddedChannel channel = new EmbeddedChannel(new IonDecoder());

        ByteBuf copy = buf.copy();
        channel.writeInbound(copy);
        List<Message> decoded = channel.readInbound();
        assertEquals(msg, decoded.get(0));

        copy = buf.copy();
        channel.writeInbound(copy.readBytes(data.length / 3));
        assertNull(channel.readInbound());
        channel.writeInbound(copy);
        decoded = channel.readInbound();
        assertEquals(msg, decoded.get(0));

        buf.release();

    }

}
