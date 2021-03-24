// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.HealthClient;
import com.yahoo.vespa.serviceview.bindings.ModelResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Functional test for {@link StateResource}.
 *
 * @author Steinar Knutsen
 */
public class StateResourceTest {

    private static final String EXTERNAL_BASE_URI = "http://someserver:8080/serviceview/";

    private static class TestResource extends StateResource {
        private static final String BASE_URI = "http://vespa.yahoo.com:8080/state/v1";

        TestResource(@Component ConfigServerLocation configServer, @Context UriInfo ui) {
            super(configServer, ui);
        }

        @Override
        protected ModelResponse getModelConfig(String tenant, String application, String environment, String region, String instance) {
            return ServiceModelTest.syntheticModelResponse();
        }

        @Override
        protected HealthClient getHealthClient(String apiParams, Service s, int requestedPort, Client client) {
            HealthClient healthClient = Mockito.mock(HealthClient.class);
            HashMap<Object, Object> dummyHealthData = new HashMap<>();
            HashMap<String, String> dummyLink = new HashMap<>();
            dummyLink.put("url", BASE_URI);
            dummyHealthData.put("resources", Collections.singletonList(dummyLink));
            Mockito.when(healthClient.getHealthInfo()).thenReturn(dummyHealthData);
            return healthClient;
        }
    }

    private StateResource testResource;
    private ServiceModel correspondingModel;

    @Before
    public void setUp() throws Exception {
        UriInfo base = Mockito.mock(UriInfo.class);
        Mockito.when(base.getBaseUri()).thenReturn(new URI(EXTERNAL_BASE_URI));
        ConfigServerLocation dummyLocation = new ConfigServerLocation(new ConfigserverConfig(new ConfigserverConfig.Builder()));
        testResource = new TestResource(dummyLocation, base);
        correspondingModel = new ServiceModel(ServiceModelTest.syntheticModelResponse());
    }

    @After
    public void tearDown() {
        testResource = null;
        correspondingModel = null;
    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void test() {
        Service s = correspondingModel.resolve("vespa.yahoo.com", 8080, null);
        String api = "/state/v1";
        HashMap boom = testResource.singleService("default", "default", "default", "default", "default", s.getIdentifier(8080), api);
        assertEquals(EXTERNAL_BASE_URI + "v1/tenant/default/application/default/environment/default/region/default/instance/default/service/" + s.getIdentifier(8080) + api,
                     ((Map) ((List) boom.get("resources")).get(0)).get("url"));
    }

    @Test
    public final void testLinkEquality() {
        ApplicationView explicitParameters = testResource.getUserInfo("default", "default", "default", "default", "default");
        ApplicationView implicitParameters = testResource.getDefaultUserInfo();
        assertEquals(explicitParameters.clusters.get(0).services.get(0).url, implicitParameters.clusters.get(0).services.get(0).url);
    }

}
