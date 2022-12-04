// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.jdisc.secretstore.SecretStoreConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class CloudSecretStore extends SimpleComponent implements SecretStoreConfig.Producer {

    private static final String CLASS = "com.yahoo.jdisc.cloud.aws.AwsParameterStore";
    private static final String BUNDLE = "jdisc-cloud-aws";

    private final List<StoreConfig> configList;

    public CloudSecretStore() {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(CLASS, CLASS, BUNDLE)));
        configList = new ArrayList<>();
    }

    public void addConfig(String name, String region, String awsId, String role, String externalId) {
        configList.add(
                new StoreConfig(name, region, awsId, role, externalId)
        );
    }

    @Override
    public void getConfig(SecretStoreConfig.Builder builder) {
        builder.awsParameterStores(
                configList.stream()
                .map(config -> new SecretStoreConfig.AwsParameterStores.Builder()
                        .name(config.name)
                        .region(config.region)
                        .awsId(config.awsId)
                        .role(config.role)
                        .externalId(config.externalId)
                ).collect(Collectors.toList())
        );
    }

    class StoreConfig {
        private final String name;
        private final String region;
        private final String awsId;
        private final String role;
        private final String externalId;

        public StoreConfig(String name, String region, String awsId, String role, String externalId) {
            this.name = name;
            this.region = region;
            this.awsId = awsId;
            this.role = role;
            this.externalId = externalId;
        }

    }
}
