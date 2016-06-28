package com.yahoo.vespa.model.test;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class which sets up a system with multiple hosts
 * 
 * @author bratseth
 */
public class ConfigModelsTester {

    private Map<String, Collection<Host>> hosts = new HashMap<>();

    /** Adds some hosts of the 'default' flavor to this system */
    public Hosts addHosts(int count) { return addHosts("", count); }
    /** Adds some hosts to this system */
    public Hosts addHosts(String flavor, int count) { 
        String baseHostName = "foo" + ( flavor.isEmpty() ? "" : "-" + flavor );
        if (flavor.isEmpty())
            flavor = "default";
        Hosts hosts = new Hosts();
        for (int i = 0; i < count; i++)
            hosts.addHost(new com.yahoo.config.model.provision.Host(baseHostName + i), Collections.emptyList());
        this.hosts.put(flavor.isEmpty() ? "default" : flavor, hosts.getHosts());
        return hosts;
    }

    /** Creates a model which uses 0 as start index */
    public VespaModel createModel(String services, boolean failOnOutOfCapacity, String ... retiredHostNames) throws ParseException {
        return createModel(services, failOnOutOfCapacity, 0, retiredHostNames);
    }
    /**
     * Creates a model using the hosts already added to this
     *
     * @param services the services xml string
     * @param failOnOutOfCapacity whether we should get an exception when not enough hosts of the requested flavor
     *        is available or if we should just silently receive a smaller allocation
     * @return the resulting model
     * @throws ParseException if the services xml is invalid
     */
    public VespaModel createModel(String services, boolean failOnOutOfCapacity, int startIndexForClusters, String ... retiredHostNames) throws ParseException {
        final VespaModelCreatorWithMockPkg modelCreatorWithMockPkg = new VespaModelCreatorWithMockPkg(null, services, ApplicationPackageUtils.generateSearchDefinition("type1"));
        final ApplicationPackage appPkg = modelCreatorWithMockPkg.appPkg;
        DeployState deployState = new DeployState.Builder().applicationPackage(appPkg).modelHostProvisioner(new InMemoryProvisioner(hosts, failOnOutOfCapacity, startIndexForClusters, retiredHostNames)).
                properties((new DeployProperties.Builder()).hostedVespa(true).build()).build();
        return modelCreatorWithMockPkg.create(false, deployState);
    }

}
