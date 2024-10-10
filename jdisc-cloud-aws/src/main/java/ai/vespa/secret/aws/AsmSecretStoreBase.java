package ai.vespa.secret.aws;

import ai.vespa.secret.model.Key;
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

    private final ConcurrentMap<AwsRole, SecretsManagerClient> clientMap = new ConcurrentHashMap<>();
    private final Function<AwsRole, SecretsManagerClient> clientAndCredentialsSupplier;


    public AsmSecretStoreBase(ZtsClient ztsClient, AthenzDomain athenzDomain, AwsRole awsRole) {
        this(role -> SecretsManagerClient.builder().region(Region.US_EAST_1)
                .credentialsProvider(getAwsSessionCredsProvider(ztsClient, athenzDomain, awsRole))
                .build());
    }

    AsmSecretStoreBase(Function<AwsRole, SecretsManagerClient> clientAndCredentialsSupplier) {
        this.clientAndCredentialsSupplier = clientAndCredentialsSupplier;
    }


    protected SecretsManagerClient getClient(AwsRole awsRole) {
        return clientMap.computeIfAbsent(awsRole, clientAndCredentialsSupplier);
    }

    private static AwsCredentialsProvider getAwsSessionCredsProvider(ZtsClient ztsClient,
                                                                     AthenzDomain athenzDomain,
                                                                     AwsRole awsRole) {
        //var awsRole = new AwsRole(role.forControlPlaneVault(vaultName));

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
