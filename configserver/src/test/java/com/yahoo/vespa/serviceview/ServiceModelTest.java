// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.HostService;
import com.yahoo.vespa.serviceview.bindings.ModelResponse;
import com.yahoo.vespa.serviceview.bindings.ServicePort;
import com.yahoo.vespa.serviceview.bindings.ServiceView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Functional tests for the programmatic view of cloud.config.model.
 *
 * @author Steinar Knutsen
 */
public class ServiceModelTest {

    private ServiceModel model;

    @Before
    public void setUp() {
        ModelResponse model = syntheticModelResponse();
        this.model = new ServiceModel(model);
    }

    static ModelResponse syntheticModelResponse() {
        ModelResponse model = new ModelResponse();
        HostService h = new HostService();
        h.name = "vespa.yahoo.com";
        com.yahoo.vespa.serviceview.bindings.Service service0 = new com.yahoo.vespa.serviceview.bindings.Service();
        {
            service0.clustername = "examplecluster";
            service0.clustertype = "somethingservers";
            service0.index = 1L;
            service0.type = "something";
            service0.name = "examplename";
            service0.configid = "blblb/lbl.0";
            ServicePort port = new ServicePort();
            port.number = Defaults.getDefaults().vespaWebServicePort();
            port.tags = "state http";
            service0.ports = Collections.singletonList(port);
        }
        com.yahoo.vespa.serviceview.bindings.Service service1 = new com.yahoo.vespa.serviceview.bindings.Service();
        {
            service1.clustername = "examplecluster";
            service1.clustertype = "somethingservers";
            service1.index = 2L;
            service1.type = "container-clustercontroller";
            service1.name = "clustercontroller";
            service1.configid = "clustercontroller/lbl.0";
            ServicePort port = new ServicePort();
            port.number = 4090;
            port.tags = "state http";
            service1.ports = Collections.singletonList(port);
        }
        com.yahoo.vespa.serviceview.bindings.Service service2 = new com.yahoo.vespa.serviceview.bindings.Service();
        {
            service2.clustername = "tralala";
            service2.clustertype = "admin";
            service2.index = 3L;
            service2.type = "configserver";
            service2.name = "configservername";
            service2.configid = "clustercontroller/lbl.0";
            ServicePort port = new ServicePort();
            port.number = 5000;
            port.tags = "state http";
            service2.ports = Collections.singletonList(port);
        }
        h.services = Arrays.asList(service0, service1, service2);
        model.hosts = Collections.singletonList(h);
        return model;
    }

    @After
    public void tearDown() {
        model = null;
    }

    @Test
    public final void test() {
        final String uriBase = "http://configserver:5000/";
        ApplicationView x = model.showAllClusters(uriBase, "/tenant/default/application/default");
        assertEquals(2, x.clusters.size());
        String urlTracking = null;
        for (com.yahoo.vespa.serviceview.bindings.ClusterView c : x.clusters) {
            for (ServiceView s : c.services) {
                if ("examplename".equals(s.serviceName)) {
                    assertEquals("something", s.serviceType);
                    urlTracking = s.url;
                    break;
                }
            }
        }
        assertNotNull(urlTracking);
        final String serviceIdentifier = urlTracking.substring(urlTracking.indexOf("something"),
                urlTracking.length() - "/state/v1/".length());
        Service y = model.getService(serviceIdentifier);
        assertEquals("examplename", y.name);
    }

}
