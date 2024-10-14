package ai.vespa.secret.aws;

import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.VaultName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AwsRole;

/**
 * Maps a {@link VaultName} to an {@link AwsRole}.
 *
 * @author gjoranv
 */
@FunctionalInterface
public interface AwsRoleMapper {

    AwsRole awsRole(VaultName vault);

    static AwsRoleMapper infrastructureReader() {
        return (vault -> new AwsRole(vault.value() + "-" + Role.READER));
    }

    static AwsRoleMapper infrastructureWriter() {
        return (vault -> new AwsRole(vault.value() + "-" + Role.WRITER));
    }

    static AwsRoleMapper tenantReader(SystemName system, TenantName tenant) {
        return (vault -> new AwsRole(AthenzUtil.resourceEntityName(system, tenant, vault)));
    }

    static AwsRoleMapper tenantWriter() {
        return ( __ -> new AwsRole(Role.TENANT_SECRET_WRITER.value()));
    }

}
