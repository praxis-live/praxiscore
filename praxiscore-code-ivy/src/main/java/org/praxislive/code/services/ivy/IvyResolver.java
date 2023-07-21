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
package org.praxislive.code.services.ivy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.praxislive.code.LibraryResolver;
import org.praxislive.core.services.LogLevel;
import org.praxislive.core.types.PResource;
import org.praxislive.purl.PackageURL;

/**
 *
 */
public class IvyResolver implements LibraryResolver {

    private final Grappa grappa;

    private IvyResolver() {
        Grappa g = null;
        try {
            g = Grappa.create();
        } catch (Exception ex) {
            System.getLogger(IvyResolver.class.getName())
                    .log(System.Logger.Level.ERROR, "Unable to initialise Ivy support", ex);
        }
        this.grappa = g;
    }

    @Override
    public Optional<Entry> resolve(PResource resource, Context context)
            throws Exception {
        var res = resource.toString();
        if (!res.startsWith("pkg:maven/")) {
            return Optional.empty();
        }
        var artefact = parsePURL(res);
        var installed = buildInstalledList(context.provided().collect(Collectors.toList()));
        var existing = findExisting(installed, artefact);
        if (existing != null) {
            if (!artefact.version().isBlank()
                    && !Objects.equals(existing.version(), artefact.version())) {
                context.log().log(LogLevel.WARNING,
                        resource + " already installed at version " + existing.version());
            }
            return Optional.of(new Entry(toPURL(existing), List.of()));
        }

        var report = grappa.resolve(artefact);

        var problemMsg = report.getAllProblemMessages().stream().collect(Collectors.joining("\n"));

        if (report.hasError()) {
            throw new IllegalStateException("Error resolving " + res
                    + (problemMsg.isBlank() ? "" : "\n" + problemMsg));
        } else if (!problemMsg.isBlank()) {
            context.log().log(LogLevel.WARNING, problemMsg);
        }

        var nodes = grappa.sort(report.getDependencies());
        var installing = new ArrayList<MavenArtefactInfo>();
        var resolved = resource;
        var provides = new ArrayList<PResource>();
        var files = new ArrayList<Path>();
        for (var node : nodes) {
            for (var download : report.getArtifactsReports(node.getResolvedId())) {
                var info = toInfo(download);
                var ex = findExisting(installed, info);
                if (ex == null) {
                    var purl = toPURL(info);
                    if (artefact.isMatchingArtefact(info)) {
                        resolved = purl;
                    }
                    provides.add(purl);
                    installing.add(info);
                    var file = download.getLocalFile();
                    if (file == null) {
                        context.log().log(LogLevel.ERROR, "No file found for " + info);
                    } else {
                        files.add(file.toPath());
                    }
                } else {
                    if (!Objects.equals(info.version(), ex.version())) {
                        context.log().log(LogLevel.WARNING,
                                "Found already installed dependency " + ex
                                + " instead of version " + info.version());
                    } else {
                        context.log().log(LogLevel.INFO,
                                "Found already installed dependency " + ex);
                    }
                }
            }
        }

        return Optional.of(new Entry(resolved, files, provides));
    }

    private static MavenArtefactInfo parsePURL(String purlString) {
        var purl = PackageURL.parse(purlString);
        return new MavenArtefactInfo(
                purl.namespace().orElseThrow(IllegalArgumentException::new),
                purl.name(),
                purl.version().orElse(""),
                purl.qualifiers().flatMap(q -> Optional.ofNullable(q.get("classifier"))).orElse("")
        );
    }

    private static PResource toPURL(MavenArtefactInfo info) {
        var builder = PackageURL.builder()
                .withType("maven")
                .withNamespace(info.group())
                .withName(info.artefact());
        if (!info.version().isBlank()) {
            builder.withVersion(info.version());
        }
        if (!info.classifier().isBlank()) {
            builder.withQualifier("classifier", info.classifier());
        }
        return PResource.of(builder.build().toURI());
    }

    private static MavenArtefactInfo toInfo(ArtifactDownloadReport report) {
        var mrid = report.getArtifact().getId().getModuleRevisionId();
        var group = mrid.getOrganisation();
        var artefact = mrid.getName();
        var version = mrid.getRevision();
        var classifier = report.getArtifact().getId()
                .getQualifiedExtraAttributes().getOrDefault("classifier", "");
        return new MavenArtefactInfo(group, artefact, version, classifier);
    }

    private static List<MavenArtefactInfo> buildInstalledList(List<PResource> provided) {
        if (provided.isEmpty()) {
            return List.of();
        }
        List<MavenArtefactInfo> list = new ArrayList<>(provided.size());
        for (var res : provided) {
            var str = res.toString();
            if (!str.startsWith("pkg:maven")) {
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

    private static MavenArtefactInfo findExisting(List<MavenArtefactInfo> installed, MavenArtefactInfo artefact) {
        return installed.stream()
                .filter(artefact::isMatchingArtefact)
                .findFirst().orElse(null);
    }

    public static class Provider implements LibraryResolver.Provider {

        @Override
        public LibraryResolver createResolver() {
            return new IvyResolver();
        }

    }

}
