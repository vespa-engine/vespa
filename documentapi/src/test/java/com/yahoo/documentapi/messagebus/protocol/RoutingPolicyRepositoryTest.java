// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoutingPolicyRepositoryTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void policy_creation_does_not_swallow_exception() {
        final RoutingPolicyRepository repo = new RoutingPolicyRepository();
        final RoutingPolicyFactory factory = mock(RoutingPolicyFactory.class);

        when(factory.createPolicy(anyString())).thenThrow(new IllegalArgumentException("oh no!"));
        repo.putFactory("foo", factory);

        expectedException.expectMessage("oh no!");
        expectedException.expect(IllegalArgumentException.class);

        repo.createPolicy("foo", "bar");
    }

}
