/*
 * (c) 2023 Neil C Smith
 * 
 * Adapted from https://github.com/package-url
 *
 * (c) 2020 Steve Springett
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.praxislive.purl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * Package-URL (aka purl) is a "mostly universal" URL to describe a package. A
 * purl is a URL composed of seven components:</p>
 * <pre>
 * scheme:type/namespace/name@version?qualifiers#subpath
 * </pre>
 * <p>
 * Components are separated by a specific character for unambiguous parsing. A
 * purl must NOT contain a URL Authority i.e. there is no support for username,
 * password, host and port components. A namespace segment may sometimes look
 * like a host but its interpretation is specific to a type.
 * </p>
 * <p>
 * SPEC:
 * <a href="https://github.com/package-url/purl-spec">https://github.com/package-url/purl-spec</a></p>
 *
 */
public final class PackageURL {

    private static final String UTF8 = StandardCharsets.UTF_8.name();
    private static final Pattern PATH_SPLITTER = Pattern.compile("/");

    /**
     * The PackageURL scheme constant
     */
    private final String scheme;

    /**
     * The package "type" or package "protocol" such as maven, npm, nuget, gem,
     * pypi, etc. Required.
     */
    private final String type;

    /**
     * The name prefix such as a Maven groupid, a Docker image owner, a GitHub
     * user or organization. Optional and type-specific.
     */
    private final String namespace;

    /**
     * The name of the package. Required.
     */
    private final String name;

    /**
     * The version of the package. Optional.
     */
    private final String version;

    /**
     * Extra qualifying data for a package such as an OS, architecture, a
     * distro, etc. Optional and type-specific.
     */
    private final Map<String, String> qualifiers;

    /**
     * Extra subpath within a package, relative to the package root. Optional.
     */
    private final String subpath;

    /**
     * The cached version of the canonical form.
     */
    private String canonicalizedForm;

    /**
     * Constructs a new PackageURL object.
     *
     * @param type the type of package (i.e. maven, npm, gem, etc)
     * @param namespace the name prefix (i.e. group, owner, organization)
     * @param name the name of the package
     * @param version the version of the package
     * @param qualifiers an array of key/value pair qualifiers
     * @param subpath the subpath string
     * @throws IllegalArgumentException if parsing fails
     */
    private PackageURL(final String type, final String namespace, final String name, final String version,
            final Map<String, String> qualifiers, final String subpath)
            throws IllegalArgumentException {

        this.scheme = validateScheme("pkg");
        this.type = validateType(type);
        this.namespace = validateNamespace(namespace);
        this.name = validateName(name);
        this.version = validateVersion(version);
        this.qualifiers = qualifiers == null ? null
                : Collections.unmodifiableMap(new TreeMap<>(validateQualifiers(qualifiers)));
        this.subpath = validatePath(subpath, true);
        verifyTypeConstraints(this.type, this.namespace, this.name);
    }

    /**
     * Returns the package url scheme.
     *
     * @return the scheme
     */
    String scheme() {
        return scheme;
    }

    /**
     * Returns the package "type" or package "protocol" such as maven, npm,
     * nuget, gem, pypi, etc.
     *
     * @return the type
     */
    public String type() {
        return type;
    }

    /**
     * Returns the name prefix such as a Maven groupid, a Docker image owner, a
     * GitHub user or organization.
     *
     * @return the namespace
     */
    public Optional<String> namespace() {
        return Optional.ofNullable(namespace);
    }

    /**
     * Returns the name of the package.
     *
     * @return the name of the package
     */
    public String name() {
        return name;
    }

    /**
     * Returns the version of the package.
     *
     * @return the version of the package
     */
    public Optional<String> version() {
        return Optional.ofNullable(version);
    }

    /**
     * Returns extra qualifying data for a package such as an OS, architecture,
     * a distro, etc. This method returns an UnmodifiableMap.
     *
     * @return qualifiers
     */
    public Optional<Map<String, String>> qualifiers() {
        return Optional.ofNullable(qualifiers);
    }

    /**
     * Returns extra subpath within a package, relative to the package root.
     *
     * @return the subpath
     */
    public Optional<String> subpath() {
        return Optional.ofNullable(subpath);
    }

    private String validateScheme(final String value) throws IllegalArgumentException {
        if ("pkg".equals(value)) {
            return "pkg";
        }
        throw new IllegalArgumentException("The PackageURL scheme is invalid");
    }

    private String validateType(final String value) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("The PackageURL type cannot be null or empty");
        }
        if (value.charAt(0) >= '0' && value.charAt(0) <= '9') {
            throw new IllegalArgumentException("The PackageURL type cannot start with a number");
        }
        final String retVal = value.toLowerCase(Locale.ENGLISH);
        if (retVal.chars().anyMatch(c -> !(c == '.' || c == '+' || c == '-'
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')))) {
            throw new IllegalArgumentException("The PackageURL type contains invalid characters");
        }
        return retVal;
    }

    private String validateNamespace(final String value) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return validateNamespace(value.split("/"));
    }

    private String validateNamespace(final String[] values) throws IllegalArgumentException {
        if (values == null || values.length == 0) {
            return null;
        }
        final String tempNamespace = validatePath(values, false);

        String retVal;
        switch (type) {
            case StandardTypes.BITBUCKET:
            case StandardTypes.DEBIAN:
            case StandardTypes.GITHUB:
            case StandardTypes.GOLANG:
            case StandardTypes.RPM:
                retVal = tempNamespace.toLowerCase(Locale.ENGLISH);
                break;
            default:
                retVal = tempNamespace;
                break;
        }
        return retVal;
    }

    private String validateName(final String value) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("The PackageURL name specified is invalid");
        }
        String temp;
        switch (type) {
            case StandardTypes.BITBUCKET:
            case StandardTypes.DEBIAN:
            case StandardTypes.GITHUB:
            case StandardTypes.GOLANG:
                temp = value.toLowerCase(Locale.ENGLISH);
                break;
            case StandardTypes.PYPI:
                temp = value.replaceAll("_", "-").toLowerCase(Locale.ENGLISH);
                break;
            default:
                temp = value;
                break;
        }
        return temp;
    }

    private String validateVersion(final String value) {
        if (value == null) {
            return null;
        }
        return value;
    }

    private static Map<String, String> validateQualifiers(final Map<String, String> values) throws IllegalArgumentException {
        if (values == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            validateKey(entry.getKey());
            final String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("The PackageURL specified contains a qualifier key with an empty or null value");
            }
        }
        return values;
    }

    private static String validateKey(final String value) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Qualifier key is invalid: " + value);
        }
        final String retValue = value.toLowerCase(Locale.ENGLISH);
        if ((value.charAt(0) >= '0' && value.charAt(0) <= '9')
                || !value.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_')) {
            throw new IllegalArgumentException("Qualifier key is invalid: " + value);
        }
        return retValue;
    }

    private static String validatePath(final String value, final boolean isSubpath) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return validatePath(value.split("/"), isSubpath);
    }

    private static String validatePath(final String[] segments, final boolean isSubpath) throws IllegalArgumentException {
        if (segments == null || segments.length == 0) {
            return null;
        }
        return Arrays.stream(segments)
                .map(segment -> {
                    if (isSubpath && ("..".equals(segment) || ".".equals(segment))) {
                        throw new IllegalArgumentException("Segments in the subpath may not be a period ('.') or repeated period ('..')");
                    } else if (segment.contains("/")) {
                        throw new IllegalArgumentException("Segments in the namespace and subpath may not contain a forward slash ('/')");
                    } else if (segment.isEmpty()) {
                        throw new IllegalArgumentException("Segments in the namespace and subpath may not be empty");
                    }
                    return segment;
                }).collect(Collectors.joining("/"));

    }

    /**
     * Returns the canonicalized representation of the purl.
     *
     * @return the canonicalized representation of the purl
     */
    @Override
    public String toString() {
        return canonicalize();
    }

    /**
     * Returns the canonicalized representation of the purl.
     *
     * @return the canonicalized representation of the purl
     */
    public String canonicalize() {
        if (canonicalizedForm != null) {
            return canonicalizedForm;
        }
        final StringBuilder purl = new StringBuilder();
        purl.append(scheme).append(":");
        if (type != null) {
            purl.append(type);
        }
        purl.append("/");
        if (namespace != null) {
            purl.append(encodePath(namespace));
            purl.append("/");
        }
        if (name != null) {
            purl.append(percentEncode(name));
        }
        if (version != null) {
            purl.append("@").append(percentEncode(version));
        }
        if (qualifiers != null && qualifiers.size() > 0) {
            purl.append("?");
            qualifiers.entrySet().stream().forEachOrdered((entry) -> {
                purl.append(entry.getKey().toLowerCase(Locale.ENGLISH));
                purl.append("=");
                purl.append(percentEncode(entry.getValue()));
                purl.append("&");
            });
            purl.setLength(purl.length() - 1);
        }
        if (subpath != null) {
            purl.append("#").append(encodePath(subpath));
        }
        canonicalizedForm = purl.toString();
        return canonicalizedForm;
    }

    /**
     * Create a URI from the canonical PackageURL.
     *
     * @return package url as uri
     */
    public URI toURI() {
        return URI.create(canonicalize());
    }

    /**
     * Encodes the input in conformance with RFC 3986.
     *
     * @param input the String to encode
     * @return an encoded String
     */
    private static String percentEncode(final String input) {
        try {
            return URLEncoder.encode(input, UTF8)
                    .replace("+", "%20")
                    // "*" is a reserved character in RFC 3986.
                    .replace("*", "%2A")
                    // "~" is an unreserved character in RFC 3986.
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            return input; // this should never occur
        }
    }

    /**
     * Optionally decodes a String, if it's encoded. If String is not encoded,
     * method will return the original input value.
     *
     * @param input the value String to decode
     * @return a decoded String
     */
    private static String percentDecode(final String input) {
        if (input == null) {
            return null;
        }
        try {
            final String decoded = URLDecoder.decode(input, UTF8);
            if (!decoded.equals(input)) {
                return decoded;
            }
        } catch (UnsupportedEncodingException e) {
            return input; // this should never occur
        }
        return input;
    }

    /**
     * Some purl types may have specific constraints. This method attempts to
     * verify them.
     *
     * @param type the purl type
     * @param namespace the purl namespace
     * @param name the purl name
     * @throws IllegalArgumentException if constraints are not met
     */
    private void verifyTypeConstraints(String type, String namespace, String name) throws IllegalArgumentException {
        if (StandardTypes.MAVEN.equals(type)) {
            if (namespace == null || namespace.isEmpty() || name == null || name.isEmpty()) {
                throw new IllegalArgumentException("The PackageURL specified is invalid. Maven requires both a namespace and name.");
            }
        }
    }

    @SuppressWarnings("StringSplitter")//reason: surprising behavior is okay in this case
    private static Map<String, String> parseQualifiers(final String encodedString) throws IllegalArgumentException {
        final Map<String, String> results = Arrays.stream(encodedString.split("&"))
                .collect(TreeMap<String, String>::new,
                        (map, value) -> {
                            final String[] entry = value.split("=", 2);
                            if (entry.length == 2 && !entry[1].isEmpty()) {
                                if (map.put(entry[0].toLowerCase(Locale.ENGLISH), percentDecode(entry[1])) != null) {
                                    throw new IllegalArgumentException("Duplicate package qualifier encountere - more then one value was specified for " + entry[0].toLowerCase(Locale.ENGLISH));
                                }
                            }
                        },
                        Map<String, String>::putAll);
        return validateQualifiers(results);

    }

    @SuppressWarnings("StringSplitter")//reason: surprising behavior is okay in this case
    private static String[] parsePath(final String value, final boolean isSubpath) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return PATH_SPLITTER.splitAsStream(value)
                .filter(segment -> !segment.isEmpty() && !(isSubpath && (".".equals(segment) || "..".equals(segment))))
                .map(segment -> percentDecode(segment))
                .toArray(String[]::new);
    }

    private String encodePath(final String path) {
        return Arrays.stream(path.split("/")).map(segment -> percentEncode(segment)).collect(Collectors.joining("/"));
    }

    /**
     * Evaluates if the specified Package URL has the same values up to, but
     * excluding the qualifier (querystring). This includes equivalence of:
     * scheme, type, namespace, name, and version, but excludes qualifier and
     * subpath from evaluation.
     *
     * @param purl the Package URL to evaluate
     * @return true if equivalence passes, false if not
     * @since 1.4.0
     */
    public boolean isCoordinatesEquals(final PackageURL purl) {
        return Objects.equals(scheme, purl.scheme)
                && Objects.equals(type, purl.type)
                && Objects.equals(namespace, purl.namespace)
                && Objects.equals(name, purl.name)
                && Objects.equals(version, purl.version);
    }

    /**
     * Evaluates if the specified Package URL has the same canonical value. This
     * method canonicalizes the Package URLs being evaluated and performs an
     * equivalence on the canonical values. Canonical equivalence is especially
     * useful for qualifiers, which can be in any order, but have a predictable
     * order in canonicalized form.
     *
     * @param purl the Package URL to evaluate
     * @return true if equivalence passes, false if not
     * @since 1.2.0
     */
    public boolean isCanonicalEquals(final PackageURL purl) {
        return (this.canonicalize().equals(purl.canonicalize()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PackageURL other = (PackageURL) o;
        return Objects.equals(scheme, other.scheme)
                && Objects.equals(type, other.type)
                && Objects.equals(namespace, other.namespace)
                && Objects.equals(name, other.name)
                && Objects.equals(version, other.version)
                && Objects.equals(qualifiers, other.qualifiers)
                && Objects.equals(subpath, other.subpath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, type, namespace, name, version, qualifiers, subpath);
    }

    /**
     * Parse a PackageURL from the given String.
     *
     * @param purl the purl string to parse
     * @throws IllegalArgumentException if an exception occurs when parsing
     */
    public static PackageURL parse(final String purl) {
        if (purl == null || purl.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid purl: Contains an empty or null value");
        }
        try {
            final URI uri = new URI(purl);
            return from(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid purl: " + e.getMessage());
        }
    }

    /**
     * Convert the provided URI to a PackageURL.
     *
     * @param uri the uri to convert
     * @throws IllegalArgumentException if the uri is not a valid package url
     */
    public static PackageURL from(URI uri) throws IllegalArgumentException {
        // Check to ensure that none of these parts are parsed. If so, it's an invalid purl.
        if (uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("Invalid purl: Contains parts not supported by the purl spec");
        }

        String scheme = uri.getScheme();
        String subpath = null;

        // subpath is optional - check for existence
        if (uri.getRawFragment() != null && !uri.getRawFragment().isEmpty()) {
            subpath = validatePath(parsePath(uri.getRawFragment(), true), true);
        }
        // This is the purl (minus the scheme) that needs parsed.
        final StringBuilder remainder = new StringBuilder(uri.getRawSchemeSpecificPart());

        // qualifiers are optional - check for existence
        int index = remainder.lastIndexOf("?");
        Map<String, String> qualifiers;
        if (index >= 0) {
            qualifiers = parseQualifiers(remainder.substring(index + 1));
            remainder.setLength(index);
        } else {
            qualifiers = null;
        }

        // trim leading and trailing '/'
        int end = remainder.length() - 1;
        while (end > 0 && '/' == remainder.charAt(end)) {
            end--;
        }
        if (end < remainder.length() - 1) {
            remainder.setLength(end + 1);
        }
        int start = 0;
        while (start < remainder.length() && '/' == remainder.charAt(start)) {
            start++;
        }

        // type
        index = remainder.indexOf("/", start);
        if (index <= start) {
            throw new IllegalArgumentException("Invalid purl: does not contain both a type and name");
        }
        String type = remainder.substring(start, index);
        start = index + 1;

        // version is optional - check for existence
        String version;
        index = remainder.lastIndexOf("@");
        if (index >= start) {
            version = percentDecode(remainder.substring(index + 1));
            remainder.setLength(index);
        } else {
            version = null;
        }

        // The 'remainder' should now consist of the an optional namespace, and the name
        String name;
        String namespace;
        index = remainder.lastIndexOf("/");
        if (index <= start) {
            name = percentDecode(remainder.substring(start));
            namespace = null;
        } else {
            name = percentDecode(remainder.substring(index + 1));
            remainder.setLength(index);
            namespace = remainder.substring(start);
        }
        return new PackageURL(type, namespace, name, version, qualifiers, subpath);

    }

    /**
     * Get a builder for creating a PackageURL from elements.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience constants that defines common Package-URL 'type's.
     *
     * @since 1.0.0
     */
    static class StandardTypes {

        public static final String BITBUCKET = "bitbucket";
        public static final String CARGO = "cargo";
        public static final String COMPOSER = "composer";
        public static final String DEBIAN = "deb";
        public static final String DOCKER = "docker";
        public static final String GEM = "gem";
        public static final String GENERIC = "generic";
        public static final String GITHUB = "github";
        public static final String GOLANG = "golang";
        public static final String HEX = "hex";
        public static final String MAVEN = "maven";
        public static final String NPM = "npm";
        public static final String NUGET = "nuget";
        public static final String PYPI = "pypi";
        public static final String RPM = "rpm";
    }

    /**
     * A builder construct for Package-URL objects.
     */
    public static final class Builder {

        private String type = null;
        private String namespace = null;
        private String name = null;
        private String version = null;
        private String subpath = null;
        private Map<String, String> qualifiers = null;

        private Builder() {
        }

        /**
         * Adds the package URL type.
         *
         * @param type the package type
         * @return a reference to the builder
         * @see PackageURL#name()
         * @see com.github.packageurl.PackageURL.StandardTypes
         */
        public Builder withType(final String type) {
            this.type = type;
            return this;
        }

        /**
         * Adds the package namespace.
         *
         * @param namespace the package namespace
         * @return a reference to the builder
         * @see PackageURL#namespace()
         */
        public Builder withNamespace(final String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Adds the package name.
         *
         * @param name the package name
         * @return a reference to the builder
         * @see PackageURL#name()
         */
        public Builder withName(final String name) {
            this.name = name;
            return this;
        }

        /**
         * Adds the package version.
         *
         * @param version the package version
         * @return a reference to the builder
         * @see PackageURL#version()
         */
        public Builder withVersion(final String version) {
            this.version = version;
            return this;
        }

        /**
         * Adds the package subpath.
         *
         * @param subpath the package subpath
         * @return a reference to the builder
         * @see PackageURL#subpath()
         */
        public Builder withSubpath(final String subpath) {
            this.subpath = subpath;
            return this;
        }

        /**
         * Adds a package qualifier.
         *
         * @param key the package qualifier key
         * @param value the package qualifier value
         * @return a reference to the builder
         * @see PackageURL#qualifiers()
         */
        public Builder withQualifier(final String key, final String value) {
            if (qualifiers == null) {
                qualifiers = new TreeMap<>();
            }
            qualifiers.put(key, value);
            return this;
        }

        /**
         * Builds the new PackageURL object.
         *
         * @return the new PackageURL object
         */
        public PackageURL build() {
            return new PackageURL(type, namespace, name, version, qualifiers, subpath);
        }
    }
}
