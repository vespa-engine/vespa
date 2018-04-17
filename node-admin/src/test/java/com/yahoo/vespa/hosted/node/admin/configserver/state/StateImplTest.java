// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.state;

import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.state.bindings.HealthResponse;
import org.junit.Test;

import java.net.ConnectException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StateImplTest {
    private final ConfigServerApi api = mock(ConfigServerApi.class);
    private final StateImpl state = new StateImpl(api);

    @Test
    public void testWhenUp() {
        HealthResponse response = new HealthResponse();
        response.status.code = "up";
        when(api.get(any(), any())).thenReturn(response);

        HealthCode code = state.getHealth();
        assertEquals(HealthCode.UP, code);
    }

    @Test
    public void connectException() {
        RuntimeException exception = new RuntimeException(new ConnectException("connection refused"));
        when(api.get(any(), any())).thenThrow(exception);

        HealthCode code = state.getHealth();
        assertEquals(HealthCode.DOWN, code);
    }
}