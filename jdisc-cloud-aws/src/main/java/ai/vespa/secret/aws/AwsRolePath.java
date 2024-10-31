/*
 * // Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 */

package ai.vespa.secret.aws;

import com.yahoo.vespa.athenz.api.AwsRole;

import java.util.Objects;

/**
 * An AWS role with path. We use paths because AWS roles can only be up to 64 chars long.
 * Note that AWS role names must be unique across paths within an account.
 *
 * @author gjoranv
 */
public record AwsRolePath(AwsPath path, AwsRole role) {

    public AwsRolePath {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
    }

    public static AwsRolePath fromStrings(String path, String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("roleName cannot be null or empty");
        }
        return new AwsRolePath(AwsPath.fromAwsPathString(path), new AwsRole(roleName));
    }

    public static AwsRolePath atRoot(String roleName) {
        return new AwsRolePath(AwsPath.of(), new AwsRole(roleName));
    }

    public String fullName() {
        return "%s%s".formatted(path.value(), role.name());
    }

    // Only for compatibility with existing APIs in AwsCredentials
    public AwsRole fullRole() {
        return new AwsRole(fullName());
    }

    @Override
    public String toString() {
        return "AwsRolePath{" + path + ", " + role.name() + '}';
    }

}
