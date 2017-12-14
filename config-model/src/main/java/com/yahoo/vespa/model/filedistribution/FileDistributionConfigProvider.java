// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.cloud.config.filedistribution.FiledistributorConfig;
import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.cloud.config.filedistribution.FilereferencesConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.admin.FileDistributionOptions;

import java.util.Collection;

public class FileDistributionConfigProvider {

    private final FileDistributor fileDistributor;
    private final FileDistributionOptions fileDistributionOptions;
    private final boolean sendAllFiles;
    private final Host host;

    public FileDistributionConfigProvider(FileDistributor fileDistributor,
                                          FileDistributionOptions fileDistributionOptions,
                                          boolean sendAllFiles,
                                          Host host) {
        this.fileDistributor = fileDistributor;
        this.fileDistributionOptions = fileDistributionOptions;
        this.sendAllFiles = sendAllFiles;
        this.host = host;
    }

    public void getConfig(FiledistributorConfig.Builder builder) {
        fileDistributionOptions.getConfig(builder);
        builder.torrentport(FileDistributorService.BASEPORT + 1);
        builder.stateport(FileDistributorService.BASEPORT + 2);
        builder.hostname(host.getHostname());
        builder.filedbpath(FileDistribution.getDefaultFileDBPath().toString());
    }

    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        // If disabled config proxy should act as file distributor, so use config proxy port
        int port = (fileDistributionOptions.disableFiledistributor()) ? ConfigProxy.BASEPORT : FileDistributorService.BASEPORT;
        builder.connectionspec("tcp/" + host.getHostname() + ":" + port);
    }

    public void getConfig(FilereferencesConfig.Builder builder) {
        for (FileReference reference : getFileReferences()) {
            builder.filereferences(reference.value());
        }
    }

    private Collection<FileReference> getFileReferences() {
        if (sendAllFiles) {
            return fileDistributor.allFilesToSend();
        } else {
            return fileDistributor.filesToSendToHost(host);
        }
    }
}
