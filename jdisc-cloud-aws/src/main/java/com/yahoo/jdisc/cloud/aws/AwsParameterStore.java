// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;

/**
 * @author mortent
 */
public class AwsParameterStore implements SecretStore {

    private final VespaAwsCredentialsProvider credentialsProvider;
    private final String roleToAssume;
    private final String externalId;

    AwsParameterStore(VespaAwsCredentialsProvider credentialsProvider, String roleToAssume, String externalId) {
        this.credentialsProvider = credentialsProvider;
        this.roleToAssume = roleToAssume;
        this.externalId = externalId;
    }

    @Override
    public String getSecret(String key) {
        AWSSecurityTokenService tokenService = AWSSecurityTokenServiceClientBuilder
                .standard()
                .withRegion("us-east-1")
                .withCredentials(credentialsProvider)
                .build();

        STSAssumeRoleSessionCredentialsProvider assumeExtAccountRole = new STSAssumeRoleSessionCredentialsProvider
                .Builder(roleToAssume, "vespa")
                .withExternalId(externalId)
                .withStsClient(tokenService)
                .build();

        AWSSimpleSystemsManagement client = AWSSimpleSystemsManagementClient.builder()
                .withCredentials(assumeExtAccountRole)
                .withRegion("us-east-1")
                .build();

        GetParametersRequest parametersRequest = new GetParametersRequest().withNames(key).withWithDecryption(true);
        GetParametersResult parameters = client.getParameters(parametersRequest);
        int count = parameters.getParameters().size();
        if (count < 1) {
            throw new SecretNotFoundException("Could not find secret " + key + " using role " + roleToAssume);
        } else if (count > 1) {
            throw new RuntimeException("Found too many parameters, expected 1, but found " + count);
        }
        return parameters.getParameters().get(0).getValue();
    }

    @Override
    public String getSecret(String key, int version) {
        // TODO
        return getSecret(key);
    }
}
