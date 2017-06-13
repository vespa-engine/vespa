// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.admin.FileDistributionOptions;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * @author tonytv
 */
public class FileDistributionConfigProducer extends AbstractConfigProducer {

    private final Map<Host, FileDistributorService> fileDistributorServices = new IdentityHashMap<>();
    private final FileDistributor fileDistributor;
    private final FileDistributionOptions options;

    private FileDistributionConfigProducer(AbstractConfigProducer parent, FileDistributor fileDistributor, FileDistributionOptions options) {
        super(parent, "filedistribution");
        this.fileDistributor = fileDistributor;
        this.options = options;
    }

    public FileDistributorService getFileDistributorService(Host host) {
        FileDistributorService service = fileDistributorServices.get(host);
        if (service == null) {
            throw new IllegalStateException("No file distribution service for host " + host);
        }
        return service;
    }

    public FileDistributor getFileDistributor() {
        return fileDistributor;
    }

    public FileDistributionOptions getOptions() {
        return options;
    }

    public void addFileDistributionService(Host host, FileDistributorService fds) {
        fileDistributorServices.put(host, fds);
    }

    public static class Builder {

        private final FileDistributionOptions options;

        public Builder(FileDistributionOptions fileDistributionOptions) {
            this.options = fileDistributionOptions;
        }

        public FileDistributionConfigProducer build(AbstractConfigProducer ancestor, FileRegistry fileRegistry) {
            FileDistributor fileDistributor = new FileDistributor(fileRegistry);
            return new FileDistributionConfigProducer(ancestor, fileDistributor, options);
        }
    }

}
