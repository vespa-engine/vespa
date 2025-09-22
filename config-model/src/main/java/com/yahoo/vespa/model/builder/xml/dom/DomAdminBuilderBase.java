// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.container.logging.LevelsModSpec;
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
import com.yahoo.vespa.model.admin.monitoring.builder.PredefinedMetricSets;
import com.yahoo.vespa.model.admin.monitoring.builder.xml.MetricsBuilder;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A base class for admin model builders, to support common functionality across versions.
 *
 * @author Ulf Lilleengen
 * @author Vegard Havdal
 */
public abstract class DomAdminBuilderBase extends VespaDomBuilder.DomConfigProducerBuilderBase<Admin> {

    private final ApplicationType applicationType;
    protected final List<ConfigServerSpec> configServerSpecs;
    protected final boolean multitenant;

    DomAdminBuilderBase(ApplicationType applicationType,
                        boolean multitenant,
                        List<ConfigServerSpec> configServerSpecs) {
        this.applicationType = applicationType;
        this.multitenant = multitenant;
        this.configServerSpecs = configServerSpecs;
    }

    List<Configserver> getConfigServersFromSpec(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent) {
        List<Configserver> configservers = new ArrayList<>();
        for (ConfigServerSpec spec : configServerSpecs) {
            HostSystem hostSystem = parent.hostSystem();
            HostResource host = new HostResource(Host.createConfigServerHost(hostSystem, spec.getHostName()));
            hostSystem.addBoundHost(host);
            Configserver configserver = new Configserver(parent, spec.getHostName(), spec.getConfigServerPort());
            configserver.setHostResource(host);
            configserver.setBasePort(configserver.getWantedPort());
            configserver.initService(deployState);
            configservers.add(configserver);
        }
        return configservers;
    }

    @Override
    protected Admin doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element adminElement) {
        Monitoring monitoring = getMonitoring(XML.getChild(adminElement,"monitoring"), deployState.isHosted());
        Metrics metrics = new MetricsBuilder(applicationType, PredefinedMetricSets.get())
                                  .buildMetrics(XML.getChild(adminElement, "metrics"));
        Admin admin = new Admin(parent, monitoring, metrics, multitenant, deployState.isHosted(), applicationType, deployState.featureFlags());
        doBuildAdmin(deployState, admin, adminElement);
        new ModelConfigProvider(admin);

        return admin;
    }

    protected abstract void doBuildAdmin(DeployState deployState, Admin admin, Element adminE);

    private Monitoring getMonitoring(Element monitoringElement, boolean isHosted) {
        if (monitoringElement == null) return new DefaultMonitoring();

        if (isHosted && applicationType.equals(ApplicationType.DEFAULT))
            throw new IllegalArgumentException("The 'monitoring' element cannot be used on hosted Vespa.");

        Optional<Integer> minutes = getMonitoringInterval(monitoringElement);
        return new DefaultMonitoring(monitoringElement.getAttribute("systemname"), minutes);
    }

    private Optional<Integer> getMonitoringInterval(Element monitoringE) {
        String seconds = monitoringE.getAttribute("interval").trim();
        if ( ! seconds.isEmpty()) {
            int m = Integer.parseInt(seconds) / 60;
            if ( ! List.of(1, 5).contains(m)) {
                throw new IllegalArgumentException("The only allowed values for 'interval' attribute in '" +
                                                   monitoringE.getTagName() + "' element is 60 or 300.");
            }
            return Optional.of(m);
        }

        return Optional.empty();
    }

    void addLogForwarders(ModelElement logForwardingElement, Admin admin, DeployState deployState) {
        if (logForwardingElement == null) return;
        boolean alsoForAdminCluster = logForwardingElement.booleanAttribute("include-admin");
        for (ModelElement e : logForwardingElement.children("splunk")) {
            LogForwarder.Config cfg = LogForwarder.cfg()
		    .withSplunkHome(e.stringAttribute("splunk-home"))
		    .withDeploymentServer(e.stringAttribute("deployment-server"))
		    .withClientName(e.stringAttribute("client-name"))
            .withPhoneHomeInterval(e.integerAttribute("phone-home-interval"))
            .withRole(parseLogforwarderRole(e.stringAttribute("role"), deployState));
            admin.setLogForwarderConfig(cfg, alsoForAdminCluster);
        }
    }

    private void addLoggingSpec(ModelElement loggingSpec, Admin admin) {
        if (loggingSpec == null) return;
        String componentSpec = loggingSpec.requiredStringAttribute("name");
        String levels = loggingSpec.requiredStringAttribute("levels");
        var levelSpec = new LevelsModSpec();
        levelSpec.setLevels(levels);
        admin.addLogctlCommand(componentSpec, levelSpec);
    }

    void addLoggingSpecs(ModelElement loggingElement, Admin admin) {
        if (loggingElement == null) return;
        for (ModelElement e : loggingElement.children("class")) {
            addLoggingSpec(e, admin);
        }
        for (ModelElement e : loggingElement.children("package")) {
            addLoggingSpec(e, admin);
        }
    }

    private String parseLogforwarderRole(String role, DeployState deployState) {
        if (role == null)
            return null;
        if (deployState.zone().system().isPublicCloudLike())
            throw new IllegalArgumentException("Logforwarder role not supported in public systems");

        // Currently only support athenz roles on format athenz://<domain>/role/<role>
        var rolePattern = Pattern.compile("(?<scheme>athenz)://" +
                "(?<domain>[a-zA-Z0-9_][a-zA-Z0-9_.-]*[a-zA-Z0-9_])" +
                "/role/" +
                "(?<role>[a-zA-Z0-9_][a-zA-Z0-9_.-]*[a-zA-Z0-9_])");
        var matcher = rolePattern.matcher(role);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid role path " + role);
        }
        return matcher.group("domain") + ":role." + matcher.group("role");
    }

}
