package ai.vespa.secret.aws;

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

    private final Function<AwsRole, SecretsManagerClient> clientAndCredentialsSupplier;

    private final ConcurrentMap<AwsRole, SecretsManagerClient> clientMap = new ConcurrentHashMap<>();


    public AsmSecretStoreBase(ZtsClient ztsClient, AthenzDomain athenzDomain) {
        this(awsRole -> SecretsManagerClient.builder().region(Region.US_EAST_1)
                     .credentialsProvider(getAwsSessionCredsProvider(awsRole, ztsClient, athenzDomain))
                     .build()
        );
    }

    AsmSecretStoreBase(Function<AwsRole, SecretsManagerClient> clientAndCredentialsSupplier) {
        this.clientAndCredentialsSupplier = clientAndCredentialsSupplier;
    }

    /** Returns the AWS role associated with the given vault. */
    protected abstract AwsRole awsRole(VaultName vault);


    protected SecretsManagerClient getClient(VaultName vault) {
        var awsRole = awsRole(vault);
        clientMap.putIfAbsent(awsRole, clientAndCredentialsSupplier.apply(awsRole));
        return clientMap.get(awsRole);
    }

    private static AwsCredentialsProvider getAwsSessionCredsProvider(AwsRole role,
                                                                     ZtsClient ztsClient,
                                                                     AthenzDomain athenzDomain) {

        AwsCredentials credentials = new AwsCredentials(ztsClient, athenzDomain, role);
        return () -> {
            AwsTemporaryCredentials temporary = credentials.get();
            return AwsSessionCredentials.create(temporary.accessKeyId(),
                    temporary.secretAccessKey(),
                    temporary.sessionToken());
        };
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
