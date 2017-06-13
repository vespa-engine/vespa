// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author bratseth
 */
public class ServiceMonitorStub implements ServiceMonitor {

    private final Map<ApplicationId, MockDeployer.ApplicationContext> apps;
    private final NodeRepository nodeRepository;

    private Set<String> downHosts = new HashSet<>();
    private boolean statusIsKnown = true;

    /** Create a service monitor where all nodes are initially up */
    @Inject
    @SuppressWarnings("unused")
    public ServiceMonitorStub(NodeRepository nodeRepository) {
        this(Collections.emptyMap(), nodeRepository);
    }

    /** Create a service monitor where all nodes are initially up */
    public ServiceMonitorStub(Map<ApplicationId, MockDeployer.ApplicationContext> apps, NodeRepository nodeRepository) {
        this.apps = apps;
        this.nodeRepository = nodeRepository;
    }

    public void setHostDown(String hostname) {
        downHosts.add(hostname);
    }

    public void setHostUp(String hostname) {
        downHosts.remove(hostname);
    }

    public void setStatusIsKnown(boolean statusIsKnown) {
        this.statusIsKnown = statusIsKnown;
    }

    private ServiceMonitorStatus getHostStatus(String hostname) {
        if (!statusIsKnown) return ServiceMonitorStatus.NOT_CHECKED;
        if (downHosts.contains(hostname)) return ServiceMonitorStatus.DOWN;
        return ServiceMonitorStatus.UP;
    }

    @Override
    public Map<ApplicationInstanceReference, ApplicationInstance<ServiceMonitorStatus>> queryStatusOfAllApplicationInstances() {
        // Convert apps information to the response payload to return
        Map<ApplicationInstanceReference, ApplicationInstance<ServiceMonitorStatus>> status = new HashMap<>();
        for (Map.Entry<ApplicationId, MockDeployer.ApplicationContext> app : apps.entrySet()) {
            Set<ServiceInstance<ServiceMonitorStatus>> serviceInstances = new HashSet<>();
            for (Node node : nodeRepository.getNodes(app.getValue().id(), Node.State.active)) {
                serviceInstances.add(new ServiceInstance<>(new ConfigId("configid"),
                                                           new HostName(node.hostname()),
                                                           getHostStatus(node.hostname())));
            }
            Set<ServiceCluster<ServiceMonitorStatus>> serviceClusters = new HashSet<>();
            serviceClusters.add(new ServiceCluster<>(new ClusterId(app.getValue().cluster().id().value()),
                                                     new ServiceType("serviceType"),
                                                     serviceInstances));
            TenantId tenantId = new TenantId(app.getKey().tenant().value());
            ApplicationInstanceId applicationInstanceId = new ApplicationInstanceId(app.getKey().application().value());
            status.put(new ApplicationInstanceReference(tenantId, applicationInstanceId),
                       new ApplicationInstance<>(tenantId, applicationInstanceId, serviceClusters));
        }
        return status;
    }

}
