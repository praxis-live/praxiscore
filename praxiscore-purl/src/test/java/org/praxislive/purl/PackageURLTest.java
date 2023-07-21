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

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class PackageURLTest {

    @Test
    public void testPackageURLBuilder() {

        PackageURL purl = PackageURL.builder()
                .withType("my.type-9+")
                .withName("name")
                .build();

        assertEquals("pkg:my.type-9+/name", purl.toString());
        assertEquals("pkg:my.type-9+/name", purl.canonicalize());

        purl = PackageURL.builder()
                .withType("type")
                .withNamespace("namespace")
                .withName("name")
                .withVersion("version")
                .withQualifier("key", "value")
                .withSubpath("subpath")
                .build();

        assertEquals("pkg:type/namespace/name@version?key=value#subpath", purl.toString());

        purl = PackageURL.builder()
                .withType(PackageURL.StandardTypes.GENERIC)
                .withNamespace("namespace")
                .withName("name")
                .withVersion("version")
                .withQualifier("key_1.1-", "value")
                .withSubpath("subpath")
                .build();

        assertEquals("pkg:generic/namespace/name@version?key_1.1-=value#subpath", purl.toString());

        purl = PackageURL.builder()
                .withType(PackageURL.StandardTypes.GENERIC)
                .withNamespace("/////")
                .withName("name")
                .withVersion("version")
                .withQualifier("key", "value")
                .withSubpath("/////")
                .build();

        assertEquals("pkg:generic/name@version?key=value", purl.toString());

        purl = PackageURL.builder()
                .withType(PackageURL.StandardTypes.GENERIC)
                .withNamespace("")
                .withName("name")
                .withVersion("version")
                .withQualifier("key", "value")
                .withQualifier("next", "value")
                .withSubpath("")
                .build();

        assertEquals("pkg:generic/name@version?key=value&next=value", purl.toString());
    }

    @Test
    public void testPackageURLBuilderExceptions() {
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.builder()
                    .withType("type")
                    .withName("name")
                    .withQualifier("key", "")
                    .build();
        }, "Build should fail due to invalid qualifier (empty value)");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.builder()
                    .withType("type")
                    .withNamespace("invalid//namespace")
                    .withName("name")
                    .build();
        }, "Build should fail due to invalid namespace");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.builder()
                    .withType("typ^e")
                    .withSubpath("invalid/name%2Fspace")
                    .withName("name")
                    .build();
        }, "Build should fail due to invalid subpath");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.builder()
                    .withType("0_type")
                    .withName("name")
                    .build();
        }, "Build should fail due to invalid type");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.builder()
                    .withType("ype")
                    .withName("name")
                    .withQualifier("0_key", "value")
                    .build();
        }, "Build should fail due to invalid qualifier key");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.builder()
                    .withType("ype")
                    .withName("name")
                    .withQualifier("", "value")
                    .build();
        }, "Build should fail due to empty qualifier key");
    }

    @Test
    public void testParse() {
        PackageURL purl = PackageURL.parse("pkg:maven/org.apache.commons/io@1.3.4");
        assertEquals("maven", purl.type());
        assertEquals("org.apache.commons", purl.namespace().orElseThrow());
        assertEquals("io", purl.name());
        assertEquals("1.3.4", purl.version().orElseThrow());
        assertFalse(purl.qualifiers().isPresent());
        assertFalse(purl.subpath().isPresent());

        purl = PackageURL.parse("pkg:maven/org.apache.commons/io");
        assertEquals("maven", purl.type());
        assertEquals("org.apache.commons", purl.namespace().orElseThrow());
        assertEquals("io", purl.name());
        assertFalse(purl.version().isPresent());
        assertFalse(purl.qualifiers().isPresent());
        assertFalse(purl.subpath().isPresent());

        purl = PackageURL.parse("pkg:Maven/org.apache.xmlgraphics/batik-anim@1.9.1?repositorY_url=repo.spring.io/release&classifier=sources");
        assertEquals("pkg:maven/org.apache.xmlgraphics/batik-anim@1.9.1?classifier=sources&repository_url=repo.spring.io%2Frelease",
                purl.canonicalize());
        assertEquals("pkg:maven/org.apache.xmlgraphics/batik-anim@1.9.1?classifier=sources&repository_url=repo.spring.io%2Frelease",
                purl.toURI().toString());
        assertEquals("maven", purl.type());
        assertEquals("org.apache.xmlgraphics", purl.namespace().orElseThrow());
        assertEquals("batik-anim", purl.name());
        assertEquals("1.9.1", purl.version().orElseThrow());
        assertEquals(Map.of("classifier", "sources", "repository_url", "repo.spring.io/release"),
                purl.qualifiers().orElseThrow());
        assertFalse(purl.subpath().isPresent());

        purl = PackageURL.parse("pkg:maven/org.apache.commons/io");
        assertEquals("maven", purl.type());
        assertEquals("org.apache.commons", purl.namespace().orElseThrow());
        assertEquals("io", purl.name());
        assertFalse(purl.version().isPresent());
        assertFalse(purl.qualifiers().isPresent());
        assertFalse(purl.subpath().isPresent());

        purl = PackageURL.parse("pkg:GOLANG/google.golang.org/genproto@abcdef#/googleapis/api/annotations/");
        assertEquals("pkg:golang/google.golang.org/genproto@abcdef#googleapis/api/annotations",
                purl.canonicalize());
        assertEquals("golang", purl.type());
        assertEquals("google.golang.org", purl.namespace().orElseThrow());
        assertEquals("genproto", purl.name());
        assertEquals("abcdef", purl.version().orElseThrow());
        assertFalse(purl.qualifiers().isPresent());
        assertEquals("googleapis/api/annotations", purl.subpath().orElseThrow());

    }

    @Test
    public void testCanonicalEquals() throws Exception {
        PackageURL p1 = PackageURL.parse("pkg:generic/acme/example-component@1.0.0?key1=value1&key2=value2");
        PackageURL p2 = PackageURL.parse("pkg:generic/acme/example-component@1.0.0?key2=value2&key1=value1");
        assertTrue(p1.isCanonicalEquals(p2));
    }

    @Test
    public void testCoordinatesEquals() throws Exception {
        PackageURL p1 = PackageURL.parse("pkg:generic/acme/example-component@1.0.0?key1=value1&key2=value2");
        PackageURL p2 = PackageURL.parse("pkg:generic/acme/example-component@1.0.0");
        assertTrue(p2.isCoordinatesEquals(p1));
        assertTrue(p1.isCoordinatesEquals(p2));
    }

    @Test
    public void testParseExceptions() {
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.parse("");
        }, "Parse of invalid type should fail");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.parse("pkg:GOLANG/google.golang.org/genproto@abcdedf#invalid/%2F/subpath");
        }, "Parse of invalid subpath should fail");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.parse("pkg://generic:8080/name");
        }, "Parse with port number should fail");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.parse("pkg://generic/name?key=one&key=two");
        }, "Parse with duplicate keys should fail");
        assertThrows(IllegalArgumentException.class, () -> {
            PackageURL purl = PackageURL.parse("pkg:maven//io@1.3.4");
        }, "Maven namespace is required");
    }

}
