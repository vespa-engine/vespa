//  Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.secret.aws;

import ai.vespa.secret.model.ExternalId;

import java.util.Objects;
import java.util.Optional;

/**
 * Information used to assume an AWS role.
 * @param role The role and path to assume
 * @param externalId The external ID to use when assuming the role, <em>Optional.empty()</em> if not required
 * @author mortent
 */
public record AssumedRoleInfo(AwsRolePath role, Optional<ExternalId> externalId) {

    public AssumedRoleInfo {
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(externalId, "externalId cannot be null");
    }

    public static AssumedRoleInfo of(AwsRolePath role) {
        return new AssumedRoleInfo(role, Optional.empty());
    }

    public static AssumedRoleInfo of(AwsRolePath role, ExternalId externalId) {
        return new AssumedRoleInfo(role, Optional.ofNullable(externalId));
    }
}
