// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult;
import com.google.inject.Inject;
import com.yahoo.cloud.config.SecretStoreConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;

/**
 * @author mortent
 */
public class AwsParameterStore extends AbstractComponent implements SecretStore {

    private final VespaAwsCredentialsProvider credentialsProvider;
    private final SecretStoreConfig secretStoreConfig;

    @Inject
    public AwsParameterStore(SecretStoreConfig secretStoreConfig) {
        this.secretStoreConfig = secretStoreConfig;
        this.credentialsProvider = new VespaAwsCredentialsProvider();
    }

    @Override
    public String getSecret(String key) {
        for (var group : secretStoreConfig.groups()) {
            AWSSecurityTokenService tokenService = AWSSecurityTokenServiceClientBuilder
                    .standard()
                    .withRegion(group.region())
                    .withCredentials(credentialsProvider)
                    .build();

            STSAssumeRoleSessionCredentialsProvider assumeExtAccountRole = new STSAssumeRoleSessionCredentialsProvider
                    .Builder(toRoleArn(group.awsId(), group.role()), "vespa")
                    .withExternalId(group.externalId())
                    .withStsClient(tokenService)
                    .build();

            AWSSimpleSystemsManagement client = AWSSimpleSystemsManagementClient.builder()
                    .withCredentials(assumeExtAccountRole)
                    .withRegion(group.region())
                    .build();

            GetParametersRequest parametersRequest = new GetParametersRequest().withNames(key).withWithDecryption(true);
            GetParametersResult parameters = client.getParameters(parametersRequest);
            int count = parameters.getParameters().size();
            if (count == 1) {
                return parameters.getParameters().get(0).getValue();
            } else if (count > 1) {
                throw new RuntimeException("Found too many parameters, expected 1, but found " + count);
            }
        }
        throw new SecretNotFoundException("Could not find secret " + key + " in any configured secret store");
    }

    @Override
    public String getSecret(String key, int version) {
        // TODO
        return getSecret(key);
    }

    private String toRoleArn(String awsId, String role) {
        return "arn:aws:iam::" + awsId + ":role/" + role;
    }
}
