// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoutingPolicyRepositoryTest {

    @Test
    public void policy_creation_does_not_swallow_exception() {
        final RoutingPolicyRepository repo = new RoutingPolicyRepository();
        final RoutingPolicyFactory factory = mock(RoutingPolicyFactory.class);

        when(factory.createPolicy(anyString())).thenThrow(new IllegalArgumentException("oh no!"));
        repo.putFactory("foo", factory);

        try {
            repo.createPolicy("foo", "bar");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("oh no!", e.getMessage());
        }
    }

}
