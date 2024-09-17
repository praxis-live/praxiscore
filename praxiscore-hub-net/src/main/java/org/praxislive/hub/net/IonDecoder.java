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
package org.praxislive.hub.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import java.util.ArrayList;
import java.util.List;

class IonDecoder extends LengthFieldBasedFrameDecoder {

    IonDecoder() {
        super(10 * 1048576, 0, 4, 0, 4);
    }

    @Override
    protected List<Message> decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf bytes = (ByteBuf) super.decode(ctx, in);

        if (bytes == null) {
            return null;
        }

        List<Message> messages = new ArrayList<>();
        try (var stream = new ByteBufInputStream(bytes, true)) {
            IonCodec.getDefault().readMessages(stream, messages::add);
        }
        return List.copyOf(messages);

    }

}
