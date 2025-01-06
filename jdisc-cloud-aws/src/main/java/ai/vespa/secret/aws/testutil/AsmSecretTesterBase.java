/*
 * // Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 */

package ai.vespa.secret.aws.testutil;

import ai.vespa.secret.aws.AwsRolePath;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.SecretVersionState;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretVersionsListEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

        public final AwsRolePath awsRole;
        public boolean isClosed = false;

        protected MockSecretsManagerClient(AwsRolePath awsRole) {
            this.awsRole = awsRole;
            clients.add(this);
        }

        @Override
        public ListSecretsResponse listSecrets(Consumer<ListSecretsRequest.Builder> listSecretsRequest) {
            return ListSecretsResponse.builder()
                    .secretList(secrets.keySet().stream()
                                        .map(name -> SecretListEntry.builder().name(name).build())
                                        .toList())
                    .build();
        }

        // Used by both reader and writer testers
        @Override
        public ListSecretVersionIdsResponse listSecretVersionIds(ListSecretVersionIdsRequest request) {
            if (! secrets.containsKey(request.secretId())) {
                // Emulate AWS behavior
                throw ResourceNotFoundException.builder().message("Secret not found: " + request.secretId()).build();
            }
            return ListSecretVersionIdsResponse.builder()
                    .name(request.secretId())
                    .versions(secrets.get(request.secretId()).stream()
                                      .map(version -> SecretVersionsListEntry.builder()
                                              .versionId(version.version())
                                              .versionStages(toAwsStages(version.state()))
                                              .build())
                                      .toList())
                    .build();
        }

        @Override
        public void close() {
            isClosed = true;
        }

        protected List<String> toAwsStages(SecretVersionState state) {
            return switch (state) {
                // We don't remove the AWSPENDING label when setting AWSCURRENT
                case CURRENT -> List.of("AWSPENDING", "AWSCURRENT");
                case PENDING -> List.of("AWSPENDING");
                case PREVIOUS, DEPRECATED -> List.of();
            };
        }

    }

}
