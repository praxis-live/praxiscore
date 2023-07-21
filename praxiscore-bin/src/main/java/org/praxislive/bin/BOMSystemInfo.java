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
package org.praxislive.bin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.praxislive.code.LibraryResolver;
import org.praxislive.core.types.PResource;
import org.praxislive.purl.PackageURL;

/**
 * Implementation of {@link LibraryResolver.SystemInfo} for the assembly.
 */
public class BOMSystemInfo implements LibraryResolver.SystemInfo {

    private static final List<PResource> CACHE;

    static {
        List<PResource> list;
        try (var input = BOMSystemInfo.class.getResourceAsStream("/META-INF/praxiscore/bom")) {
            var bom = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            list = bom.lines()
                    .filter(line -> line.contains("jar"))
                    .map(String::strip)
                    .map(line -> line.split(":"))
                    .map(tokens -> toPResource(tokens[0], tokens[1], tokens[3]))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            System.getLogger(BOMSystemInfo.class.getName()).log(System.Logger.Level.ERROR, "Unable to process assembly bom", ex);
            list = List.of();
        }
        CACHE = List.copyOf(list);
    }

    private static PResource toPResource(String group, String artefact, String version) {
        var purl = PackageURL.builder()
                .withType("maven")
                .withNamespace(group)
                .withName(artefact)
                .withVersion(version)
                .build();
        return PResource.of(purl.toURI());
    }

    @Override
    public Stream<PResource> provided() {
        return CACHE.stream();
    }

}
