// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.Objects;

/**
 * Represents a reference to a certificate and private key.
 *
 * @author mortent
 * @author andreer
 */
public class ApplicationCertificate {

    private final String secretsKeyNamePrefix;

    public ApplicationCertificate(String secretsKeyNamePrefix) {
        this.secretsKeyNamePrefix = secretsKeyNamePrefix;
    }

    public String secretsKeyNamePrefix() {
        return secretsKeyNamePrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationCertificate that = (ApplicationCertificate) o;
        return Objects.equals(secretsKeyNamePrefix, that.secretsKeyNamePrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secretsKeyNamePrefix);
    }
}
