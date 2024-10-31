/*
 * // Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 */

package ai.vespa.secret.aws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class AwsPathTest {

    @Test
    void it_starts_and_ends_with_slash() {
        var path = AwsPath.of("a", "b", "c");
        assertEquals("/a/b/c/", path.value());
    }

    @Test
    void it_is_lowercased() {
        var path = AwsPath.of("A", "B", "C");
        assertEquals("/a/b/c/", path.value());
    }

    @Test
    void empty_path_yields_single_slash() {
        var path = AwsPath.of();
        assertEquals("/", path.value());
    }

}
