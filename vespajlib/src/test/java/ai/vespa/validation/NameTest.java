// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
class NameTest {

    @Test
    void testNames() {
        Name.of("name123-_-");
        Name.of("O".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> Name.of("0"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("_"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("-"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("foo."));
        assertThrows(IllegalArgumentException.class, () -> Name.of("foo/"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("foo%"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("w".repeat(65)));
    }

}
