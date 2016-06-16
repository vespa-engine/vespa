// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.ReadOnlyStatusRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Utility methods for working with service monitor model entity objects.
 *
 * @author <a href="mailto:bakksjo@yahoo-inc.com">Oyvind Bakksjo</a>
 */
public class OrchestratorUtil {
    // Utility class, not to be instantiated.
    private OrchestratorUtil() {}

    public static Set<HostName> getHostsUsedByApplicationInstance(final ApplicationInstance<?> applicationInstance) {
        return applicationInstance.serviceClusters().stream()
                .flatMap(serviceCluster -> getHostsUsedByServiceCluster(serviceCluster).stream())
                .collect(toSet());
    }

    public static Set<HostName> getHostsUsedByServiceCluster(final ServiceCluster<?> serviceCluster) {
        return serviceCluster.serviceInstances().stream()
                .map(ServiceInstance::hostName)
                .collect(toSet());
    }

    public static <T> Set<ServiceCluster<T>> getServiceClustersUsingHost(
            final Collection<ServiceCluster<T>> serviceClusters,
            final HostName hostName) {
        return serviceClusters.stream()
                .filter(serviceCluster -> hasServiceInstanceOnHost(serviceCluster, hostName))
                .collect(toSet());
    }

    public static Map<HostName, HostStatus> getHostStatusMap(
            final Collection<HostName> hosts,
            final ReadOnlyStatusRegistry hostStatusService) {
        return hosts.stream()
                .collect(Collectors.toMap(
                        hostName -> hostName,
                        hostName -> hostStatusService.getHostStatus(hostName)));
    }

    private static boolean hasServiceInstanceOnHost(
            final ServiceCluster<?> serviceCluster,
            final HostName hostName) {
        return serviceInstancesOnHost(serviceCluster, hostName).count() > 0;
    }

    public static <T> Stream<ServiceInstance<T>> serviceInstancesOnHost(
            final ServiceCluster<T> serviceCluster,
            final HostName hostName) {
        return serviceCluster.serviceInstances().stream()
                .filter(instance -> instance.hostName().equals(hostName));
    }

    public static <K, V1, V2> Map<K, V2> mapValues(
            final Map<K, V1> map,
            final Function<V1, V2> valueConverter) {
        return map.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, entry -> valueConverter.apply(entry.getValue())));
    }

    private static final Pattern APPLICATION_INSTANCE_REFERENCE_REST_FORMAT_PATTERN = Pattern.compile("^([^:]+):(.+)$");

    /** Returns an ApplicationInstanceReference constructed from the serialized format used in the REST API. */
    public static ApplicationInstanceReference parseAppInstanceReference(final String restFormat) {
        if (restFormat == null) {
            throw new IllegalArgumentException("Could not construct instance id from null string");
        }

        final Matcher matcher = APPLICATION_INSTANCE_REFERENCE_REST_FORMAT_PATTERN.matcher(restFormat);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Could not construct instance id from string \"" + restFormat +"\"");
        }

        final TenantId tenantId = new TenantId(matcher.group(1));
        final ApplicationInstanceId applicationInstanceId = new ApplicationInstanceId(matcher.group(2));
        return new ApplicationInstanceReference(tenantId, applicationInstanceId);
    }

    public static String toRestApiFormat(final ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstanceReference.tenantId() + ":" + applicationInstanceReference.applicationInstanceId();
    }


    public static ApplicationInstanceReference toApplicationInstanceReference(ApplicationId appId,
                                                                              InstanceLookupService instanceLookupService)
            throws ApplicationIdNotFoundException {

        String appRegex = String.format("^%s:.*:.*:%s$",appId.application().toString(), appId.instance().toString());
        Set<ApplicationInstanceReference> appRefs = instanceLookupService.knownInstances();
        Optional<ApplicationInstanceReference> appRef = appRefs.stream()
                .filter(a -> a.tenantId().equals(appId.tenant()))
                .filter(a -> a.applicationInstanceId().toString().matches(appRegex))
                .findFirst();
        return appRef.orElseThrow(() -> new ApplicationIdNotFoundException());
    }

    public static ApplicationId toApplicationId(ApplicationInstanceReference appRef) {
        TenantName tenantName = TenantName.from(appRef.tenantId().toString());

        // Now for the application/instance pair we need to split this
        String appNameStr = appRef.applicationInstanceId().toString();
        String[] appNameParts = appNameStr.split(":");

        // We assume a valid application reference has at lest two parts appname:instancename
        if (appNameParts.length < 2)  {
            throw new IllegalArgumentException("Application reference not valid: " + appRef);
        }

        // Last part of string is the instance name
        InstanceName instanceName = InstanceName.from(appNameParts[appNameParts.length-1]);

        // The rest is application
        int whereAppNameEnds = appNameStr.lastIndexOf(":");
        ApplicationName appName = ApplicationName.from(appNameStr.substring(0, whereAppNameEnds));

        return ApplicationId.from(tenantName, appName, instanceName);
    }
}
