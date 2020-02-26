// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.service.monitor.DuperModelListener;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

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
    private final ApplicationInfo application1 = mock(ApplicationInfo.class);
    private final ApplicationId id1 = ApplicationId.fromSerializedForm("tenant:app1:default");
    private final ApplicationId id2 = ApplicationId.fromSerializedForm("tenant:app2:default");
    private final ApplicationInfo application2 = mock(ApplicationInfo.class);
    private final DuperModelListener listener1 = mock(DuperModelListener.class);

    @Before
    public void setUp() {
        when(application1.getApplicationId()).thenReturn(id1);
        when(application2.getApplicationId()).thenReturn(id2);
    }

    @Test
    public void test() {
        duperModel.add(application1);
        assertTrue(duperModel.contains(id1));
        assertEquals(Arrays.asList(application1), duperModel.getApplicationInfos());

        duperModel.registerListener(listener1);
        verify(listener1, times(1)).applicationActivated(application1);
        verifyNoMoreInteractions(listener1);

        duperModel.remove(id2);
        verifyNoMoreInteractions(listener1);

        duperModel.add(application2);
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
}