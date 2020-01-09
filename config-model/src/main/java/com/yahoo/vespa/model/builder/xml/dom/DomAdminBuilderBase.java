// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Configserver;
import com.yahoo.vespa.model.admin.LogForwarder;
import com.yahoo.vespa.model.admin.ModelConfigProvider;
import com.yahoo.vespa.model.admin.monitoring.DefaultMonitoring;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import com.yahoo.vespa.model.admin.monitoring.builder.xml.MetricsBuilder;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.config.application.api.FileRegistry;
import org.w3c.dom.Element;


import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.model.admin.monitoring.builder.PredefinedMetricSets.predefinedMetricSets;

/**
 * A base class for admin model builders, to support common functionality across versions.
 *
 * @author Ulf Lilleengen
 * @author Vegard Havdal
 */
public abstract class DomAdminBuilderBase extends VespaDomBuilder.DomConfigProducerBuilder<Admin> {

    private static final int DEFAULT_INTERVAL = 1; // in minutes
    private static final String DEFAULT_CLUSTER_NAME = "vespa";

    private final ApplicationType applicationType;
    protected final List<ConfigServerSpec> configServerSpecs;
    private final FileRegistry fileRegistry;
    protected final boolean multitenant;

    DomAdminBuilderBase(ApplicationType applicationType, FileRegistry fileRegistry, boolean multitenant,
                        List<ConfigServerSpec> configServerSpecs) {
        this.applicationType = applicationType;
        this.fileRegistry = fileRegistry;
        this.multitenant = multitenant;
        this.configServerSpecs = configServerSpecs;
    }

    List<Configserver> getConfigServersFromSpec(DeployLogger deployLogger, AbstractConfigProducer parent) {
        List<Configserver> configservers = new ArrayList<>();
        for (ConfigServerSpec spec : configServerSpecs) {
            HostSystem hostSystem = parent.hostSystem();
            HostResource host = new HostResource(Host.createConfigServerHost(hostSystem, spec.getHostName()));
            hostSystem.addBoundHost(host);
            Configserver configserver = new Configserver(parent, spec.getHostName(), spec.getConfigServerPort());
            configserver.setHostResource(host);
            configserver.setBasePort(configserver.getWantedPort());
            configserver.initService(deployLogger);
            configservers.add(configserver);
        }
        return configservers;
    }

    @Override
    protected Admin doBuild(DeployState deployState, AbstractConfigProducer parent, Element adminElement) {
        Monitoring monitoring = getMonitoring(XML.getChild(adminElement,"monitoring"));
        Metrics metrics = new MetricsBuilder(applicationType, predefinedMetricSets)
                .buildMetrics(XML.getChild(adminElement, "metrics"));
        FileDistributionConfigProducer fileDistributionConfigProducer = getFileDistributionConfigProducer(parent);

        Admin admin = new Admin(parent, monitoring, metrics, multitenant, fileDistributionConfigProducer, deployState.isHosted());
        admin.setApplicationType(applicationType);
        doBuildAdmin(deployState, admin, adminElement);
        new ModelConfigProvider(admin);

        return admin;
    }

    private FileDistributionConfigProducer getFileDistributionConfigProducer(AbstractConfigProducer parent) {
        return new FileDistributionConfigProducer(parent, fileRegistry, configServerSpecs);
    }

    protected abstract void doBuildAdmin(DeployState deployState, Admin admin, Element adminE);

    private Monitoring getMonitoring(Element monitoringElement) {
        if (monitoringElement == null) return new DefaultMonitoring(DEFAULT_CLUSTER_NAME, DEFAULT_INTERVAL);

        Integer minutes = getMonitoringInterval(monitoringElement);
        if (minutes == null)
            minutes = DEFAULT_INTERVAL;
        return new DefaultMonitoring(monitoringElement.getAttribute("systemname"), minutes);
    }

    private Integer getMonitoringInterval(Element monitoringE) {
        Integer minutes = null;
        String seconds = monitoringE.getAttribute("interval").trim();
        if ( ! seconds.isEmpty()) {
            minutes = Integer.parseInt(seconds) / 60;
            if (!(minutes == 1 || minutes == 5)) {
                throw new IllegalArgumentException("The only allowed values for 'interval' attribute in '" + monitoringE.getTagName() +
                                                   "' element is 60 or 300.");
            }
        }
        return minutes;
    }

    void addLogForwarders(ModelElement logForwardingElement, Admin admin) {
        if (logForwardingElement == null) return;

        for (ModelElement e : logForwardingElement.children("splunk")) {
            LogForwarder.Config cfg = LogForwarder.cfg()
		    .withSplunkHome(e.stringAttribute("splunk-home"))
		    .withDeploymentServer(e.stringAttribute("deployment-server"))
		    .withClientName(e.stringAttribute("client-name"))
            .withPhoneHomeInterval(e.integerAttribute("phone-home-interval"));
            admin.setLogForwarderConfig(cfg);
        }
    }

}
