// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.cloud.config.filedistribution.FiledistributorConfig;
import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.cloud.config.filedistribution.FilereferencesConfig;
import com.yahoo.config.FileReference;

import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.admin.FileDistributionOptions;

import java.util.Collection;

/**
 * @author tonytv
 */
public class FileDistributorService extends AbstractService implements
        FiledistributorConfig.Producer,
        FiledistributorrpcConfig.Producer,
        FilereferencesConfig.Producer {
    private final static int BASEPORT = 19092;

    private final FileDistributor fileDistributor;
    private final FileDistributionOptions fileDistributionOptions;
    private final boolean sendAllFiles;

    private Collection<FileReference> getFileReferences() {
        if (sendAllFiles) {
            return fileDistributor.allFilesToSend();
        } else {
            return fileDistributor.filesToSendToHost(getHost());
        }
    }

    public FileDistributorService(AbstractConfigProducer parent,
                                  String name,
                                  FileDistributor fileDistributor,
                                  FileDistributionOptions fileDistributionOptions,
                                  boolean sendAllFiles) {
        super(parent, name);
        portsMeta.on(0).tag("rpc");
        portsMeta.on(1).tag("torrent");
        portsMeta.on(2).tag("http").tag("state");
        setProp("clustertype", "filedistribution");
        setProp("clustername", "admin");

        this.fileDistributor = fileDistributor;
        this.fileDistributionOptions = fileDistributionOptions;
        this.sendAllFiles = sendAllFiles;
        monitorService();
    }

    @Override
    public String getStartupCommand() {
        return "exec $ROOT/sbin/vespa-filedistributor"
                + " --configid " + getConfigId();
    }

    @Override
    public boolean getAutostartFlag() {
        return true;
    }

    @Override
    public boolean getAutorestartFlag() {
        return true;
    }

    public int getPortCount() {
        return 3;
    }

    @Override
    public int getWantedPort() {
        return BASEPORT;
    }

    @Override
    public void getConfig(FiledistributorConfig.Builder builder) {
        fileDistributionOptions.getConfig(builder);
        builder.torrentport(getRelativePort(1));
        builder.stateport(getRelativePort(2));
        builder.hostname(getHostName());
        builder.filedbpath(FileDistribution.getDefaultFileDBPath().toString());
    }

    @Override
    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        builder.connectionspec("tcp/" + getHostName() + ":" + getRelativePort(0));
    }

    @Override
    public void getConfig(FilereferencesConfig.Builder builder) {
        for (FileReference reference : getFileReferences()) {
            builder.filereferences(reference.value());
        }
    }
}
