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
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
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
 *
 * @author gjoranv
 */
public class AsmSecretStoreTester {

    public record SecretVersion(String version, SecretVersionState state, String value) {}

    private final Map<String, List<SecretVersion>> secrets = new HashMap<>();
    private final List<MockSecretsManagerClient> clients = new ArrayList<>();
    private final Function<Key, String> awsSecretIdMapper;

    public AsmSecretStoreTester(Function<Key, String> awsSecretId) {
        this.awsSecretIdMapper = awsSecretId;
    }

    public void reset() {
        secrets.clear();
        clients.clear();
    }

    public void put(Key key, SecretVersion... versions) {
        secrets.put(awsSecretIdMapper.apply(key), List.of(versions));
    }

    public MockSecretsManagerClient newClient(AwsRole awsRole) {
        return new MockSecretsManagerClient(awsRole);
    }

    public List<MockSecretsManagerClient> clients() {
        return List.copyOf(clients);
    }


    public class MockSecretsManagerClient implements SecretsManagerClient {

        public final AwsRole awsRole;
        public boolean isClosed = false;

        MockSecretsManagerClient(AwsRole awsRole) {
            this.awsRole = awsRole;
            clients.add(this);
        }

        @Override
        public GetSecretValueResponse getSecretValue(GetSecretValueRequest request) {
            String id = request.secretId();
            String reqVersion = request.versionId();

            var versions = secrets.get(id);
            if (versions == null) {
                throw ResourceNotFoundException.builder().message("Secret not found").build();
            }
            var secret = findSecret(versions, reqVersion);
            return GetSecretValueResponse.builder()
                    .name(request.secretId())
                    .secretString(secret.value())
                    .versionId(secret.version)
                    .versionStages(List.of(toAwsStage(secret.state)))
                    .build();
        }

        SecretVersion findSecret(List<SecretVersion> versions, String reqVersion) {
            return versions.stream()
                    .filter(reqVersion == null ?
                                    v -> v.state() == SecretVersionState.CURRENT
                                    : v -> v.version().equals(reqVersion))
                    .findFirst()
                    .orElseThrow(() -> ResourceNotFoundException.builder().message("Version not found: " + reqVersion).build());
        }

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
        public String serviceName() {
            return AsmSecretStoreTester.class.getSimpleName();
        }

        @Override
        public void close() {
            isClosed = true;
        }

        private String toAwsStage(SecretVersionState state) {
            return switch (state) {
                case CURRENT -> "AWSCURRENT";
                case PENDING -> "AWSPENDING";
                case PREVIOUS -> "AWSPREVIOUS";
                default -> throw new IllegalArgumentException("Unknown state: " + state);
            };
        }

    }

}
