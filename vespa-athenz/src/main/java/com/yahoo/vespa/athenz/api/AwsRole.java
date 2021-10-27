// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.net.URLEncoder;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author mortent
 */
public class AwsRole {

    private final String name;
    private final String encodedName;

    public AwsRole(String awsRoleName) {
        this.name = awsRoleName;
        // Encoded twice for zts compatibility
        this.encodedName = URLEncoder.encode(URLEncoder.encode(this.name, UTF_8), UTF_8);
    }

    public String encodedName() {
        return encodedName;
    }

    public String name() {
        return name;
    }

    public static AwsRole from(AthenzIdentity identity) {
        return new AwsRole(identity.getFullName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AwsRole awsRole = (AwsRole) o;
        return Objects.equals(name, awsRole.name) &&
               Objects.equals(encodedName, awsRole.encodedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, encodedName);
    }
}
