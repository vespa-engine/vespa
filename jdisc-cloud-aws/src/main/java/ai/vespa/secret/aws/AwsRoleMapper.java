package ai.vespa.secret.aws;

import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.VaultName;
import com.yahoo.vespa.athenz.api.AwsRole;

/**
 * This is just syntactic sugar over raw function.
 *
 * @author gjoranv
 */
@FunctionalInterface
public interface AwsRoleMapper {

    AwsRole awsRole(VaultName vault);

    static AwsRoleMapper controlPlaneReader() {
        return (vault -> new AwsRole(vault.value() + "-" + Role.READER));
    }

    static AwsRoleMapper controlPlaneWriter() {
        return (vault -> new AwsRole(vault.value() + "-" + Role.WRITER));
    }

}
