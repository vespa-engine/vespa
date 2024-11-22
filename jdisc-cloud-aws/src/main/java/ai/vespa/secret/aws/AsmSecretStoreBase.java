// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.aws;

import ai.vespa.secret.model.ExternalId;
import ai.vespa.secret.model.VaultName;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import com.yahoo.vespa.athenz.aws.AwsCredentials;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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

    private final Function<AssumedRoleInfo, SecretsManagerClient> clientAndCredentialsSupplier;

    private final ConcurrentMap<AssumedRoleInfo, SecretsManagerClient> clientMap = new ConcurrentHashMap<>();


    public AsmSecretStoreBase(ZtsClient ztsClient, AthenzDomain athenzDomain) {
        this(assumedRoleInfo -> SecretsManagerClient.builder().region(Region.US_EAST_1)
                     .credentialsProvider(getAwsSessionCredsProvider(assumedRoleInfo, ztsClient, athenzDomain))
                     .build()
        );
    }

    // For testing
    protected AsmSecretStoreBase(Function<AssumedRoleInfo, SecretsManagerClient> clientAndCredentialsSupplier) {
        this.clientAndCredentialsSupplier = clientAndCredentialsSupplier;
    }

    /** Returns the AWS role associated with the given vault. */
    protected abstract AwsRolePath awsRole(VaultName vault);

    protected ExternalId externalId(VaultName vault) {
        return null;
    }

    protected SecretsManagerClient getClient(VaultName vault) {
        var awsRole = awsRole(vault);
        var externalId = externalId(vault);
        var assumedRoleInfo = new AssumedRoleInfo(awsRole, Optional.ofNullable(externalId));
        clientMap.putIfAbsent(assumedRoleInfo, clientAndCredentialsSupplier.apply(assumedRoleInfo));
        return clientMap.get(assumedRoleInfo);
    }

    private static AwsCredentialsProvider getAwsSessionCredsProvider(AssumedRoleInfo roleInfo,
                                                                     ZtsClient ztsClient,
                                                                     AthenzDomain athenzDomain) {

        AwsCredentials credentials = new AwsCredentials(ztsClient, athenzDomain, roleInfo.role().athenzAwsRole(), roleInfo.externalId().map(ExternalId::value).orElse(null));
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

    // Only for testing
    public Set<AwsRolePath> clientRoleNames() {
        return new HashSet<>(clientMap.keySet().stream().map(AssumedRoleInfo::role).toList());
    }
}
