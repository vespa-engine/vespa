package ai.vespa.secret.aws;

import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.VaultName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;


/**
 * Tenant secret store constants and functions used across modules and repos.
 *
 * @author gjoranv
 */
public class AthenzUtil {

    public static final String PREFIX = "tenant-secret";

    public static String roleAndPolicyPrefix(SystemName system, TenantName tenant) {
        return "%s.%s.%s".formatted(PREFIX, system.value(), tenant.value()).toLowerCase();
    }

    public static String resourceEntityName(SystemName system, TenantName tenant, VaultName vault) {
        // Note that the domain name is added by AthenzDomainName, such that actual resource name will be
        // e.g. vespa.external.tenant-secret:<aws-role-name>
        // The aws role name is: tenant-secret.<system>.<tenant>.<vault>.reader
        return "%s.%s.%s".formatted(roleAndPolicyPrefix(system, tenant), vault.value(), Role.READER).toLowerCase();
    }

}
