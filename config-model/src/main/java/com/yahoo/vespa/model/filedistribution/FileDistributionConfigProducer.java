// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.admin.FileDistributionOptions;

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
    private final FileDistributionOptions options;

    private FileDistributionConfigProducer(AbstractConfigProducer parent, FileDistributor fileDistributor, FileDistributionOptions options) {
        super(parent, "filedistribution");
        this.fileDistributor = fileDistributor;
        this.options = options;
    }

    public FileDistributor getFileDistributor() {
        return fileDistributor;
    }

    public FileDistributionOptions getOptions() {
        return options;
    }

    public void addFileDistributionConfigProducer(Host host, AbstractConfigProducer fileDistributionConfigProducer) {
        fileDistributionConfigProducers.put(host, fileDistributionConfigProducer);
    }

    public static class Builder {

        private final FileDistributionOptions options;

        public Builder(FileDistributionOptions fileDistributionOptions) {
            this.options = fileDistributionOptions;
        }

        public FileDistributionConfigProducer build(AbstractConfigProducer ancestor, FileRegistry fileRegistry, List<ConfigServerSpec> configServerSpec) {
            FileDistributor fileDistributor = new FileDistributor(fileRegistry, configServerSpec);
            return new FileDistributionConfigProducer(ancestor, fileDistributor, options);
        }
    }

    public AbstractConfigProducer getConfigProducer(Host host) {
        return fileDistributionConfigProducers.get(host);
    }

}
