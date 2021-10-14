// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.attribute.UserPrincipalNotFoundException;

import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerUserPrincipal;
import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerGroupPrincipal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author valerijf
 */
class ContainerUserPrincipalLookupServiceTest {

    private final ContainerUserPrincipalLookupService userPrincipalLookupService =
            new ContainerUserPrincipalLookupService(TestFileSystem.create().getUserPrincipalLookupService(), 1000, 2000);

    @Test
    public void correctly_resolves_ids() throws IOException {
        ContainerUserPrincipal user = userPrincipalLookupService.lookupPrincipalByName("1000");
        assertEquals("vespa", user.getName());
        assertEquals("2000", user.baseFsPrincipal().getName());
        assertEquals(user, userPrincipalLookupService.lookupPrincipalByName("vespa"));

        ContainerGroupPrincipal group = userPrincipalLookupService.lookupPrincipalByGroupName("1000");
        assertEquals("vespa", group.getName());
        assertEquals("3000", group.baseFsPrincipal().getName());
        assertEquals(group, userPrincipalLookupService.lookupPrincipalByGroupName("vespa"));

        assertThrows(UserPrincipalNotFoundException.class, () -> userPrincipalLookupService.lookupPrincipalByName("test"));
    }

    @Test
    public void translates_between_ids() {
        assertEquals(1001, userPrincipalLookupService.containerUidToHostUid(1));
        assertEquals(2001, userPrincipalLookupService.containerGidToHostGid(1));
        assertEquals(1, userPrincipalLookupService.hostUidToContainerUid(1001));
        assertEquals(1, userPrincipalLookupService.hostGidToContainerGid(2001));

        assertEquals(65_534, userPrincipalLookupService.hostUidToContainerUid(1));
        assertEquals(65_534, userPrincipalLookupService.hostUidToContainerUid(999999));

        assertThrows(IllegalArgumentException.class, () -> userPrincipalLookupService.containerUidToHostUid(-1));
        assertThrows(IllegalArgumentException.class, () -> userPrincipalLookupService.containerUidToHostUid(70_000));
    }
}