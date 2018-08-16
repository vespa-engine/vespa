// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation.InstanceValidator.SERVICE_PROPERTIES_DOMAIN_KEY;
import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation.InstanceValidator.SERVICE_PROPERTIES_SERVICE_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author valerijf
 * @author bjorncs
 */
public class InstanceValidatorTest {

    private final ApplicationId applicationId = ApplicationId.from("tenant", "application", "instance");
    private final String domain = "domain";
    private final String service = "service";


    @Test
    public void application_does_not_exist() {
        SuperModelProvider superModelProvider = mockSuperModelProvider();
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider);

        assertFalse(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    @Test
    public void application_does_not_have_domain_set() {
        SuperModelProvider superModelProvider = mockSuperModelProvider(
                mockApplicationInfo(applicationId, 5, Collections.emptyList()));
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider);

        assertFalse(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    @Test
    public void application_has_wrong_domain() {
        ServiceInfo serviceInfo = new ServiceInfo("serviceName", "type", Collections.emptyList(),
                Collections.singletonMap(SERVICE_PROPERTIES_DOMAIN_KEY, "not-domain"), "confId", "hostName");

        SuperModelProvider superModelProvider = mockSuperModelProvider(
                mockApplicationInfo(applicationId, 5, Collections.singletonList(serviceInfo)));
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider);

        assertFalse(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    @Test
    public void application_has_same_domain_and_service() {
        Map<String, String> properties = new HashMap<>();
        properties.put(SERVICE_PROPERTIES_DOMAIN_KEY, domain);
        properties.put(SERVICE_PROPERTIES_SERVICE_KEY, service);

        ServiceInfo serviceInfo = new ServiceInfo("serviceName", "type", Collections.emptyList(),
                properties, "confId", "hostName");

        SuperModelProvider superModelProvider = mockSuperModelProvider(
                mockApplicationInfo(applicationId, 5, Collections.singletonList(serviceInfo)));
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider);

        assertTrue(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    private SuperModelProvider mockSuperModelProvider(ApplicationInfo... appInfos) {
        SuperModel superModel = new SuperModel(Stream.of(appInfos)
                .collect(Collectors.groupingBy(
                        appInfo -> appInfo.getApplicationId().tenant(),
                        Collectors.toMap(
                                ApplicationInfo::getApplicationId,
                                Function.identity()
                        )
                )));

        SuperModelProvider superModelProvider = mock(SuperModelProvider.class);
        when(superModelProvider.getSuperModel()).thenReturn(superModel);
        return superModelProvider;
    }

    private ApplicationInfo mockApplicationInfo(ApplicationId appId, int numHosts, List<ServiceInfo> serviceInfo) {
        List<HostInfo> hosts = IntStream.range(0, numHosts)
                .mapToObj(i -> new HostInfo("host-" + i + "." + appId.toShortString() + ".yahoo.com", serviceInfo))
                .collect(Collectors.toList());

        Model model = mock(Model.class);
        when(model.getHosts()).thenReturn(hosts);

        return new ApplicationInfo(appId, 0, model);
    }
}
