// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.secret.config.aws.AsmSecretConfig;
import ai.vespa.secret.config.aws.AsmTenantSecretConfig;
import com.yahoo.config.model.api.TenantVault;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.net.URI;
import java.util.List;

/**
 * @author lesters
 */
public class CloudAsmSecrets extends SimpleComponent implements
        AsmSecretConfig.Producer,
        AsmTenantSecretConfig.Producer {

    static final String CLASS = "ai.vespa.secret.aws.AsmTenantSecretReader";
    private static final String BUNDLE = "jdisc-cloud-aws";

    private final URI ztsUri;
    private final AthenzDomain athenzDomain;
    private final SystemName system;
    private final TenantName tenant;
    private final List<TenantVault> tenantVaults;

    public CloudAsmSecrets(URI ztsUri, AthenzDomain athenzDomain,
                           SystemName system, TenantName tenant,
                           List<TenantVault> tenantVaults) {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(CLASS, CLASS, BUNDLE)));
        this.ztsUri = ztsUri;
        this.athenzDomain =  athenzDomain;
        this.system = system;
        this.tenant = tenant;
        this.tenantVaults = tenantVaults;
    }

    @Override
    public void getConfig(AsmSecretConfig.Builder builder) {
        builder.ztsUri(ztsUri.toString())
                .athenzDomain(athenzDomain.value())
                .refreshInterval(1); // 1 minute
    }

    @Override
    public void getConfig(AsmTenantSecretConfig.Builder builder) {
        builder.system(system.value())
                .tenant(tenant.value());

        tenantVaults.forEach(vault -> {
            builder.vaults(vaultBuilder -> {
                vaultBuilder.id(vault.id())
                        .name(vault.name())
                        .externalId(vault.externalId());
            });
        });
    }

}
