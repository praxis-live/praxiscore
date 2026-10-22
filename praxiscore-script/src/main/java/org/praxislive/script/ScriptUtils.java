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
package org.praxislive.script;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PResource;

/**
 * Various helper utilities for implementing Pcl commands.
 */
public final class ScriptUtils {

    private ScriptUtils() {
        throw new Error();
    }

    /**
     * Query the current working directory from a namespace. The working
     * directory is used to resolve relative file references. It may not be a
     * local file location. If not set within the provided namespace, the
     * working directory defaults to the JDK's current working directory.
     *
     * @param namespace namespace to query
     * @return current working directory
     */
    public static URI currentWorkingDirectory(Namespace namespace) {
        return Optional.ofNullable(namespace.getVariable(Env.PWD))
                .flatMap(v -> PResource.from(v.getValue()))
                .map(PResource::value)
                .orElse(new File("").toURI());
    }

    /**
     * Find the global namespace at the root of the provided namespace.
     *
     * @param namespace child namespace
     * @return global namespace if available
     */
    public static final Optional<Namespace> findGlobalNamespace(Namespace namespace) {
        return Optional.ofNullable(
                namespace.getVariable("_GLOBAL_NAMESPACE"))
                .map(Variable::getValue)
                .flatMap(PReference::from)
                .flatMap(ref -> ref.as(Namespace.class));
    }

    /**
     * Resolve a path against the current working directory of the provided
     * namespace. If the provided path is an absolute URI, it will be returned
     * as is. Otherwise the path will be resolved against the current working
     * directory of the namespace.
     *
     * @param namespace namespace to query
     * @param path path to resolve
     * @return resolved URI
     * @throws URISyntaxException on invalid path
     */
    public static URI resolvePath(Namespace namespace, String path) throws URISyntaxException {
        if (path.contains(":")) {
            try {
                URI uri = new URI(path);
                if (uri.isAbsolute()) {
                    return uri;
                }
            } catch (URISyntaxException ex) {
                // fall through?
            }
        }
        URI base = currentWorkingDirectory(namespace);
        URI uri = base.resolve(new URI(null, null, path, null));
        if ("file".equals(base.getScheme())) {
            uri = new File(uri).toURI();
        }
        return uri;
    }

    /**
     * List files relative to the current working directory of the namespace.
     * The provided glob path is used to filter results. To list all files pass
     * {@code "*"}.
     * <p>
     * If the glob path includes slashes, the path will first be resolved. Glob
     * characters prior to the last slash will be treated as literal path
     * characters. A glob path ending in a slash will be treated as if it ends
     * with {@code "/*"}.
     *
     * @implNote this method currently only works for files on the local file
     * system. It might be extended to support other sources in future.
     *
     * @param namespace namespace to query
     * @param globPath glob path
     * @return list of matching paths
     * @throws IOException on failure
     */
    public static List<URI> listFiles(Namespace namespace, String globPath) throws IOException {
        int lastSlash = globPath.lastIndexOf("/");
        String path = "";
        if (lastSlash > 0) {
            path = globPath.substring(0, lastSlash);
        }
        String glob = "*";
        if (lastSlash < (globPath.length() - 1)) {
            glob = globPath.substring(lastSlash + 1);
        }
        return listFiles(namespace, path, glob);
    }

    private static List<URI> listFiles(Namespace namespace, String path, String glob) throws IOException {
        try {
            URI base = path.isEmpty() ? currentWorkingDirectory(namespace) : resolvePath(namespace, path);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(base), glob)) {
                return StreamSupport.stream(stream.spliterator(), false)
                        .sorted()
                        .map(Path::toUri)
                        .toList();
            }
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
    }

}
