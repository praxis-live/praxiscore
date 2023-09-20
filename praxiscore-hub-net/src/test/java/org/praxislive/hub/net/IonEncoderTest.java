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
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.praxislive.core.types.PMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class IonEncoderTest {

    @Test
    public void testEncoding() throws Exception {
        Message.System msg = new Message.System(12345, "TEST", PMap.of("key1", "value1"));

        EmbeddedChannel channel = new EmbeddedChannel(new IonEncoder());

        channel.writeOutbound(List.of(msg));

        ByteBuf bytes = channel.readOutbound();
        int size = bytes.readableBytes() - Integer.BYTES;
        assertEquals(size, bytes.readInt());
        List<Message> decoded = new ArrayList<>();
        try (var stream = new ByteBufInputStream(bytes, true)) {
            IonCodec.getDefault().readMessages(stream, decoded::add);
        }
        assertEquals(1, decoded.size());
        assertEquals(msg, decoded.get(0));
    }

}
