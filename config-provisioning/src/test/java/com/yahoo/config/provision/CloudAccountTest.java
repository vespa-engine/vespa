// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 */
class CloudAccountTest {

    @Test
    void aws_accounts() {
        CloudAccount oldFormat = CloudAccount.from("123456789012");
        CloudAccount newFormat = CloudAccount.from("aws:123456789012");
        assertEquals(oldFormat, newFormat);

        for (CloudAccount account : List.of(oldFormat, newFormat)) {
            assertFalse(account.isUnspecified());
            assertEquals(account, CloudAccount.from(account.value()));
            assertEquals("123456789012", account.account());
            assertEquals(CloudName.AWS, account.cloudName());
            assertEquals("aws:123456789012", account.value());
        }
    }

    @Test
    void gcp_accounts() {
        CloudAccount oldFormat = CloudAccount.from("my-project");
        CloudAccount newFormat = CloudAccount.from("gcp:my-project");
        assertEquals(oldFormat, newFormat);

        for (CloudAccount account : List.of(oldFormat, newFormat)) {
            assertFalse(account.isUnspecified());
            assertEquals(account, CloudAccount.from(account.value()));
            assertEquals("my-project", account.account());
            assertEquals(CloudName.GCP, account.cloudName());
            assertEquals("gcp:my-project", account.value());
        }
    }

    @Test
    void default_accounts() {
        CloudAccount variant1 = CloudAccount.from("");
        CloudAccount variant2 = CloudAccount.from("default");
        assertEquals(variant1, variant2);

        for (CloudAccount account : List.of(variant1, variant2)) {
            assertTrue(account.isUnspecified());
            assertEquals(account, CloudAccount.from(account.value()));
            assertEquals("", account.account());
            assertEquals(CloudName.DEFAULT, account.cloudName());
            assertEquals("", account.value());
        }
    }

    @Test
    void invalid_accounts() {
        assertInvalidAccount("aws:123", "Invalid cloud account 'aws:123': Account ID must match '[0-9]{12}'");
        assertInvalidAccount("gcp:123", "Invalid cloud account 'gcp:123': Project ID must match '[a-z][a-z0-9-]{4,28}[a-z0-9]'");
        assertInvalidAccount("$something", "Invalid cloud account '$something': Must be on format '<cloud-name>:<account>' or 'default'");
        assertInvalidAccount("unknown:account", "Invalid cloud account 'unknown:account': Cloud name must be one of: aws, gcp");
    }

    private static void assertInvalidAccount(String account, String message) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> CloudAccount.from(account));
        assertEquals(message, exception.getMessage());
    }
}
