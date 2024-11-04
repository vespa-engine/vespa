// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.aws;

import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.VaultId;
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

    /* tenant-secret.<system>.<tenant>.<vaultName>.reader */
    public static String resourceEntityName(String system, String tenant, VaultName vault) {
        // Note that the domain name is added by AthenzDomainName, such that actual resource name will be
        // e.g. vespa.external.tenant-secret:<aws-role-name>
        return "%s.%s".formatted(roleAndPolicyPrefix(system, tenant),
                                 athenzReaderRoleName(vault))
                .toLowerCase();
    }

    /* <vaultName>.reader */
    public static String athenzReaderRoleName(VaultName vault) {
        return "%s.%s".formatted(vault.value(), Role.READER.value());
    }

    /* Path: /tenant-secret/<system>/<tenant>/ */
    public static AwsPath awsPath(String systemName, String tenantName) {
        return AwsPath.of(PREFIX, systemName, tenantName);
    }

    /*
     * Path: /tenant-secret/<system>/<tenant>/ + Role: <vaultId>.reader
     *
     * We use vaultId instead of vaultName because vaultName is not unique across tenants,
     * and role names must be unique across paths within an account.
     */
    public static AwsRolePath awsReaderRole(String systemName, String tenantName, VaultId vaultId) {
        return new AwsRolePath(awsPath(systemName, tenantName), new AwsRole(awsReaderRoleName(vaultId)));
    }

    /* <vaultName>.reader */
    private static String awsReaderRoleName(VaultId vaultId) {
        return "%s.%s".formatted(vaultId.value(), Role.READER.value());
    }

}
