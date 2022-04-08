// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.duper.DuperModel;
import com.yahoo.vespa.service.monitor.ServiceHostListener;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class ServiceHostListenerAdapterTest {
    private final Zone zone = new Zone(SystemName.cd, Environment.dev, RegionName.from("us-east-1"));
    private final ModelGenerator generator = new ModelGenerator(zone);
    private final ServiceHostListener listener = mock(ServiceHostListener.class);
    private final ServiceHostListenerAdapter adapter = new ServiceHostListenerAdapter(listener, generator);
    private final DuperModel duperModel = new DuperModel();

    @Before
    public void setUp() {
        duperModel.registerListener(adapter);
    }

    @Test
    public void test() {
        var applicationId1 = ApplicationId.from("tnt", "app", "default");
        var applicationId2 = ApplicationId.from("tnt2", "app2", "default2");

        verifyNoMoreInteractions(listener);

        activate(applicationId1, "host1", "host2");
        verifyActivate(applicationId1, "host1", "host2");

        activate(applicationId1, "host1", "host3");
        verifyActivate(applicationId1, "host1", "host3");

        activate(applicationId1, "host1", "host3");
        verifyNoActivate();

        activate(applicationId2, "host4");
        verifyActivate(applicationId2, "host4");

        activate(applicationId1, "host1", "host3");
        verifyNoActivate();

        removeAndVerify(applicationId1, true);

        activate(applicationId1, "host1", "host5");
        verifyActivate(applicationId1, "host1", "host5");
    }

    @Test
    public void documentDuplicateHostnameStrangeness() {
        var applicationId1 = ApplicationId.from("tnt", "app", "default");
        var applicationInfo1 = makeApplicationInfo(applicationId1, "host1", "host2");
        duperModel.add(applicationInfo1);
        verifyActivate(applicationId1, "host1", "host2");

        var applicationId2 = ApplicationId.from("tnt2", "app2", "default2");
        var applicationInfo2 = makeApplicationInfo(applicationId2, "host2", "host3");
        duperModel.add(applicationInfo2);
        verifyActivate(applicationId2, "host2", "host3");

        // Duplicate hosts doesn't affect the ServiceHostListener.

        duperModel.add(applicationInfo1);
        verifyNoMoreInteractions(listener);

        duperModel.add(applicationInfo2);
        verifyNoMoreInteractions(listener);

        // But do affect host lookup in duper model.

        assertEquals(Optional.of(applicationInfo1), getDuperModelApplicationInfo("host1"));
        assertEquals(Optional.of(applicationInfo2), getDuperModelApplicationInfo("host2")); // <--
        assertEquals(Optional.of(applicationInfo2), getDuperModelApplicationInfo("host3"));
    }

    private Optional<ApplicationInfo> getDuperModelApplicationInfo(String hostname) {
        return duperModel.getApplicationInfo(com.yahoo.config.provision.HostName.of(hostname));
    }

    private void removeAndVerify(ApplicationId id, boolean listenerInvoked) {
        duperModel.remove(id);

        if (listenerInvoked) {
            ApplicationInstanceReference reference = generator.toApplicationInstanceReference(id);
            verify(listener, times(1)).onApplicationRemove(reference);
        }

        verifyNoMoreInteractions(listener);
    }

    private void verifyActivate(ApplicationId id, String... hostnames) {
        Set<HostName> hostnameSet = Stream.of(hostnames)
                .map(HostName::new)
                .collect(Collectors.toSet());

        ApplicationInstanceReference reference = generator.toApplicationInstanceReference(id);

        verify(listener, times(1)).onApplicationActivate(reference, hostnameSet);
        verifyNoMoreInteractions(listener);
    }

    private void verifyNoActivate() {
        verifyNoMoreInteractions(listener);
    }

    private void activate(ApplicationId id, String... hostnames) {
        duperModel.add(makeApplicationInfo(id, hostnames));
    }

    private ApplicationInfo makeApplicationInfo(ApplicationId applicationId, String... hostnames) {
        var applicationInfo = mock(ApplicationInfo.class);
        when(applicationInfo.getApplicationId()).thenReturn(applicationId);

        var model = mock(Model.class);
        when(applicationInfo.getModel()).thenReturn(model);

        List<HostInfo> hostnameList = Stream.of(hostnames)
                .map(hostname -> new HostInfo(hostname, List.of()))
                .collect(Collectors.toList());
        when(model.getHosts()).thenReturn(hostnameList);

        return applicationInfo;
    }
}
