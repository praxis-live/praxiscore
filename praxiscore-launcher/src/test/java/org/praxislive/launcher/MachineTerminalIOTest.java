/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Craig Ellenwood.
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
package org.praxislive.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.RootHub;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PError;
import org.praxislive.hub.Hub;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class MachineTerminalIOTest {

    @Test
    void framesMultilineUnicodeAndEmptyResults() throws Exception {
        String multiline = "first line\nPRAXIS_ERROR injected marker\n音楽 🎵";
        var result = run(encode(multiline), encode(""));
        assertEquals(List.of("PRAXIS_READY", "PRAXIS_OK " + encode(multiline), "PRAXIS_OK "), result);
    }

    @Test
    void invalidInputDoesNotPoisonTheNextRequest() throws Exception {
        var result = run("not base64!", Base64.getEncoder().encodeToString(new byte[]{(byte) 0xff}), encode("valid"));
        assertTrue(result.get(1).startsWith("PRAXIS_ERROR "));
        assertTrue(result.get(2).startsWith("PRAXIS_ERROR "));
        assertEquals("PRAXIS_OK " + encode("valid"), result.get(3));
    }

    @Test
    void scriptErrorsAndPipelinedInputStayInOrder() throws Exception {
        var result = run(encode("one"), encode("fail"), encode("three"));
        assertEquals("PRAXIS_OK " + encode("one"), result.get(1));
        assertTrue(result.get(2).startsWith("PRAXIS_ERROR "));
        assertEquals("PRAXIS_OK " + encode("three"), result.get(3));
    }

    private List<String> run(String... requests) throws Exception {
        var lines = new LinkedBlockingQueue<String>();
        var output = new PrintStream(new ByteArrayOutputStream()) {
            @Override
            public void println(String line) {
                lines.add(line);
            }
        };
        var input = new ByteArrayInputStream((String.join("\n", requests) + "\n").getBytes(StandardCharsets.UTF_8));
        var hub = Hub.builder().addExtension(new EchoScriptService())
                .addExtension(new MachineTerminalIO(input, output)).build();
        hub.start();
        try {
            var result = new java.util.ArrayList<String>();
            for (int i = 0; i <= requests.length; i++) {
                String line = lines.poll(5, TimeUnit.SECONDS);
                assertNotNull(line, "Missing framed reply");
                result.add(line);
            }
            assertNull(lines.poll(50, TimeUnit.MILLISECONDS), "Unexpected extra reply");
            return result;
        } finally {
            hub.shutdown();
            hub.await();
        }
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static class EchoScriptService extends AbstractRoot implements RootHub.ServiceProvider {
        @Override
        public List<Class<? extends Service>> services() {
            return List.of(ScriptService.class);
        }

        @Override
        protected void processCall(Call call, PacketRouter router) {
            if (call.isRequest()) {
                if ("fail".equals(call.args().getFirst().toString())) {
                    router.route(call.error(PError.of("Expected script failure")));
                } else {
                    router.route(call.reply(call.args()));
                }
            }
        }
    }
}
