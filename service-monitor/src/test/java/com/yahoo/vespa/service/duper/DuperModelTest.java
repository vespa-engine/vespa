// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.service.monitor.DuperModelListener;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class DuperModelTest {
    private final DuperModel duperModel = new DuperModel();

    private final ApplicationId id1 = ApplicationId.fromSerializedForm("tenant:app1:default");
    private final ApplicationInfo application1 = mock(ApplicationInfo.class);
    private final HostName hostname1_1 = HostName.from("hostname1-1");
    private final HostName hostname1_2 = HostName.from("hostname1-2");

    private final ApplicationId id2 = ApplicationId.fromSerializedForm("tenant:app2:default");
    private final ApplicationInfo application2 = mock(ApplicationInfo.class);
    private final HostName hostname2_1 = HostName.from("hostname2-1");

    private final DuperModelListener listener1 = mock(DuperModelListener.class);

    @Before
    public void setUp() {
        setUpApplication(id1, application1, hostname1_1, hostname1_2);
        setUpApplication(id2, application2, hostname2_1);
    }

    private void setUpApplication(ApplicationId id, ApplicationInfo info, HostName... hostnames) {
        when(info.getApplicationId()).thenReturn(id);

        Model model = mock(Model.class);
        when(info.getModel()).thenReturn(model);

        List<HostInfo> hostInfos = Arrays.stream(hostnames)
                .map(hostname -> new HostInfo(hostname.value(), List.of()))
                .collect(Collectors.toList());
        when(model.getHosts()).thenReturn(hostInfos);
    }

    @Test
    public void test() {
        assertEquals(0, duperModel.numberOfApplications());

        duperModel.add(application1);
        assertTrue(duperModel.contains(id1));
        assertEquals(Arrays.asList(application1), duperModel.getApplicationInfos());
        assertEquals(1, duperModel.numberOfApplications());

        duperModel.registerListener(listener1);
        verify(listener1, times(1)).applicationActivated(application1);
        verifyNoMoreInteractions(listener1);

        duperModel.remove(id2);
        assertEquals(1, duperModel.numberOfApplications());
        verifyNoMoreInteractions(listener1);

        duperModel.add(application2);
        assertEquals(2, duperModel.numberOfApplications());
        verify(listener1, times(1)).applicationActivated(application2);
        verifyNoMoreInteractions(listener1);

        duperModel.remove(id1);
        assertFalse(duperModel.contains(id1));
        verify(listener1, times(1)).applicationRemoved(id1);
        verifyNoMoreInteractions(listener1);
        assertEquals(Arrays.asList(application2), duperModel.getApplicationInfos());

        duperModel.remove(id1);
        verifyNoMoreInteractions(listener1);
    }

    @Test
    public void hostIndices() {
        assertEquals(0, duperModel.numberOfHosts());

        duperModel.add(application1);
        assertEquals(2, duperModel.numberOfHosts());
        assertEquals(Optional.of(application1), duperModel.getApplicationInfo(hostname1_1));
        assertEquals(Optional.empty(), duperModel.getApplicationInfo(hostname2_1));

        duperModel.add(application2);
        assertEquals(3, duperModel.numberOfHosts());
        assertEquals(Optional.of(application1), duperModel.getApplicationInfo(hostname1_1));
        assertEquals(Optional.of(application2), duperModel.getApplicationInfo(hostname2_1));

        duperModel.remove(application1.getApplicationId());
        assertEquals(1, duperModel.numberOfHosts());
        assertEquals(Optional.empty(), duperModel.getApplicationInfo(hostname1_1));
        assertEquals(Optional.of(application2), duperModel.getApplicationInfo(hostname2_1));

        // Remove hostname2_1 and add hostname1_1 added to id2
        setUpApplication(id2, application2, hostname1_1);
        duperModel.add(application2);
        assertEquals(1, duperModel.numberOfHosts());
        assertEquals(Optional.of(application2), duperModel.getApplicationInfo(hostname1_1));
        assertEquals(Optional.empty(), duperModel.getApplicationInfo(hostname2_1));
    }
}