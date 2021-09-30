// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * Utility methods for working with service monitor model entity objects.
 *
 * @author bakksjo
 */
public class OrchestratorUtil {

    // Utility class, not to be instantiated.
    private OrchestratorUtil() {}

    public static Set<HostName> getHostsUsedByApplicationInstance(ApplicationInstance applicationInstance) {
        return applicationInstance.serviceClusters().stream()
                .flatMap(serviceCluster -> getHostsUsedByServiceCluster(serviceCluster).stream())
                .collect(toSet());
    }

    public static Set<HostName> getHostsUsedByServiceCluster(ServiceCluster serviceCluster) {
        return serviceCluster.serviceInstances().stream()
                .map(ServiceInstance::hostName)
                .collect(toSet());
    }

    private static final Pattern APPLICATION_INSTANCE_REFERENCE_REST_FORMAT_PATTERN = Pattern.compile("^([^:]+):(.+)$");

    /** Returns an ApplicationInstanceReference constructed from the serialized format used in the REST API. */
    public static ApplicationInstanceReference parseApplicationInstanceReference(String restFormat) {
        if (restFormat == null) {
            throw new IllegalArgumentException("Could not construct instance id from null string");
        }

        Matcher matcher = APPLICATION_INSTANCE_REFERENCE_REST_FORMAT_PATTERN.matcher(restFormat);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Could not construct instance id from string \"" + restFormat +"\"");
        }

        TenantId tenantId = new TenantId(matcher.group(1));
        ApplicationInstanceId applicationInstanceId = new ApplicationInstanceId(matcher.group(2));
        return new ApplicationInstanceReference(tenantId, applicationInstanceId);
    }

    public static String toRestApiFormat(ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstanceReference.tenantId() + ":" + applicationInstanceReference.applicationInstanceId();
    }

    public static ApplicationInstanceReference toApplicationInstanceReference(
            ApplicationId applicationid,
            ServiceMonitor serviceMonitor)
            throws ApplicationIdNotFoundException {

        Set<ApplicationInstanceReference> references = serviceMonitor.getAllApplicationInstanceReferences();
        List<ApplicationInstanceReference> referencesWithId = references.stream()
                .filter(a -> OrchestratorUtil.toApplicationId(a).equals(applicationid))
                .collect(Collectors.toList());

        if (referencesWithId.size() > 1) {
            String msg = String.format("ApplicationId '%s' was not unique but mapped to '%s'", applicationid, referencesWithId);
            throw new ApplicationIdNotFoundException(msg);
        }

        if (referencesWithId.size() == 0) {
            throw new ApplicationIdNotFoundException();
        }

        return referencesWithId.get(0);
    }

    public static ApplicationId toApplicationId(ApplicationInstanceReference appRef) {

        String appNameStr = appRef.asString();
        String[] appNameParts = appNameStr.split(":");

        // Env, region and instance seems to be optional due to the hardcoded config server app
        // Assume here that first two are tenant and application name.
        if (appNameParts.length == 2) {
            return ApplicationId.from(TenantName.from(appNameParts[0]),
                    ApplicationName.from(appNameParts[1]),
                    InstanceName.defaultName());
        }

        // Other normal application should have 5 parts.
        if (appNameParts.length != 5)  {
            throw new IllegalArgumentException("Application reference not valid (not 5 parts): " + appRef);
        }

        return ApplicationId.from(TenantName.from(appNameParts[0]),
                ApplicationName.from(appNameParts[1]),
                InstanceName.from(appNameParts[4]));
    }

}
