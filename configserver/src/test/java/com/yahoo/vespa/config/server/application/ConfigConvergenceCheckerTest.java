// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URI;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceResponse;

/**
 * @author Ulf Lilleengen
 */
public class ConfigConvergenceCheckerTest {

    private TenantName tenant = TenantName.from("mytenant");
    private ApplicationId appId = ApplicationId.from(tenant, ApplicationName.from("myapp"), InstanceName.from("myinstance"));
    private ObjectMapper mapper = new ObjectMapper();
    private Application application;
    private ConfigConvergenceChecker checker;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() {
        Model mockModel = MockModel.createContainer("localhost", 1337);
        application = new Application(mockModel,
                                      new ServerCache(),
                                      3,
                                      false,
                                      Version.fromIntValues(0, 0, 0),
                                      MetricUpdater.createTestUpdater(), appId);
        checker = new ConfigConvergenceChecker(
                (client, serviceUri) -> () -> string2json("{\"config\":{\"generation\":3}}"));
    }

    @Test
    public void converge() throws IOException {
        HttpResponse serviceListResponse = checker.servicesToCheck(application, URI.create("http://foo:234/serviceconverge"));
        assertThat(serviceListResponse.getStatus(), is(200));
        assertEquals("{" +
                             "\"services\":[" +
                             "{" +
                             "\"host\":\"localhost\"," +
                             "\"port\":1337," +
                             "\"type\":\"container\"," +
                             "\"url\":\"http://foo:234/serviceconverge/localhost:1337\"}]," +
                             "\"url\":\"http://foo:234/serviceconverge\"," +
                             "\"wantedGeneration\":3}",
                SessionHandlerTest.getRenderedString(serviceListResponse));

        ServiceResponse response = checker.checkService(application,
                                                        "localhost:1337",
                                                        URI.create("http://foo:234/serviceconverge/localhost:1337"));
        assertThat(response.getStatus(), is(200));
        assertEquals("{" +
                             "\"url\":\"http://foo:234/serviceconverge/localhost:1337\"," +
                             "\"host\":\"localhost:1337\"," +
                             "\"wantedGeneration\":3," +
                             "\"converged\":true," +
                             "\"currentGeneration\":3}",
                             SessionHandlerTest.getRenderedString(response));

        ServiceResponse hostMissingResponse = checker.checkService(application,
                                                                   "notPresent:1337",
                                                                   URI.create("http://foo:234/serviceconverge/notPresent:1337"));
        assertThat(hostMissingResponse.getStatus(), is(410));
        assertEquals("{" +
                             "\"url\":\"http://foo:234/serviceconverge/notPresent:1337\"," +
                             "\"host\":\"notPresent:1337\"," +
                             "\"wantedGeneration\":3," +
                             "\"problem\":\"Host:port (service) no longer part of application, refetch list of services.\"}",
                     SessionHandlerTest.getRenderedString(hostMissingResponse));
    }

    private JsonNode string2json(String data) {
        try {
            return mapper.readTree(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
