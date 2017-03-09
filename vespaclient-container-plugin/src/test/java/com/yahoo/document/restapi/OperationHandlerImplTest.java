// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.vespaclient.ClusterDef;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;


public class OperationHandlerImplTest {

    @Test(expected = IllegalArgumentException.class)
    public void missingClusterDef() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        OperationHandlerImpl.resolveClusterRoute(Optional.empty(), clusterDef);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingClusterDefSpecifiedCluster() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        OperationHandlerImpl.resolveClusterRoute(Optional.of("cluster"), clusterDef);
    }

    @Test(expected = RestApiException.class)
    public void oneClusterPresentNotMatching() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo", "configId"));
        OperationHandlerImpl.resolveClusterRoute(Optional.of("cluster"), clusterDef);
    }

    @Test()
    public void oneClusterMatching() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo", "configId"));
        assertThat(OperationHandlerImpl.resolveClusterRoute(Optional.of("foo"), clusterDef),
                is("[Storage:cluster=foo;clusterconfigid=configId]"));
    }

    @Test()
    public void oneClusterMatchingManyAvailable() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo2", "configId2"));
        clusterDef.add(new ClusterDef("foo", "configId"));
        clusterDef.add(new ClusterDef("foo3", "configId2"));
        assertThat(OperationHandlerImpl.resolveClusterRoute(Optional.of("foo"), clusterDef),
                is("[Storage:cluster=foo;clusterconfigid=configId]"));
    }

    @Test()
    public void checkErrorMessage() throws RestApiException, IOException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo2", "configId2"));
        clusterDef.add(new ClusterDef("foo", "configId"));
        clusterDef.add(new ClusterDef("foo3", "configId2"));
        try {
            OperationHandlerImpl.resolveClusterRoute(Optional.of("wrong"), clusterDef);
        } catch(RestApiException e) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            e.getResponse().render(stream);
            String errorMsg = new String( stream.toByteArray());
            assertThat(errorMsg, is("{\"errors\":[{\"description\":" +
                    "\"MISSING_CLUSTER Your vespa cluster contains the content clusters foo2 (configId2), foo (configId)," +
                    " foo3 (configId2),  not wrong. Please select a valid vespa cluster.\",\"id\":-9}]}"));
            return;
        }
        fail("Expected exception");
    }
}