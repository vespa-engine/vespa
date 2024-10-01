// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.secret.config.aws.AsmSecretConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.net.URI;

/**
 * @author lesters
 */
public class CloudAsmSecrets extends SimpleComponent implements AsmSecretConfig.Producer {

    private static final String CLASS = "ai.vespa.secret.aws.AsmSecretStore";
    private static final String BUNDLE = "jdisc-cloud-aws";

    private final URI ztsUri;

    public CloudAsmSecrets(URI ztsUri) {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(CLASS, CLASS, BUNDLE)));
        this.ztsUri = ztsUri;
    }

    @Override
    public void getConfig(AsmSecretConfig.Builder builder) {
        builder.ztsUri(ztsUri.toString());
    }

}
