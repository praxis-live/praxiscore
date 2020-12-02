/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
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
package org.praxislive.code;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.praxislive.core.Lookup;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.types.PResource;

/**
 * Service for resolving library resources to one or more local paths for
 * compilation. Look up available {@link Provider} via service loader to create
 * instances. Instances are not intended to be reusable.
 */
public interface LibraryResolver {

    /**
     * Resolve the provided resource to an {@link Entry} with paths to add for
     * compilation. The resolved entry will be from the first resolver to return
     * a result. The entry should only contain additional paths required to
     * resolve the requested resource on top of the already resolved resources
     * in the provided context. The resolver may return an alternative resource
     * in the entry if an earlier resolution provides the required library, eg.
     * at a different version. The resolver should throw an exception if the
     * resource is a type it should be able to resolve but for some reason
     * cannot.
     *
     * @param resource library resource
     * @param context context, including already resolved paths
     * @return optional list of paths
     * @throws Exception if the resolver claims the resource but cannot resolve
     */
    public Optional<Entry> resolve(PResource resource, Context context) throws Exception;

    /**
     * Optional hook for disposing of cached resources. The instance should not
     * be used after this method has been called.
     */
    public default void dispose() {
    }

    /**
     * Context providing access to existing resolved libraries and the lookup
     * for resource resolution, etc.
     */
    public static interface Context extends Lookup.Provider {

        /**
         * List of already resolved libraries.
         *
         * @return resolved libraries
         */
        public List<Entry> resolved();

        /**
         * A log builder for logging library resolution.
         *
         * @return log
         */
        public LogBuilder log();

    }

    /**
     * Provider interface for creating instances of {@link LibraryResolver}.
     */
    public static interface Provider {

        /**
         * Create an instance of a resolver.
         *
         * @return resolver
         */
        public LibraryResolver createResolver();

    }

    /**
     * Data type giving a resource and the additional, resolved local paths that
     * were added for that resource. The paths list might be empty if another,
     * earlier entry provided all required paths.
     */
    public final static class Entry {

        private final PResource resource;
        private final List<Path> paths;

        public Entry(PResource resource, List<Path> paths) {
            this.resource = Objects.requireNonNull(resource);
            this.paths = List.copyOf(paths);
        }

        public PResource resource() {
            return resource;
        }

        public List<Path> paths() {
            return paths;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 17 * hash + Objects.hashCode(this.resource);
            hash = 17 * hash + Objects.hashCode(this.paths);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Entry other = (Entry) obj;
            if (!Objects.equals(this.resource, other.resource)) {
                return false;
            }
            if (!Objects.equals(this.paths, other.paths)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "Entry{" + "resource=" + resource + ", paths=" + paths + '}';
        }

    }

}
