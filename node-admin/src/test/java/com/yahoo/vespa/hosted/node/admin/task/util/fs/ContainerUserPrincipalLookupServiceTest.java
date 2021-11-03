// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.nodeagent.UserNamespace;
import com.yahoo.vespa.hosted.node.admin.nodeagent.UserScope;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.attribute.UserPrincipalNotFoundException;

import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerGroupPrincipal;
import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerUserPrincipal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author freva
 */
class ContainerUserPrincipalLookupServiceTest {

    private final UserScope userScope = UserScope.create(new UnixUser("vespa", 1000, "users", 100), new UserNamespace(10_000, 11_000, 10000));
    private final ContainerUserPrincipalLookupService userPrincipalLookupService =
            new ContainerUserPrincipalLookupService(TestFileSystem.create().getUserPrincipalLookupService(), userScope);

    @Test
    public void correctly_resolves_ids() throws IOException {
        ContainerUserPrincipal user = userPrincipalLookupService.lookupPrincipalByName("1000");
        assertEquals("vespa", user.getName());
        assertEquals("11000", user.baseFsPrincipal().getName());
        assertEquals(user, userPrincipalLookupService.lookupPrincipalByName("vespa"));

        ContainerGroupPrincipal group = userPrincipalLookupService.lookupPrincipalByGroupName("100");
        assertEquals("users", group.getName());
        assertEquals("11100", group.baseFsPrincipal().getName());
        assertEquals(group, userPrincipalLookupService.lookupPrincipalByGroupName("users"));

        assertThrows(UserPrincipalNotFoundException.class, () -> userPrincipalLookupService.lookupPrincipalByName("test"));
    }
}
