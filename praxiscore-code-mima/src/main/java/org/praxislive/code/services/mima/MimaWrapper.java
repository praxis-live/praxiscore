/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
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
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
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
        Dependency dependency = new Dependency(artifact, JavaScopes.COMPILE);
        
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        collectRequest.setRepositories(context.remoteRepositories());
        if (existing != null && !existing.isEmpty()) {
            collectRequest.setManagedDependencies(existing.stream()
                    .map(a -> new Dependency(a, JavaScopes.COMPILE))
                    .toList()
            );
        }
        
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,
                (node, parents) -> !node.getDependency().getOptional()
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

}
