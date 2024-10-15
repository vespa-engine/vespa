package ai.vespa.secret.aws;

import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.VaultName;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AwsRole;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import com.yahoo.vespa.athenz.aws.AwsCredentials;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Base class for AWS Secrets Manager read or write clients.
 *
 * @author gjoranv
 */
public abstract class AsmSecretStoreBase extends AbstractComponent implements AutoCloseable {

    public static final String AWSCURRENT = "AWSCURRENT";

    private final ConcurrentMap<VaultName, SecretsManagerClient> clientMap = new ConcurrentHashMap<>();
    private final Function<VaultName, SecretsManagerClient> clientAndCredentialsSupplier;


    public AsmSecretStoreBase(ZtsClient ztsClient, Role role, AthenzDomain athenzDomain) {
        this(vault -> SecretsManagerClient.builder().region(Region.US_EAST_1)
                .credentialsProvider(getAwsSessionCredsProvider(ztsClient, athenzDomain, role, vault))
                .build());
    }

    AsmSecretStoreBase(Function<VaultName, SecretsManagerClient> clientAndCredentialsSupplier) {
        this.clientAndCredentialsSupplier = clientAndCredentialsSupplier;
    }


    protected SecretsManagerClient getClient(VaultName vaultName) {
        // TODO: Use role name instead of vault name
        return clientMap.computeIfAbsent(vaultName, clientAndCredentialsSupplier);
    }

    private static AwsCredentialsProvider getAwsSessionCredsProvider(ZtsClient ztsClient, AthenzDomain athenzDomain, Role role, VaultName vaultName) {
        var awsRole = new AwsRole(role.forVault(vaultName));
        AwsCredentials credentials = new AwsCredentials(ztsClient, athenzDomain, awsRole);

        return () -> {
            AwsTemporaryCredentials temporary = credentials.get();
            return AwsSessionCredentials.create(temporary.accessKeyId(),
                    temporary.secretAccessKey(),
                    temporary.sessionToken());
        };
    }

    protected String awsSecretId(Key key) {
        return key.vaultName().value() + "/" + key.secretName();
    }

    @Override
    public void close() {
        clientMap.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to close", e);
            }
        });
    }

    @Override
    public void deconstruct() {
        close();
        super.deconstruct();
    }

}
