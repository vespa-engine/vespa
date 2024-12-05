/*
 * // Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 */

package ai.vespa.secret.aws.testutil;

import ai.vespa.secret.aws.AssumedRoleInfo;
import ai.vespa.secret.aws.AwsRolePath;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.SecretVersionState;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.List;
import java.util.function.Function;

/**
 *
 * @author gjoranv
 */
public class AsmSecretReaderTester extends AsmSecretTesterBase {

    public AsmSecretReaderTester(Function<Key, String> awsSecretId) {
        super(awsSecretId);
    }

    /** Ensures that tests fail if the mapping from Key to AWS secret id is not as expected. */
    public void put(Key key, SecretVersion... versions) {
        secrets.put(awsSecretIdMapper.apply(key), List.of(versions));
    }

    public MockSecretsReader newClient(AssumedRoleInfo assumedRoleInfo) {
        return new MockSecretsReader(assumedRoleInfo.role());
    }


    public class MockSecretsReader extends MockSecretsManagerClient {

        MockSecretsReader(AwsRolePath awsRole) {
            super(awsRole);
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
                    .versionId(secret.version())
                    .versionStages(toAwsStages(secret.state()))
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
        public String serviceName() {
            return MockSecretsReader.class.getSimpleName();
        }

    }

}
