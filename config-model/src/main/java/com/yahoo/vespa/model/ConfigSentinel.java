// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;

/**
 * There is one config-sentinel running on each Vespa host, and one
 * instance of this class is therefore created by each instance of
 * class {@link Host}.
 *
 * @author gjoranv
 */
public class ConfigSentinel extends AbstractService implements SentinelConfig.Producer {

    private final ApplicationId applicationId;
    private final Zone zone;

    /**
     * Constructs a new ConfigSentinel for the given host.
     *
     * @param host Physical host on which to run.
     */
    public ConfigSentinel(Host host, ApplicationId applicationId, Zone zone) {
        super(host, "sentinel");
        this.applicationId = applicationId;
        this.zone = zone;
        portsMeta.on(0).tag("rpc").tag("admin");
        portsMeta.on(1).tag("telnet").tag("interactive").tag("http").tag("state");
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
    }

    /**
     * Returns the desired base port for this service.
     */
    public int getWantedPort() { return 19097; }

    /**
     * The desired base port is the only allowed base port.
     */
    public boolean requiresWantedPort() { return true; }

    /**
     * @return The number of ports reserved by the Sentinel.
     */
    public int getPortCount() { return 2; }

    @Override
    public String[] getPortSuffixes() {
        String[] suffixes = new String[2];
        suffixes[0] = "rpc";
        suffixes[1] = "http";
        return suffixes;
    }

    @Override
    public int getHealthPort() {return getRelativePort(1); }

    /**
     * Overrides parent method as this is named config-sentinel and not configsentinel all over Vespa
     * @return service type for config-sentinel
     */
    public String getServiceType(){
        return "config-sentinel";
    }

    @Override
    public void getConfig(SentinelConfig.Builder builder) {
        builder.application(getApplicationConfig());
        for (Service s : getHostResource().getServices()) {
            if (s.getStartupCommand() != null) {
                builder.service(getServiceConfig(s));
            }
        }
    }

    private SentinelConfig.Application.Builder getApplicationConfig() {
        SentinelConfig.Application.Builder builder = new SentinelConfig.Application.Builder();
        builder.tenant(applicationId.tenant().value());
        builder.name(applicationId.application().value());
        builder.environment(zone.environment().value());
        builder.region(zone.region().value());
        builder.instance(applicationId.instance().value());
        return builder;
    }

    private SentinelConfig.Service.Builder getServiceConfig(Service s) {
        SentinelConfig.Service.Builder serviceBuilder = new SentinelConfig.Service.Builder();
        serviceBuilder.command(s.getStartupCommand());
        serviceBuilder.name(s.getServiceName());
        serviceBuilder.autostart(s.getAutostartFlag());
        serviceBuilder.autorestart(s.getAutorestartFlag());
        serviceBuilder.id(s.getConfigId());
        serviceBuilder.affinity(getServiceAffinity(s));
        setPreShutdownCommand(serviceBuilder, s);
        return serviceBuilder;
    }

    private void setPreShutdownCommand(SentinelConfig.Service.Builder serviceBuilder, Service service) {
        if (service.getPreShutdownCommand().isPresent()) {
            serviceBuilder.preShutdownCommand(service.getPreShutdownCommand().get());
        }
    }


    private SentinelConfig.Service.Affinity.Builder getServiceAffinity(Service s) {
        SentinelConfig.Service.Affinity.Builder builder = new SentinelConfig.Service.Affinity.Builder();
        if (s.getAffinity().isPresent()) {
            builder.cpuSocket(s.getAffinity().get().cpuSocket());
        }
        return builder;
    }
}
