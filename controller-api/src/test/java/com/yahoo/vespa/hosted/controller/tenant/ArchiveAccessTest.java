package com.yahoo.vespa.hosted.controller.tenant;// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class ArchiveAccessTest {

    @Test
    void validatesUserProvidedIamRole() {
        assertValidIamRole("arn:aws:iam::012345678912:user/foo");
        assertValidIamRole("arn:aws:iam::012345678912:role/foo");

        assertInvalidIamRole("arn:aws:iam::012345678912:foo/foo", "Invalid resource type - must be either a 'role' or 'user'");
        assertInvalidIamRole("arn:aws:iam::012345678912:foo", "Missing resource type - must be 'role' or 'user'");
        assertInvalidIamRole("arn:aws:iam::012345678912:role", "Missing resource type - must be 'role' or 'user'");
        assertInvalidIamRole("arn:aws:iam::012345678912:", "Malformed ARN - no resource specified");
        assertInvalidIamRole("arn:aws:iam::01234567891:user/foo", "Account id must be a 12-digit number");
        assertInvalidIamRole("arn:gcp:iam::012345678912:user/foo", "Partition must be 'aws'");
        assertInvalidIamRole("uri:aws:iam::012345678912:user/foo", "Malformed ARN - doesn't start with 'arn:'");
        assertInvalidIamRole("arn:aws:s3:::mybucket", "Service must be 'iam'");
        assertInvalidIamRole("", "Malformed ARN - doesn't start with 'arn:'");
        assertInvalidIamRole("foo", "Malformed ARN - doesn't start with 'arn:'");
    }

    private static void assertValidIamRole(String role) { assertDoesNotThrow(() -> archiveAccess(role)); }

    private static void assertInvalidIamRole(String role, String expectedMessage) {
        var t = assertThrows(IllegalArgumentException.class, () -> archiveAccess(role));
        var expectedPrefix = Text.format("Invalid archive access IAM role '%s': ", role);
        System.out.println(t.getMessage());
        assertTrue(t.getMessage().startsWith(expectedPrefix), role);
        assertEquals(expectedMessage, t.getMessage().substring(expectedPrefix.length()));
    }

    private static ArchiveAccess archiveAccess(String iamRole) { return new ArchiveAccess().withAWSRole(iamRole); }

}