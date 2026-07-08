// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.model.deploy.DeployState;
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

    static final int BASEPORT = 19097;

    private final ApplicationId applicationId;
    private final Zone zone;
    private final boolean ignoreConnectivityChecks;

    /**
     * Constructs a new ConfigSentinel for the given host.
     *
     * @param host Physical host on which to run.
     */
    public ConfigSentinel(Host host, DeployState deployState) {
        super(host, "sentinel");
        this.applicationId = deployState.getProperties().applicationId();
        this.zone = deployState.zone();
        this.ignoreConnectivityChecks = deployState.featureFlags().ignoreConnectivityChecksAtStartup();
        portsMeta.on(0).tag("rpc").tag("admin");
        portsMeta.on(1).tag("telnet").tag("interactive").tag("http").tag("state");
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = BASEPORT;
        from.requirePort(start++, "rpc");
        from.requirePort(start++, "http");
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
        builder.connectivity(b -> b.ignore(ignoreConnectivityChecks));
        for (Service s : getHostResource().getServices()) {
            s.getStartupCommand().ifPresent(command -> builder.service(getServiceConfig(s, command)));
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

    private SentinelConfig.Service.Builder getServiceConfig(Service s, String startupCommand) {
        SentinelConfig.Service.Builder serviceBuilder = new SentinelConfig.Service.Builder();
        serviceBuilder.command(startupCommand);
        serviceBuilder.name(s.getServiceName());
        serviceBuilder.id(s.getConfigId());
        serviceBuilder.affinity(getServiceAffinity(s));
        for (var entry : s.getEnvVars().entrySet()) {
            serviceBuilder.environ(b -> b.varname(entry.getKey()).varvalue(entry.getValue().toString()));
        }
        for (var entry : s.getLogctlSpecs()) {
            serviceBuilder.logctl(b -> b.componentSpec(entry.componentSpec())
                                        .levelsModSpec(entry.levelsModSpec().toLogctlModSpec()));
        }
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
