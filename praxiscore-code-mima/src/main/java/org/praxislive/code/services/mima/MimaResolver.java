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
package org.praxislive.code.services.mima;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.praxislive.code.LibraryResolver;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PResource;
import org.praxislive.purl.PackageURL;

/**
 *
 */
public class MimaResolver implements LibraryResolver {
    
    MimaWrapper mima;
    

    @Override
    public Optional<Entry> resolve(PResource resource, Context context) throws Exception {
        String res = resource.toString();
        if (!res.startsWith("pkg:maven/")) {
            return Optional.empty();
        }
        Artifact artifact = parsePURL(res);
        List<Artifact> installed = buildInstalledList(context.provided().toList());
        Artifact existing = findExisting(installed, artifact);
        if (existing != null) {
            if (!artifact.getVersion().isBlank()
                    && !Objects.equals(existing.getVersion(), artifact.getVersion())) {
                context.log().log(LogLevel.WARNING,
                        resource + " already installed at version " + existing.getVersion());
            }
            return Optional.of(new Entry(toPURL(existing), List.of()));
        }
        if (mima == null) {
            mima = new MimaWrapper();
        }
        List<Artifact> resolved = mima.resolve(artifact, installed);
        PResource root = resource;
        List<PResource> provides = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        
        for (Artifact dep : resolved) {
            Artifact ex = findExisting(installed, dep);
            if (ex == null) {
                Path file = dep.getFile().toPath();
                PResource purl = toPURL(dep);
                if (matchingArtifacts(dep, artifact)) {
                    root = purl;
                }
                provides.add(purl);
                files.add(file);
                context.log().log(LogLevel.INFO, "Installing dependency " + purl);
            } else {
                if (!Objects.equals(dep.getVersion(), ex.getVersion())) {
                        context.log().log(LogLevel.WARNING,
                                "Found already installed dependency " + toPURL(ex)
                                + " instead of version " + dep.getVersion());
                    } else {
                        context.log().log(LogLevel.INFO,
                                "Found already installed dependency " + toPURL(ex));
                    }
            }
        }
        
        return Optional.of(new Entry(root, files, provides));
    }

    @Override
    public void dispose() {
        if (mima != null) {
            mima.dispose();
            mima = null;
        }
    }

    private static Artifact parsePURL(String purlString) {
        PackageURL purl = PackageURL.parse(purlString);
        return new DefaultArtifact(
                purl.namespace().orElseThrow(IllegalArgumentException::new),
                purl.name(),
                purl.qualifiers().flatMap(q -> Optional.ofNullable(q.get("classifier"))).orElse(""),
                purl.qualifiers().flatMap(q -> Optional.ofNullable(q.get("type"))).orElse("jar"),
                purl.version().orElse(""));
    }
    
    private static PResource toPURL(Artifact artifact) {
        PackageURL.Builder builder = PackageURL.builder()
                .withType("maven")
                .withNamespace(artifact.getGroupId())
                .withName(artifact.getArtifactId());
        if (!artifact.getVersion().isBlank()) {
            builder.withVersion(artifact.getVersion());
        }
        if (!artifact.getClassifier().isBlank()) {
            builder.withQualifier("classifier", artifact.getClassifier());
        }
        if (!"jar".equals(artifact.getExtension())) {
            builder.withQualifier("type", artifact.getExtension());
        }
        return PResource.of(builder.build().toURI());
    }

    private static List<Artifact> buildInstalledList(List<PResource> provided) {
        if (provided.isEmpty()) {
            return List.of();
        }
        List<Artifact> list = new ArrayList<>(provided.size());
        for (var res : provided) {
            var str = res.toString();
            if (!str.startsWith("pkg:maven/")) {
                continue;
            }
            try {
                list.add(parsePURL(str));
            } catch (Exception ex) {
                // fall through
            }
        }
        return List.copyOf(list);
    }
    
    private static Artifact findExisting(List<Artifact> installed, Artifact artifact) {
        return installed.stream()
                .filter(lib -> matchingArtifacts(lib, artifact))
                .findFirst().orElse(null);
    }

    private static boolean matchingArtifacts(Artifact art1, Artifact art2) {
        return Objects.equals(art1, art2)
                || (Objects.equals(art1.getGroupId(), art2.getGroupId())
                && Objects.equals(art1.getArtifactId(), art2.getArtifactId())
                && Objects.equals(art1.getClassifier(), art2.getClassifier())
                && Objects.equals(art1.getExtension(), art2.getExtension()));
    }

    public static final class Provider implements LibraryResolver.Provider {

        @Override
        public LibraryResolver createResolver() {
            return new MimaResolver();
        }

    }

}
