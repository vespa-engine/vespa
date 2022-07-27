// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
public class AthenzDomainTest {

    @Test
    void domain_can_be_constructed_from_valid_string() {
        new AthenzDomain("home.john.my-app");
    }

    @Test
    void invalid_domain_throws_exception() {
        assertInvalid(() -> new AthenzDomain("endswithdot."));
        assertInvalid(() -> new AthenzDomain(".startswithdot"));
    }

    @Test
    void parent_domain_is_without_name_suffix() {
        assertEquals(new AthenzDomain("home.john"), new AthenzDomain("home.john.myapp").getParent());
    }

    @Test
    void domain_name_suffix_is_the_suffix_after_last_dot() {
        assertEquals("myapp", new AthenzDomain("home.john.myapp").getNameSuffix());
    }

    @Test
    void domain_without_dot_is_toplevel() {
        assertTrue(new AthenzDomain("toplevel").isTopLevelDomain());
        assertFalse(new AthenzDomain("not.toplevel").isTopLevelDomain());
    }

    private static void assertInvalid(Supplier<AthenzDomain> domainCreator) {
        try {
            AthenzDomain domain = domainCreator.get();
            fail("Expected IllegalArgumentException for domain: " + domain.getName());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Not a valid domain name"));
        }
    }


}
