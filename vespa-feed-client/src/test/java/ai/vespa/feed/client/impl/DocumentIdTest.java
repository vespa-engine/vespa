// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.DocumentId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 */
class DocumentIdTest {

    @Test
    void testParsing() {
        Assertions.assertEquals("id:ns:type::user",
                                DocumentId.of("id:ns:type::user").toString());

        assertEquals("id:ns:type:n=123:user",
                     DocumentId.of("id:ns:type:n=123:user").toString());

        assertEquals("id:ns:type:g=foo:user",
                     DocumentId.of("id:ns:type:g=foo:user").toString());

        assertEquals("id:ns:type::user::specific",
                     DocumentId.of("id:ns:type::user::specific").toString());

        assertEquals("id:ns:type:::",
                     DocumentId.of("id:ns:type:::").toString());

        assertThrows(IllegalArgumentException.class,
                     () -> DocumentId.of("idd:ns:type:user"));

        assertThrows(IllegalArgumentException.class,
                     () -> DocumentId.of("id:ns::user"));

        assertThrows(IllegalArgumentException.class,
                     () -> DocumentId.of("id::type:user"));

        assertThrows(IllegalArgumentException.class,
                     () -> DocumentId.of("id:ns:type:g=:user"));

        assertThrows(IllegalArgumentException.class,
                     () -> DocumentId.of("id:ns:type:n=:user"));

        assertThrows(NumberFormatException.class,
                     () -> DocumentId.of("id:ns:type:n=foo:user"));

        assertThrows(IllegalArgumentException.class,
                     () -> DocumentId.of("id:ns:type::"));
    }

}
