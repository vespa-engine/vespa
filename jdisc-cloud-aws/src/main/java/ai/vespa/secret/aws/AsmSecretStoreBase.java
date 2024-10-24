package ai.vespa.secret.aws;

import ai.vespa.secret.model.Key;
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

    private final Function<VaultName, SecretsManagerClient> clientAndCredentialsSupplier;

    private final AwsRoleMapper roleMapper;
    private final ConcurrentMap<AwsRole, SecretsManagerClient> clientMap = new ConcurrentHashMap<>();


    public AsmSecretStoreBase(AwsRoleMapper roleMapper, ZtsClient ztsClient, AthenzDomain athenzDomain) {
        this(roleMapper,
             vault -> SecretsManagerClient.builder().region(Region.US_EAST_1)
                     .credentialsProvider(getAwsSessionCredsProvider(roleMapper, ztsClient, athenzDomain, vault))
                     .build()
        );
    }

    AsmSecretStoreBase(AwsRoleMapper roleMapper, Function<VaultName, SecretsManagerClient> clientAndCredentialsSupplier) {
        this.roleMapper = roleMapper;
        this.clientAndCredentialsSupplier = clientAndCredentialsSupplier;
    }


    protected SecretsManagerClient getClient(VaultName vault) {
        var awsRole = roleMapper.awsRole(vault);
        clientMap.putIfAbsent(awsRole, clientAndCredentialsSupplier.apply(vault));
        return clientMap.get(awsRole);
    }

    private static AwsCredentialsProvider getAwsSessionCredsProvider(AwsRoleMapper roleMapper,
                                                                     ZtsClient ztsClient,
                                                                     AthenzDomain athenzDomain,
                                                                     VaultName vaultName) {

        AwsCredentials credentials = new AwsCredentials(ztsClient, athenzDomain, roleMapper.awsRole(vaultName));
        return () -> {
            AwsTemporaryCredentials temporary = credentials.get();
            return AwsSessionCredentials.create(temporary.accessKeyId(),
                    temporary.secretAccessKey(),
                    temporary.sessionToken());
        };
    }

    protected String awsSecretId(Key key) {
        return key.vaultName().value() + "/" + key.secretName().value();
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
