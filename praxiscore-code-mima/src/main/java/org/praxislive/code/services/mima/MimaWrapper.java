/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 *
 */
class MimaWrapper {

    private final Context context;

    MimaWrapper() {
        Runtime runtime = Runtimes.INSTANCE.getRuntime();
        context = runtime.create(ContextOverrides.create().withUserSettings(true).build());
    }

    List<Artifact> resolve(Artifact artifact, List<Artifact> existing) throws DependencyResolutionException {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRepositories(context.remoteRepositories());

        if (existing != null && !existing.isEmpty()) {
            List<Dependency> managedDeps = existing.stream()
                    .flatMap(this::artifactToDeps)
                    .toList();
            collectRequest.setManagedDependencies(managedDeps);
            collectRequest.addDependency(toDependency(artifact, managedDeps));
        } else {
            collectRequest.addDependency(toDependency(artifact, List.of()));
        }

        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,
                (node, parents) -> {
                    return node == null
                    || node.getDependency() == null
                    || !node.getDependency().isOptional();
                }
        );
        DependencyResult dependencyResult = context.repositorySystem()
                .resolveDependencies(context.repositorySystemSession(), dependencyRequest);
        List<ArtifactResult> artifacts = dependencyResult.getArtifactResults();
        return artifacts.stream()
                .filter(ArtifactResult::isResolved)
                .map(ArtifactResult::getArtifact)
                .toList();

    }

    void dispose() {
        context.close();
    }

    private Stream<Dependency> artifactToDeps(Artifact artifact) {
        if ("pom".equals(artifact.getExtension())) {
            ArtifactDescriptorRequest req = new ArtifactDescriptorRequest()
                    .setArtifact(artifact);
            try {
                ArtifactDescriptorResult res = context.repositorySystem()
                        .readArtifactDescriptor(context.repositorySystemSession(), req);
                return res.getManagedDependencies().stream();
            } catch (ArtifactDescriptorException ex) {
                return Stream.empty();
            }
        } else {
            return Stream.of(new Dependency(artifact, JavaScopes.COMPILE));
        }
    }

    private Dependency toDependency(Artifact artifact, List<Dependency> managedDeps) {
        if (artifact.getVersion().isEmpty()) {
            return managedDeps.stream()
                    .filter(d -> isMatchingDependency(d, artifact))
                    .findFirst()
                    .orElseGet(() -> new Dependency(artifact, JavaScopes.COMPILE));
        } else {
            return new Dependency(artifact, JavaScopes.COMPILE);
        }
    }

    private boolean isMatchingDependency(Dependency dependency, Artifact artifact) {
        Artifact dep = dependency.getArtifact();
        return Objects.equals(dep.getGroupId(), artifact.getGroupId())
                && Objects.equals(dep.getArtifactId(), artifact.getArtifactId())
                && Objects.equals(dep.getClassifier(), artifact.getClassifier())
                && Objects.equals(dep.getExtension(), artifact.getExtension());
    }

}
