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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class FileServerTest {

    @Test
    public void testFileServer() throws IOException {
        var rootDir = Files.createTempDirectory("px-fileserver-test").toRealPath();
        var allowedDir = Files.createDirectory(rootDir.resolve("allowed"));
        var allowedFile = Files.writeString(allowedDir.resolve("allowed-text.txt"),
                "ALLOWED", StandardOpenOption.CREATE_NEW);
        var forbiddenFile = Files.writeString(rootDir.resolve("forbidden-text.txt"),
                "FORBIDDEN", StandardOpenOption.CREATE_NEW);

        var fileServer = new FileServer(allowedDir);
        var port = fileServer.start().port();

        var serverURI = URI.create("http://localhost:" + port);

        try (var in = new BufferedReader(new InputStreamReader(
                serverURI.resolve(allowedFile.toUri().getRawPath()).toURL().openStream(),
                StandardCharsets.UTF_8))) {
            assertEquals("ALLOWED", in.readLine());
        }

        assertThrows(Exception.class, () -> {
            try (var in = new BufferedReader(new InputStreamReader(
                    serverURI.resolve(forbiddenFile.toUri().getRawPath()).toURL().openStream(),
                    StandardCharsets.UTF_8))) {
                in.readLine();
            } catch (Exception ex) {
                System.out.println(ex);
                throw ex;
            }
        });
        
        fileServer.stop();

        Files.deleteIfExists(allowedFile);
        Files.deleteIfExists(forbiddenFile);
        Files.deleteIfExists(allowedDir);
        Files.deleteIfExists(rootDir);

    }

}
