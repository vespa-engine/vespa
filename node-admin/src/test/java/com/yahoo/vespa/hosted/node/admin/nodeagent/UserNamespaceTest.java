// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author freva
 */
class UserNamespaceTest {

    private final UserNamespace userNamespace = new UserNamespace(1000, 2000, 10000);

    @Test
    public void translates_between_ids() {
        assertEquals(1001, userNamespace.userIdOnHost(1));
        assertEquals(2001, userNamespace.groupIdOnHost(1));
        assertEquals(1, userNamespace.userIdInContainer(1001));
        assertEquals(1, userNamespace.groupIdInContainer(2001));

        assertEquals(userNamespace.overflowId(), userNamespace.userIdInContainer(1));
        assertEquals(userNamespace.overflowId(), userNamespace.userIdInContainer(999999));

        assertThrows(IllegalArgumentException.class, () -> userNamespace.userIdOnHost(-1));
        assertThrows(IllegalArgumentException.class, () -> userNamespace.userIdOnHost(70_000));
    }
}