// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.cloud.config.filedistribution.FiledistributorConfig;
import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.cloud.config.filedistribution.FilereferencesConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;

/**
 * @author Tony Vaagenes
 *
 * Config is produced by {@link FileDistributionConfigProvider}
 */
public class FileDistributorService extends AbstractService implements
        FiledistributorConfig.Producer,
        FiledistributorrpcConfig.Producer,
        FilereferencesConfig.Producer {

    final static int BASEPORT = 19092;

    private final FileDistributionConfigProvider configProvider;

    public FileDistributorService(AbstractConfigProducer parent, String hostname, FileDistributionConfigProvider configProvider) {
        super(parent, hostname);
        this.configProvider = configProvider;
        portsMeta.on(0).tag("rpc");
        portsMeta.on(1).tag("torrent");
        portsMeta.on(2).tag("http").tag("state");
        setProp("clustertype", "filedistribution");
        setProp("clustername", "admin");
    }

    @Override
    public String getStartupCommand() {
        return "exec $ROOT/sbin/vespa-filedistributor" + " --configid " + getConfigId();
    }

    @Override
    public boolean getAutostartFlag() {
        return true;
    }

    @Override
    public boolean getAutorestartFlag() {
        return true;
    }

    @Override
    public int getPortCount() {
        return 3;
    }

    @Override
    public int getWantedPort() {
        return BASEPORT;
    }

    @Override
    public void getConfig(FiledistributorConfig.Builder builder) {
        configProvider.getConfig(builder);
    }

    @Override
    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        configProvider.getConfig(builder);
    }

    @Override
    public void getConfig(FilereferencesConfig.Builder builder) {
        configProvider.getConfig(builder);
    }
}
