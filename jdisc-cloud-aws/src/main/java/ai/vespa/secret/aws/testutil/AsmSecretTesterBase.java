/*
 * // Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 */

package ai.vespa.secret.aws.testutil;

import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.SecretVersionState;
import com.yahoo.vespa.athenz.api.AwsRole;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.InternalServiceErrorException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidNextTokenException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidParameterException;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretVersionsListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Base class for ASM reader and writer testers.
 * Expected mapping from Key to AWS secret id must be provided by each test.
 * This mapping is used when manually writing/reading secrets to/from the 'secrets' map,
 * so that lookup will fail if the mapping does not match what the production code uses.
 *
 * @author gjoranv
 */
public class AsmSecretTesterBase {

    public record SecretVersion(String version, SecretVersionState state, String value) {}

    protected final Map<String, List<SecretVersion>> secrets = new HashMap<>();
    protected final List<MockSecretsManagerClient> clients = new ArrayList<>();
    protected final Function<Key, String> awsSecretIdMapper;

    public AsmSecretTesterBase(Function<Key, String> awsSecretId) {
        this.awsSecretIdMapper = awsSecretId;
    }

    public void reset() {
        secrets.clear();
        clients.clear();
    }

    public List<MockSecretsManagerClient> clients() {
        return List.copyOf(clients);
    }


    public abstract class MockSecretsManagerClient implements SecretsManagerClient {

        public final AwsRole awsRole;
        public boolean isClosed = false;

        protected MockSecretsManagerClient(AwsRole awsRole) {
            this.awsRole = awsRole;
            clients.add(this);
        }

        // Used by both reader and writer testers
        @Override
        public ListSecretVersionIdsResponse listSecretVersionIds(ListSecretVersionIdsRequest request) throws InvalidNextTokenException, ResourceNotFoundException, InternalServiceErrorException, InvalidParameterException, AwsServiceException, SdkClientException, SecretsManagerException {
            return ListSecretVersionIdsResponse.builder()
                    .name(request.secretId())
                    .versions(secrets.getOrDefault(request.secretId(), List.of()).stream()
                                      .map(version -> SecretVersionsListEntry.builder()
                                              .versionId(version.version())
                                              .versionStages(List.of(toAwsStage(version.state())))
                                              .build())
                                      .toList())
                    .build();
        }

        @Override
        public void close() {
            isClosed = true;
        }

        protected String toAwsStage(SecretVersionState state) {
            return switch (state) {
                case CURRENT -> "AWSCURRENT";
                case PENDING -> "AWSPENDING";
                case PREVIOUS -> "AWSPREVIOUS";
                default -> throw new IllegalArgumentException("Unknown state: " + state);
            };
        }

    }

}
