// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;


import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;
import com.yahoo.vespa.service.monitor.AntiServiceMonitor;
import com.yahoo.vespa.service.monitor.CriticalRegion;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A hardcoded set of applications with one storage cluster with two nodes each.
 *
 * @author oyving
 * @author smorgrav
 */
public class DummyServiceMonitor implements ServiceMonitor, AntiServiceMonitor {

    public static final HostName TEST1_HOST_NAME = new HostName("test1.hostname.tld");
    public static final HostName TEST3_HOST_NAME = new HostName("test3.hostname.tld");
    public static final HostName TEST6_HOST_NAME = new HostName("test6.hostname.tld");

    private static final List<ApplicationInstance> apps = new ArrayList<>();

    static {
        apps.add(new ApplicationInstance(
                new TenantId("test-tenant-id"),
                new ApplicationInstanceId("application:prod:utopia-1:instance"),
                Set.of(
                        new ServiceCluster(
                                new ClusterId("test-cluster-id-1"),
                                new ServiceType("storagenode"),
                                Set.of(
                                        new ServiceInstance(
                                                new ConfigId("storage/storage/1"),
                                                TEST1_HOST_NAME,
                                                ServiceStatus.UP),
                                        new ServiceInstance(
                                                new ConfigId("storage/storage/2"),
                                                new HostName("test2.hostname.tld"),
                                                ServiceStatus.UP))),
                        new ServiceCluster(
                                new ClusterId("clustercontroller"),
                                new ServiceType("container-clustercontroller"),
                                Set.of(
                                        new ServiceInstance(
                                                new ConfigId("clustercontroller-1"),
                                                new HostName("myclustercontroller.hostname.tld"),
                                                ServiceStatus.UP)))

                )
        ));

        apps.add(new ApplicationInstance(
                new TenantId("mediasearch"),
                new ApplicationInstanceId("imagesearch:prod:utopia-1:default"),
                Set.of(
                        new ServiceCluster(
                                new ClusterId("image"),
                                new ServiceType("storagenode"),
                                Set.of(
                                        new ServiceInstance(
                                                new ConfigId("storage/storage/3"),
                                                TEST3_HOST_NAME,
                                                ServiceStatus.UP),
                                        new ServiceInstance(
                                                new ConfigId("storage/storage/4"),
                                                new HostName("test4.hostname.tld"),
                                                ServiceStatus.UP))),
                        new ServiceCluster(
                                new ClusterId("clustercontroller"),
                                new ServiceType("container-clustercontroller"),
                                Set.of(
                                        new ServiceInstance(
                                                new ConfigId("clustercontroller-1"),
                                                new HostName("myclustercontroller2.hostname.tld"),
                                                ServiceStatus.UP)))
                                        )
                                )
        );

        apps.add(new ApplicationInstance(
                new TenantId("tenant-id-3"),
                new ApplicationInstanceId("application-instance-3:prod:utopia-1:default"),
                Set.of(
                        new ServiceCluster(
                                new ClusterId("cluster-id-3"),
                                new ServiceType("storagenode"),
                                Set.of(
                                        new ServiceInstance(
                                                new ConfigId("storage/storage/1"),
                                                TEST6_HOST_NAME,
                                                ServiceStatus.UP),
                                        new ServiceInstance(
                                                new ConfigId("storage/storage/4"),
                                                new HostName("test4.hostname.tld"),
                                                ServiceStatus.UP))),
                        new ServiceCluster(
                                new ClusterId("clustercontroller"),
                                new ServiceType("container-clustercontroller"),
                                Set.of(
                                        new ServiceInstance(
                                                new ConfigId("clustercontroller-1"),
                                                new HostName("myclustercontroller3.hostname.tld"),
                                                ServiceStatus.UP)))
                )
        ));
    }

    // A node group is tied to an application, so we need to define them after we have populated the above applications.
    public final static NodeGroup TEST1_NODE_GROUP = new NodeGroup(new DummyServiceMonitor().getApplication(TEST1_HOST_NAME).get(), TEST1_HOST_NAME);
    public final static NodeGroup TEST3_NODE_GROUP = new NodeGroup(new DummyServiceMonitor().getApplication(TEST3_HOST_NAME).get(), TEST3_HOST_NAME);
    public final static NodeGroup TEST6_NODE_GROUP = new NodeGroup(new DummyServiceMonitor().getApplication(TEST6_HOST_NAME).get(), TEST6_HOST_NAME);

    @Override
    public ServiceModel getServiceModelSnapshot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<ApplicationInstanceReference> getAllApplicationInstanceReferences() {
        return apps.stream().map(a ->
                new ApplicationInstanceReference(a.tenantId(),a.applicationInstanceId())).collect(Collectors.toSet());
    }

    @Override
    public Optional<ApplicationInstance> getApplication(HostName hostname) {
        for (ApplicationInstance app : apps) {
            for (ServiceCluster cluster : app.serviceClusters()) {
                for (ServiceInstance service : cluster.serviceInstances()) {
                    if (hostname.equals(service.hostName())) return Optional.of(app);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ApplicationInstance> getApplication(ApplicationInstanceReference reference) {
        for (ApplicationInstance app : apps) {
            if (app.reference().equals(reference)) return Optional.of(app);
        }
        return Optional.empty();
    }

    @Override
    public CriticalRegion disallowDuperModelLockAcquisition(String regionDescription) {
        return () -> {};
    }

    public static Set<HostName> getContentHosts(ApplicationInstanceReference appRef) {
        return apps.stream()
                   .filter(application -> application.reference().equals(appRef))
                   .flatMap(appReference -> appReference.serviceClusters().stream())
                   .filter(VespaModelUtil::isContent)
                   .flatMap(serviceCluster -> serviceCluster.serviceInstances().stream())
                   .map(ServiceInstance::hostName)
                   .collect(Collectors.toSet());
    }

    public static  List<ApplicationInstance> getApplications() {
       return apps;
    }
}
