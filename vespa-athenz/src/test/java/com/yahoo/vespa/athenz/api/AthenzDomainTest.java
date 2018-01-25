// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import org.junit.Test;

import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bjorncs
 */
public class AthenzDomainTest {

    @Test
    public void domain_can_be_constructed_from_valid_string() {
        new AthenzDomain("home.john.my-app");
    }

    @Test
    public void invalid_domain_throws_exception() {
        assertInvalid(() -> new AthenzDomain("endswithdot."));
        assertInvalid(() -> new AthenzDomain(".startswithdot"));
    }

    @Test
    public void parent_domain_is_without_name_suffix() {
        assertEquals(new AthenzDomain("home.john"), new AthenzDomain("home.john.myapp").getParent());
    }

    @Test
    public void domain_name_suffix_is_the_suffix_after_last_dot() {
        assertEquals("myapp", new AthenzDomain("home.john.myapp").getNameSuffix());
    }

    @Test
    public void domain_without_dot_is_toplevel() {
        assertTrue(new AthenzDomain("toplevel").isTopLevelDomain());
        assertFalse(new AthenzDomain("not.toplevel").isTopLevelDomain());
    }

    private static void assertInvalid(Supplier<AthenzDomain> domainCreator) {
        try {
            AthenzDomain domain = domainCreator.get();
            fail("Expected IllegalArgumentException for domain: " + domain.getName());
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), startsWith("Not a valid domain name"));
        }
    }


}
