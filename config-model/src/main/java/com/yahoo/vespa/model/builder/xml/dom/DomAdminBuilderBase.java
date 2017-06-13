// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.*;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Yamas;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import com.yahoo.vespa.model.admin.monitoring.builder.xml.MetricsBuilder;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.config.application.api.FileRegistry;
import org.w3c.dom.Element;

import java.util.*;

import static com.yahoo.vespa.model.admin.monitoring.builder.PredefinedMetricSets.predefinedMetricSets;

/**
 * A base class for admin model builders, to support common functionality across versions.
 *
 * @author lulf
 * @author vegardh
 * @since 5.12
 */
public abstract class DomAdminBuilderBase extends VespaDomBuilder.DomConfigProducerBuilder<Admin> {

    private static final int DEFAULT_INTERVAL = 1; // in minutes
    private static final String DEFAULT_CLUSTER_NAME = "vespa";

    private final ApplicationType applicationType;
    private final List<ConfigServerSpec> configServerSpecs;
    private final FileRegistry fileRegistry;
    protected final boolean multitenant;

    public DomAdminBuilderBase(ApplicationType applicationType, FileRegistry fileRegistry, boolean multitenant, List<ConfigServerSpec> configServerSpecs) {
        this.applicationType = applicationType;
        this.fileRegistry = fileRegistry;
        this.multitenant = multitenant;
        this.configServerSpecs = configServerSpecs;
    }

    protected List<Configserver> getConfigServersFromSpec(AbstractConfigProducer parent) {
        List<Configserver> configservers = new ArrayList<>();
        for (ConfigServerSpec spec : configServerSpecs) {
            HostSystem hostSystem = parent.getHostSystem();
            HostResource host = new HostResource(Host.createConfigServerHost(hostSystem, spec.getHostName()));
            hostSystem.addBoundHost(host);
            Configserver configserver = new Configserver(parent, spec.getHostName());
            configserver.setHostResource(host);
            configserver.setBasePort(configserver.getWantedPort());
            configserver.initService();
            configservers.add(configserver);
        }
        return configservers;
    }

    @Override
    protected Admin doBuild(AbstractConfigProducer parent, Element adminElement) {
        Yamas yamas = getYamas(XML.getChild(adminElement, "yamas"));

        Metrics metrics = new MetricsBuilder(applicationType, predefinedMetricSets)
                .buildMetrics(XML.getChild(adminElement, "metrics"));
        Map<String, MetricsConsumer> legacyMetricsConsumers = DomMetricBuilderHelper
                .buildMetricsConsumers(XML.getChild(adminElement, "metric-consumers"));

        Admin admin = new Admin(parent, yamas, metrics, legacyMetricsConsumers, multitenant);

        doBuildAdmin(admin, adminElement);

        new ModelConfigProvider(admin);

        FileDistributionOptions fileDistributionOptions = new DomFileDistributionOptionsBuilder().build(XML.getChild(adminElement, "filedistribution"));
        admin.setFileDistribution(new FileDistributionConfigProducer.Builder(fileDistributionOptions).build(parent, fileRegistry));
        return admin;
    }

    protected abstract void doBuildAdmin(Admin admin, Element adminE);

    private Yamas getYamas(Element yamasE) {
        Yamas yamas;
        if (yamasE == null) {
            yamas = new Yamas(DEFAULT_CLUSTER_NAME, DEFAULT_INTERVAL);
        } else {
            Integer minutes = getMonitoringInterval(yamasE);
            if (minutes == null) {
                minutes = DEFAULT_INTERVAL;
            }
            yamas = new Yamas(yamasE.getAttribute("systemname"), minutes);
        }
        return yamas;
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

}
