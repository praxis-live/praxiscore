/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
 * Loosely based on GrapeIvy.groovy from Apache Groovy, and licensed under ASL.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.sort.SortOptions;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;

/**
 *
 */
class Grappa {
    
    private final Ivy ivy;
    
    private List<ExcludeRule> excludes;
    
    private Grappa(Ivy ivy) {
        this.ivy = ivy;
        excludes = List.of();
    }
    
    void setExcludes(String excludes) {
        this.excludes = List.copyOf(parseExcludes(excludes));
    }
    
    
    
    @SuppressWarnings("unchecked")
    List<IvyNode> sort(List<IvyNode> nodes) {
        return (List<IvyNode>) ivy.execute(new Ivy.IvyCallback() {
            @Override
            public Object doInIvyContext(Ivy ivy, IvyContext context) {
                return ivy.sortNodes(nodes, SortOptions.DEFAULT);
            }
        });
        
    }
    
    ResolveReport resolve(MavenArtefactInfo dep) {
        return (ResolveReport) ivy.execute(new Ivy.IvyCallback() {
            @Override
            public Object doInIvyContext(Ivy ivy, IvyContext context) {
                var millis = System.currentTimeMillis();
                var md = new DefaultModuleDescriptor(
                        ModuleRevisionId.newInstance("grappa", "all-grappa", "working" + millis),
                        "integration", null, true);
                md.addConfiguration(new Configuration("default"));
                md.setLastModified(millis);
                
                var version = dep.version().isBlank() ? "latest.release" : dep.version();
                var mrid = ModuleRevisionId.newInstance(dep.group(), dep.artefact(), version);
                var dd = new DefaultDependencyDescriptor(md, mrid, false, false, true);
                dd.addDependencyConfiguration("default", "default");
                
                if (dep.classifier() != null && !dep.classifier().isBlank()) {
                    var dad = new DefaultDependencyArtifactDescriptor(dd, mrid.getName(),
                            "jar", "jar", null, Map.of("classifier", dep.classifier()));
                    dd.addDependencyArtifact("default", dad);
                }
                
                md.addDependency(dd);
                
                excludes.forEach(md::addExcludeRule);
                
                var options = new ResolveOptions();
                options.setConfs(new String[]{"default"});
                options.setOutputReport(false);
                
                ResolveReport report = null;
                int attempt = 8;
                while (true) {
                    try {
                        report = ivy.resolve(md, options);
                        break;
                    } catch (Exception e) {
                        if (attempt-- > 0) {
                            try {
                                Thread.sleep(250);
                                continue;
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                        throw new RuntimeException(e);
                    }
                }
                
                var cacheManager = ivy.getResolutionCacheManager();
                cacheManager.getResolvedIvyFileInCache(md.getModuleRevisionId()).delete();
                cacheManager.getResolvedIvyPropertiesInCache(md.getModuleRevisionId()).delete();
                
                return report;
                
            }
        });
    }
    
    private List<ExcludeRule> parseExcludes(String excludes) {
        try {
            var arr = PArray.parse(excludes);
            List<ExcludeRule> lst = new ArrayList<>(arr.size());
            for (Value v : arr) {
                String[] split = v.toString().split(":");
                if (split.length != 2) {
                    throw new IllegalArgumentException();
                }
                var exclude = new DefaultExcludeRule(
                        new ArtifactId(new ModuleId(split[0], split[1]),
                                PatternMatcher.ANY_EXPRESSION,
                                PatternMatcher.ANY_EXPRESSION,
                                PatternMatcher.ANY_EXPRESSION),
                        ExactPatternMatcher.INSTANCE, null);
                exclude.addConfiguration("default");
                lst.add(exclude);
            }
            return lst;
        } catch (Exception ex) {
            System.getLogger(IvyResolver.class.getName())
                    .log(System.Logger.Level.ERROR, "Invalid excludes", ex);
            return List.of();
        }
    }
    
    static Grappa create() throws Exception {
        var ivy = Ivy.newInstance();
        ivy.configureDefault();
        return new Grappa(ivy);
    }
    
}
