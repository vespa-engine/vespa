// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author glebashnik
 * @author Martin Polden
 */
public class DockerImageTest {

    @Test
    void test_registry_valid() {
        // Hostname
        new DockerImage("registry.example.com", "vespa", Optional.empty());
        // Hostname with port
        new DockerImage("registry.example.com:5000", "vespa", Optional.empty());
        // Multi-component hostname
        new DockerImage("a.b.c.d", "vespa", Optional.empty());
        // Domain name with trailing dot
        new DockerImage("registry.example.com.", "vespa", Optional.empty());
        // IPv4
        new DockerImage("192.168.1.1", "vespa", Optional.empty());
        // IPv4 with port
        new DockerImage("192.168.1.1:5000", "vespa", Optional.empty());
        // IPv6
        new DockerImage("[::1]", "vespa", Optional.empty());
        new DockerImage("[2001:db8::1]", "vespa", Optional.empty());
    }

    @Test
    void test_registry_invalid() {
        // Empty
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("", "vespa", Optional.empty()));
        // Starts with dot
        assertThrows(IllegalArgumentException.class, () -> new DockerImage(".registry.example.com", "vespa", Optional.empty()));
        // Invalid characters
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry/example", "vespa", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry example", "vespa", Optional.empty()));
    }

    @Test
    void test_repository_valid() {
        // Simple
        new DockerImage("registry.example.com", "vespa", Optional.empty());
        // Multi-component
        new DockerImage("registry.example.com", "vespa/vespa", Optional.empty());
        new DockerImage("registry.example.com", "prefix/vespa/vespa", Optional.empty());
        // Separators
        new DockerImage("registry.example.com", "my.repo", Optional.empty());
        new DockerImage("registry.example.com", "my_repo", Optional.empty());
        new DockerImage("registry.example.com", "my__repo", Optional.empty());
        new DockerImage("registry.example.com", "my-repo", Optional.empty());
        new DockerImage("registry.example.com", "my--repo", Optional.empty());
    }

    @Test
    void test_repository_invalid() {
        // Empty
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "", Optional.empty()));
        // Uppercase
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "Vespa", Optional.empty()));
        // Starts or ends with separator
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "-vespa", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa-", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", ".vespa", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa.", Optional.empty()));
        // Empty path component
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa//vespa", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "/vespa", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa/", Optional.empty()));
    }

    @Test
    void test_tag_valid() {
        new DockerImage("registry.example.com", "vespa", Optional.of("latest"));
        new DockerImage("registry.example.com", "vespa", Optional.of("7.42"));
        new DockerImage("registry.example.com", "vespa", Optional.of("7.42.1"));
        new DockerImage("registry.example.com", "vespa", Optional.of("7.42-ext"));
        new DockerImage("registry.example.com", "vespa", Optional.of("ext7.42"));
        new DockerImage("registry.example.com", "vespa", Optional.of("tag-with-hyphen"));
        new DockerImage("registry.example.com", "vespa", Optional.of("tag_with_underscore"));
        new DockerImage("registry.example.com", "vespa", Optional.of("tag.with.dot"));
    }

    @Test
    void test_tag_invalid() {
        // Empty
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa", Optional.of("")));
        // Starts with invalid character
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa", Optional.of("-tag")));
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa", Optional.of(".tag")));
        // Too long (129 characters)
        assertThrows(IllegalArgumentException.class, () -> new DockerImage("registry.example.com", "vespa", Optional.of("a".repeat(129))));
    }

    @Test
    void test_empty_valid() {
        assertEquals(DockerImage.EMPTY, new DockerImage("", "", Optional.empty()));
    }

    @Test
    void test_fromString_valid() {
        // Empty string gives EMPTY
        assertEquals(DockerImage.EMPTY, DockerImage.fromString(""));
        // Correct components are parsed
        var image = DockerImage.fromString("registry.example.com:5000/vespa/vespa:7.42");
        assertEquals("registry.example.com:5000", image.registry());
        assertEquals("vespa/vespa", image.repository());
        assertEquals(Optional.of("7.42"), image.tag());
        // No tag
        assertEquals(Optional.empty(), DockerImage.fromString("registry.example.com/vespa").tag());
        // asString roundtrips
        assertEquals("registry.example.com/vespa:latest", DockerImage.fromString("registry.example.com/vespa:latest").asString());
        assertEquals("registry.example.com:5000/vespa/vespa:7.42", DockerImage.fromString("registry.example.com:5000/vespa/vespa:7.42").asString());
        assertEquals("192.168.1.1:5000/vespa", DockerImage.fromString("192.168.1.1:5000/vespa").asString());
        assertEquals("[::1]/vespa", DockerImage.fromString("[::1]/vespa").asString());
    }

    @Test
    void test_fromString_invalid() {
        // No slash (no registry)
        assertThrows(IllegalArgumentException.class, () -> DockerImage.fromString("registry.example.com"));
        assertThrows(IllegalArgumentException.class, () -> DockerImage.fromString("foo"));
        assertThrows(IllegalArgumentException.class, () -> DockerImage.fromString("foo:1.2.3"));
        // Empty repository
        assertThrows(IllegalArgumentException.class, () -> DockerImage.fromString("registry.example.com/"));
        // Empty tag
        assertThrows(IllegalArgumentException.class, () -> DockerImage.fromString("registry.example.com/vespa:"));
    }

    @Test
    void test_withRegistry_valid() {
        var image = DockerImage.fromString("registry.example.com/vespa:7.42");
        var result = image.withRegistry("other.registry.com");
        assertEquals("other.registry.com", result.registry());
        assertEquals(image.repository(), result.repository());
        assertEquals(image.tag(), result.tag());
    }

    @Test
    void test_withRegistry_invalid() {
        var image = DockerImage.fromString("registry.example.com/vespa:7.42");
        assertThrows(IllegalArgumentException.class, () -> image.withRegistry(""));
        assertThrows(IllegalArgumentException.class, () -> image.withRegistry("/invalid"));
    }

    @Test
    void test_withRepository_valid() {
        var image = DockerImage.fromString("registry.example.com/vespa:7.42");
        var result = image.withRepository("other/repo");
        assertEquals(image.registry(), result.registry());
        assertEquals("other/repo", result.repository());
        assertEquals(image.tag(), result.tag());
    }

    @Test
    void test_withRepository_invalid() {
        var image = DockerImage.fromString("registry.example.com/vespa:7.42");
        assertThrows(IllegalArgumentException.class, () -> image.withRepository(""));
        assertThrows(IllegalArgumentException.class, () -> image.withRepository("Invalid"));
    }

    @Test
    void test_withTag_valid() {
        var image = DockerImage.fromString("registry.example.com/vespa:7.42");
        var result = image.withTag(Optional.of("8.0"));
        assertEquals(image.registry(), result.registry());
        assertEquals(image.repository(), result.repository());
        assertEquals(Optional.of("8.0"), result.tag());
        // Clear tag
        assertEquals(Optional.empty(), image.withTag(Optional.empty()).tag());
    }

    @Test
    void test_withTag_invalid() {
        var image = DockerImage.fromString("registry.example.com/vespa:7.42");
        assertThrows(IllegalArgumentException.class, () -> image.withTag(Optional.of("")));
        assertThrows(IllegalArgumentException.class, () -> image.withTag(Optional.of("-invalid")));
    }
}