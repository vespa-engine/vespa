// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.aws;

import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.VaultName;


/**
 * Tenant secret store constants and functions used across modules and repos.
 * Note that we cannot use SystemName and TenantName from config-provision here,
 * as that bundle is not available in tenant containers.
 * @author gjoranv
 */
public class AthenzUtil {

    public static final String PREFIX = "tenant-secret";

    public static String roleAndPolicyPrefix(String systemName, String tenantName) {
        return "%s.%s.%s".formatted(PREFIX, systemName, tenantName).toLowerCase();
    }

    public static String resourceEntityName(String system, String tenant, VaultName vault) {
        // Note that the domain name is added by AthenzDomainName, such that actual resource name will be
        // e.g. vespa.external.tenant-secret:<aws-role-name>
        // The aws role name is: tenant-secret.<system>.<tenant>.<vault>.reader
        return "%s.%s.%s".formatted(roleAndPolicyPrefix(system, tenant),
                                    vault.value(),
                                    Role.READER.value())
                .toLowerCase();
    }

}
