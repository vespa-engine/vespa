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
 * File distribution config producer, delegates getting config to {@link DummyFileDistributionConfigProducer} (one per host)
 */
public class FileDistributionConfigProducer extends AbstractConfigProducer {

    private final Map<Host, AbstractConfigProducer> fileDistributionConfigProducers = new IdentityHashMap<>();
    private final FileDistributor fileDistributor;

    private FileDistributionConfigProducer(AbstractConfigProducer parent, FileDistributor fileDistributor) {
        super(parent, "filedistribution");
        this.fileDistributor = fileDistributor;
    }

    public FileDistributor getFileDistributor() {
        return fileDistributor;
    }

    public void addFileDistributionConfigProducer(Host host, AbstractConfigProducer fileDistributionConfigProducer) {
        fileDistributionConfigProducers.put(host, fileDistributionConfigProducer);
    }

    public static class Builder {

        public FileDistributionConfigProducer build(AbstractConfigProducer ancestor, FileRegistry fileRegistry, List<ConfigServerSpec> configServerSpec) {
            FileDistributor fileDistributor = new FileDistributor(fileRegistry, configServerSpec);
            return new FileDistributionConfigProducer(ancestor, fileDistributor);
        }
    }

    public AbstractConfigProducer getConfigProducer(Host host) {
        return fileDistributionConfigProducers.get(host);
    }

}
