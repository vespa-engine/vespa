// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.Host;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hmusum
 * <p>
 * File distribution config producer, delegates getting config to {@link FileDistributionConfigProvider} (one per host)
 */
public class FileDistributionConfigProducer extends AbstractConfigProducer {

    private final Map<Host, FileDistributionConfigProvider> fileDistributionConfigProviders = new IdentityHashMap<>();
    private final FileDistributor fileDistributor;

    public FileDistributionConfigProducer(AbstractConfigProducer ancestor, FileRegistry fileRegistry, List<ConfigServerSpec> configServerSpec) {
        this(ancestor, new FileDistributor(fileRegistry, configServerSpec));
    }

    private FileDistributionConfigProducer(AbstractConfigProducer parent, FileDistributor fileDistributor) {
        super(parent, "filedistribution");
        this.fileDistributor = fileDistributor;
    }

    public FileDistributor getFileDistributor() {
        return fileDistributor;
    }

    public void addFileDistributionConfigProducer(Host host, FileDistributionConfigProvider fileDistributionConfigProvider) {
        fileDistributionConfigProviders.put(host, fileDistributionConfigProvider);
    }

    public FileDistributionConfigProvider getConfigProducer(Host host) {
        return fileDistributionConfigProviders.get(host);
    }

}
