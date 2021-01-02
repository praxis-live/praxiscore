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
import java.util.stream.Stream;
import org.praxislive.core.Lookup;
import org.praxislive.core.services.LogBuilder;
import org.praxislive.core.types.PResource;

/**
 * Service for resolving library resources to one or more local paths for
 * compilation. Look up available {@link Provider} via service loader to create
 * instances. Instances are not intended to be reusable and so may maintain
 * their own cache of previously resolved entries.
 */
public interface LibraryResolver {

    /**
     * Resolve the provided resource to an {@link Entry} with local files to add
     * for compilation. The resolved entry will be from the first resolver to
     * return a result. The entry should only contain additional files required
     * to resolve the requested resource on top of the already resolved
     * resources in the provided context.
     * <p>
     * The resolver may return an alternative primary resource in the entry if
     * an earlier resolution provides the required library, eg. at a different
     * version. The entry may also report additional resources that have been
     * provided by the additional files, such as additional transitive
     * dependencies or alternative identifiers.
     * <p>
     * The resolver should throw an exception if the resource is a type it
     * should be able to resolve but for some reason cannot.
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
     * Context providing access to existing resolved libraries and files, the
     * Lookup for resource resolution, and a LogBuilder for reporting
     * information and errors.
     */
    public static interface Context extends Lookup.Provider {

        /**
         * Stream of already resolved libraries.
         *
         * @return resolved libraries
         */
        public Stream<PResource> resolved();

        /**
         * Stream of already resolved libraries, including transitive
         * dependencies and/or alternative identifiers.
         *
         * @return resolved and transitive libraries
         */
        public Stream<PResource> provided();

        /**
         * Stream of local files from already resolved libraries.
         *
         * @return resolved files
         */
        public Stream<Path> files();

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
     * Data type giving a resource and the additional, resolved local files that
     * were added for that resource. The files list might be empty if another,
     * earlier entry provided all required files.
     * <p>
     * A list can be used to report all resources that have been provided by the
     * additional files, such as additional transitive dependencies or
     * alternative identifiers. The primary resource must also be included in
     * this list.
     */
    public final static class Entry {

        private final PResource resource;
        private final List<PResource> provides;
        private final List<Path> files;

        /**
         * Create an Entry for the provided resource and any additional required
         * files.
         *
         * @param resource resolved library identifier
         * @param files additional local files (may be empty)
         */
        public Entry(PResource resource, List<Path> files) {
            this(resource, files, List.of(resource));
        }

        /**
         * Create an Entry for the provided resources and any additional
         * required files. Also provide a list of all additional resources added
         * by this entry, including
         *
         * @param resource resolved library identifier
         * @param files additional local files (may be empty)
         * @param provides all resources provided by this entry
         * @throws IllegalArgumentException if provides does not contain
         * resource
         */
        public Entry(PResource resource, List<Path> files, List<PResource> provides) {
            this.resource = Objects.requireNonNull(resource);
            this.files = List.copyOf(files);
            this.provides = List.copyOf(provides);
            if (!this.provides.contains(resource)) {
                throw new IllegalArgumentException("Resource not in provides");
            }
        }

        /**
         * Get the primary resource.
         *
         * @return primary resource
         */
        public PResource resource() {
            return resource;
        }

        /**
         * Get the list of all provided resources. Will include the primary
         * resource and possibly any transitive dependencies or alternative
         * identifiers.
         *
         * @return all provided resources
         */
        public List<PResource> provides() {
            return provides;
        }

        /**
         * All additional files required to resolve the primary resource and its
         * dependencies, within the existing context. May be empty.
         *
         * @return additional required files
         */
        public List<Path> files() {
            return files;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + Objects.hashCode(this.resource);
            hash = 23 * hash + Objects.hashCode(this.provides);
            hash = 23 * hash + Objects.hashCode(this.files);
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
            if (!Objects.equals(this.provides, other.provides)) {
                return false;
            }
            if (!Objects.equals(this.files, other.files)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "Entry{" + "resource=" + resource + ", provides=" + provides
                    + ", files=" + files + '}';
        }

    }

}
