/*
 * // Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 */

package ai.vespa.secret.aws.testutil;

import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.SecretVersionState;
import com.yahoo.vespa.athenz.api.AwsRole;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
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
