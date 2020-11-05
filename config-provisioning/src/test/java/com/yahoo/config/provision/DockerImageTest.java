// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class DockerImageTest {

    @Test
    public void parse() {
        Map<String, DockerImage> tests = Map.of(
                "", DockerImage.EMPTY,
                "registry.example.com:9999/vespa/vespa:7.42", new DockerImage("registry.example.com:9999", "vespa/vespa", Optional.of("7.42"), Optional.empty()),
                "registry.example.com/vespa/vespa:7.42", new DockerImage("registry.example.com", "vespa/vespa", Optional.of("7.42"), Optional.empty()),
                "registry.example.com:9999/vespa/vespa", new DockerImage("registry.example.com:9999", "vespa/vespa", Optional.empty(), Optional.empty()),
                "registry.example.com/vespa/vespa", new DockerImage("registry.example.com", "vespa/vespa", Optional.empty(), Optional.empty())
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
    public void parse_invalid() {
        List<String> tests = List.of(
                "registry.example.com",
                "registry.example.com/",
                "foo",
                "foo:1.2.3"
        );
        for (var value : tests) {
            try {
                DockerImage.fromString(value);
                fail("Expected failure");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Test
    public void empty_replacement() {
        DockerImage image = new DockerImage("foo.example.com", "vespa/vespa", Optional.of("7.42"), Optional.empty());
        assertTrue(image.withReplacedBy(DockerImage.EMPTY).replacedBy().isEmpty());
    }

}
