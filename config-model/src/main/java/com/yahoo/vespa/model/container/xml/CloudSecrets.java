// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.secret.config.SecretsConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lesters
 */
public class CloudSecrets extends SimpleComponent implements SecretsConfig.Producer {

    private static final String CLASS = "ai.vespa.secret.aws.SecretsImpl";
    private static final String BUNDLE = "jdisc-cloud-aws";

    private final List<SecretConfig> secrets = new ArrayList<>();

    private record SecretConfig(String key, String name, String vault) {}

    public CloudSecrets() {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(CLASS, CLASS, BUNDLE)));
    }

    @Override
    public void getConfig(SecretsConfig.Builder builder) {
        secrets.forEach(secret ->
            builder.secret(secret.key(), new SecretsConfig.Secret.Builder().name(secret.name()).vault(secret.vault()))
        );
    }

    public void addSecret(String key, String name, String vault) {
        secrets.add(new SecretConfig(key, name, vault));
    }

}
