// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A hardcoded set of applications with one storage cluster with two nodes each.
 *
 * @author oyving
 * @author smorgrav
 */
public class DummyInstanceLookupService implements InstanceLookupService {

    public static final HostName TEST1_HOST_NAME = new HostName("test1.hostname.tld");
    public static final HostName TEST3_HOST_NAME = new HostName("test3.hostname.tld");
    public static final HostName TEST6_HOST_NAME = new HostName("test6.hostname.tld");

    private static final Set<ApplicationInstance> apps = new HashSet<>();

    static {
        apps.add(new ApplicationInstance(
                new TenantId("test-tenant-id"),
                new ApplicationInstanceId("application:prod:utopia-1:instance"),
                TestUtil.makeServiceClusterSet(
                        new ServiceCluster(
                                new ClusterId("test-cluster-id-1"),
                                new ServiceType("storagenode"),
                                TestUtil.makeServiceInstanceSet(
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
                                TestUtil.makeServiceInstanceSet(
                                        new ServiceInstance(
                                                new ConfigId("clustercontroller-1"),
                                                new HostName("myclustercontroller.hostname.tld"),
                                                ServiceStatus.UP)))

                )
        ));

        apps.add(new ApplicationInstance(
                new TenantId("mediasearch"),
                new ApplicationInstanceId("imagesearch:prod:utopia-1:default"),
                TestUtil.makeServiceClusterSet(
                        new ServiceCluster(
                                new ClusterId("image"),
                                new ServiceType("storagenode"),
                                TestUtil.makeServiceInstanceSet(
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
                                TestUtil.makeServiceInstanceSet(
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
                TestUtil.makeServiceClusterSet(
                        new ServiceCluster(
                                new ClusterId("cluster-id-3"),
                                new ServiceType("storagenode"),
                                TestUtil.makeServiceInstanceSet(
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
                                TestUtil.makeServiceInstanceSet(
                                        new ServiceInstance(
                                                new ConfigId("clustercontroller-1"),
                                                new HostName("myclustercontroller3.hostname.tld"),
                                                ServiceStatus.UP)))
                )
        ));
    }

    // A node group is tied to an application, so we need to define them after we have populated the above applications.
    public final static NodeGroup TEST1_NODE_GROUP = new NodeGroup(new DummyInstanceLookupService().findInstanceByHost(TEST1_HOST_NAME).get(), TEST1_HOST_NAME);
    public final static NodeGroup TEST3_NODE_GROUP = new NodeGroup(new DummyInstanceLookupService().findInstanceByHost(TEST3_HOST_NAME).get(), TEST3_HOST_NAME);
    public final static NodeGroup TEST6_NODE_GROUP = new NodeGroup(new DummyInstanceLookupService().findInstanceByHost(TEST6_HOST_NAME).get(), TEST6_HOST_NAME);


    @Override
    public Optional<ApplicationInstance> findInstanceById(
            final ApplicationInstanceReference applicationInstanceReference) {
        for (ApplicationInstance app : apps) {
            if (app.reference().equals(applicationInstanceReference)) return Optional.of(app);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ApplicationInstance> findInstanceByHost(HostName hostName) {
        for (ApplicationInstance app : apps) {
            for (ServiceCluster cluster : app.serviceClusters()) {
                for (ServiceInstance service : cluster.serviceInstances()) {
                    if (hostName.equals(service.hostName())) return Optional.of(app);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<ApplicationInstanceReference> knownInstances() {
        return apps.stream().map(a ->
                new ApplicationInstanceReference(a.tenantId(),a.applicationInstanceId())).collect(Collectors.toSet());

    }

    @Override
    public Optional<ApplicationInstance> findInstancePossiblyNarrowedToHost(HostName hostname) {
        return findInstanceByHost(hostname);
    }

    public static Set<HostName> getContentHosts(ApplicationInstanceReference appRef) {
        Set<HostName> hosts = apps.stream()
                .filter(application -> application.reference().equals(appRef))
                .flatMap(appReference -> appReference.serviceClusters().stream())
                .filter(VespaModelUtil::isContent)
                .flatMap(serviceCluster -> serviceCluster.serviceInstances().stream())
                .map(ServiceInstance::hostName)
                .collect(Collectors.toSet());

        return hosts;
    }

    public static  Set<ApplicationInstance> getApplications() {
       return apps;
    }
}
