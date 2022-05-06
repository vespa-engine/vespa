// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.container.jdisc.secretstore.SecretStoreConfig;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
public class AwsParameterStore extends AbstractComponent implements SecretStore {

    private final VespaAwsCredentialsProvider credentialsProvider;
    private final List<AwsSettings> configuredStores;

    @Inject
    public AwsParameterStore(SecretStoreConfig secretStoreConfig) {
        this(translateConfig(secretStoreConfig));
    }

    public AwsParameterStore(List<AwsSettings> configuredStores) {
        this.configuredStores = configuredStores;
        this.credentialsProvider = new VespaAwsCredentialsProvider();
    }

    @Override
    public String getSecret(String key) {
        for (var store : configuredStores) {
            AWSSecurityTokenService tokenService = AWSSecurityTokenServiceClientBuilder
                    .standard()
                    .withRegion(Regions.DEFAULT_REGION)
                    .withCredentials(credentialsProvider)
                    .build();

            STSAssumeRoleSessionCredentialsProvider assumeExtAccountRole = new STSAssumeRoleSessionCredentialsProvider
                    .Builder(toRoleArn(store.getAwsId(), store.getRole()), "vespa")
                    .withExternalId(store.getExternalId())
                    .withStsClient(tokenService)
                    .build();

            AWSSimpleSystemsManagement client = AWSSimpleSystemsManagementClient.builder()
                    .withCredentials(assumeExtAccountRole)
                    .withRegion(store.getRegion())
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

    private static List<AwsSettings> translateConfig(SecretStoreConfig secretStoreConfig) {
        return secretStoreConfig.awsParameterStores()
                .stream()
                .map(config -> new AwsSettings(config.name(), config.role(), config.awsId(), config.externalId(), config.region()))
                .collect(Collectors.toList());
    }

    public static class AwsSettings {
        String name;
        String role;
        String awsId;
        String externalId;
        String region;

        AwsSettings(String name, String role, String awsId, String externalId, String region) {
            this.name = validate(name, "name");
            this.role = validate(role, "role");
            this.awsId = validate(awsId, "awsId");
            this.externalId = validate(externalId, "externalId");
            this.region = validate(region, "region");
        }


        public String getName() {
            return name;
        }

        public String getRole() {
            return role;
        }

        public String getAwsId() {
            return awsId;
        }

        public String getExternalId() {
            return externalId;
        }

        public String getRegion() {
            return region;
        }

        static AwsSettings fromSlime(Slime slime) {
            var json = slime.get();
            return new AwsSettings(
                    json.field("name").asString(),
                    json.field("role").asString(),
                    json.field("awsId").asString(),
                    json.field("externalId").asString(),
                    json.field("region").asString()
            );
        }

        void toSlime(Cursor slime) {
            slime.setString("name", name);
            slime.setString("role", role);
            slime.setString("awsId", awsId);
            slime.setString("externalId", "*****");
            slime.setString("region", region);
        }

        static String validate(String value, String name) {
            if (value == null || value.isBlank())
                throw new IllegalArgumentException("Config parameter '" + name + "' was blank or empty");
            return value;
        }
    }
}
