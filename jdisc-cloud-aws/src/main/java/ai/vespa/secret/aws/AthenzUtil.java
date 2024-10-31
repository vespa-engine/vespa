// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.aws;

import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.VaultName;
import com.yahoo.vespa.athenz.api.AwsRole;


/**
 * Tenant secret store constants and functions used across modules and repos.
 * Note that we cannot use SystemName and TenantName from config-provision here,
 * as that bundle is not available in tenant containers.
 *
 * @author gjoranv
 */
public class AthenzUtil {

    // Serves as a namespace for resources in athenz and AWS
    public static final String PREFIX = "tenant-secret";

    /* tenant-secret.<system>.<tenant> */
    public static String roleAndPolicyPrefix(String systemName, String tenantName) {
        return String.join(".", PREFIX, systemName, tenantName).toLowerCase();
    }

    /* tenant-secret.<system>.<tenant>.<vault>.reader */
    public static String resourceEntityName(String system, String tenant, VaultName vault) {
        // Note that the domain name is added by AthenzDomainName, such that actual resource name will be
        // e.g. vespa.external.tenant-secret:<aws-role-name>
        return "%s.%s".formatted(roleAndPolicyPrefix(system, tenant),
                                 readerRoleName(vault))
                .toLowerCase();
    }

    /* Path: /tenant-secret/<system>/<tenant>/ */
    public static AwsPath awsPath(String systemName, String tenantName) {
        return AwsPath.of(PREFIX, systemName, tenantName);
    }

    /* Path: /tenant-secret/<system>/<tenant>/ + Role: <vault>.reader */
    public static AwsRolePath awsReaderRole(String systemName, String tenantName, VaultName vault) {
        return new AwsRolePath(awsPath(systemName, tenantName), new AwsRole(readerRoleName(vault)));
    }

    /* <vault>.reader */
    private static String readerRoleName(VaultName vault) {
        return "%s.%s".formatted(vault.value(), Role.READER.value());
    }

}
