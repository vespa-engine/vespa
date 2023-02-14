// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.Host;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * File distribution config producer, delegates getting config to {@link FileDistributionConfigProvider} (one per host)
 *
 * @author hmusum
 */
public class FileDistributionConfigProducer extends TreeConfigProducer<FileDistributionConfigProvider> {

    private final Map<Host, FileDistributionConfigProvider> fileDistributionConfigProviders = new IdentityHashMap<>();

    public FileDistributionConfigProducer(TreeConfigProducer<AnyConfigProducer> parent) {
        super(parent, "filedistribution");
    }

    public void addProvider(Host host, FileDistributionConfigProvider fileDistributionConfigProvider) {
        fileDistributionConfigProviders.put(host, fileDistributionConfigProvider);
    }

    public FileDistributionConfigProvider getConfigProducer(Host host) {
        return fileDistributionConfigProviders.get(host);
    }

}
