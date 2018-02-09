// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.cloud.config.filedistribution.FilereferencesConfig;
import com.yahoo.config.FileReference;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.Host;

import java.util.Collection;

public class FileDistributionConfigProvider {

    private final FileDistributor fileDistributor;
    private final boolean sendAllFiles;
    private final Host host;

    public FileDistributionConfigProvider(FileDistributor fileDistributor,
                                          boolean sendAllFiles,
                                          Host host) {
        this.fileDistributor = fileDistributor;
        this.sendAllFiles = sendAllFiles;
        this.host = host;
    }

    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        builder.connectionspec("tcp/" + host.getHostname() + ":" + ConfigProxy.BASEPORT);
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
