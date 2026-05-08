// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author mpolden
 */
public class DockerImageTest {

    @Test
    void parse() {
        Map<String, DockerImage> tests = Map.of(
                               "", DockerImage.EMPTY,
                               "registry.example.com:9999/vespa/vespa:7.42", new DockerImage("registry.example.com:9999", "vespa/vespa", Optional.of("7.42")),
                               "registry.example.com/vespa/vespa:7.42", new DockerImage("registry.example.com", "vespa/vespa", Optional.of("7.42")),
                               "registry.example.com:9999/vespa/vespa", new DockerImage("registry.example.com:9999", "vespa/vespa", Optional.empty()),
                               "registry.example.com/vespa/vespa", new DockerImage("registry.example.com", "vespa/vespa", Optional.empty()),
                               "registry.example.com/project/repo/vespa/vespa", new DockerImage("registry.example.com/project/repo", "vespa/vespa", Optional.empty())
        );
        tests.forEach((value, expected) -> {
            DockerImage parsed = DockerImage.fromString(value);
            assertEquals(value, parsed.asString());

            String untagged = expected.equals(DockerImage.EMPTY)
                                   ? ""
                                   : expected.registry() + "/" + expected.repository();
            assertEquals(untagged, parsed.untagged());
        });
    }

    @Test
    void registry_cannot_contain_slash() {
        DockerImage image = DockerImage.fromString("registry.example.com/vespa/vespa");
        assertThrows(IllegalArgumentException.class, () -> image.withRegistry(""));
        assertThrows(IllegalArgumentException.class, () -> image.withRegistry("my-registry/path/"));
    }

    @Test
    void tag_suffix() {
        assertEquals("", DockerImage.fromString("registry.example.com/vespa/vespa").tagSuffix());
        assertEquals("", DockerImage.fromString("registry.example.com/vespa/vespa:7.42").tagSuffix());
        assertEquals("-alma9", DockerImage.fromString("registry.example.com/vespa/vespa:7.42-alma9").tagSuffix());
        assertEquals("-alma9-extra", DockerImage.fromString("registry.example.com/vespa/vespa:7.42-alma9-extra").tagSuffix());
    }

    @Test
    void with_tag_and_suffix() {
        DockerImage image = DockerImage.fromString("registry.example.com/vespa/vespa:7.42-alma9");
        Version version = Version.fromString("8.123.45");
        assertEquals("registry.example.com/vespa/vespa:8.123.45",
                     image.withTag(version).asString());
        assertEquals("registry.example.com/vespa/vespa:8.123.45-alma9",
                     image.withTag(version, image.tagSuffix()).asString());
        assertEquals("registry.example.com/vespa/vespa:8.123.45",
                     image.withTag(version, "").asString());
    }

    @Test
    void parse_invalid() {
        List<String> tests = List.of(
                               "registry.example.com",
                               "registry.example.com/",
                               "registry.example.com/repository",
                               "registry.example.com/repository:",
                               "foo",
                               "foo:1.2.3"
        );
        for (var value : tests) {
            try {
                DockerImage.fromString(value);
                fail("Expected failure for: " + value);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

}
