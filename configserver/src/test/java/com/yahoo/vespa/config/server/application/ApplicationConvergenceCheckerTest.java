// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker.ServiceResponse;

/**
 * @author lulf
 */
public class ApplicationConvergenceCheckerTest {

    private TenantName tenant = TenantName.from("mytenant");
    private ApplicationId appId = ApplicationId.from(tenant, ApplicationName.from("myapp"), InstanceName.from("myinstance"));
    private ObjectMapper mapper = new ObjectMapper();
    private Application application;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() throws IOException, SAXException, InterruptedException {
        Model mockModel = new MockModel("localhost", 1337);
        application = new Application(mockModel, new ServerCache(), 3, Version.fromIntValues(0, 0, 0), MetricUpdater.createTestUpdater(), appId);
    }

    @Test
    public void converge() throws IOException, SAXException {
        ApplicationConvergenceChecker checker = new ApplicationConvergenceChecker(
                (client, serviceUri) -> () -> string2json("{\"config\":{\"generation\":3}}"));
        HttpResponse serviceListResponse = checker.serviceListToCheckForConfigConvergence(application,
                                                                                          URI.create("http://foo:234/serviceconverge"));
        assertThat(serviceListResponse.getStatus(), is(200));
        assertEquals("{\"services\":[" +
                             "{\"host\":\"localhost\"," +
                             "\"port\":1337," +
                             "\"type\":\"container\"," +
                             "\"url\":\"http://foo:234/serviceconverge/localhost:1337\"}]," +
                             "\"debug\":{\"wantedGeneration\":3}," +
                             "\"url\":\"http://foo:234/serviceconverge\"}",
                     SessionHandlerTest.getRenderedString(serviceListResponse));

        ServiceResponse serviceResponse = checker.serviceConvergenceCheck(application,
                                                                          "localhost:1337",
                                                                          URI.create("http://foo:234/serviceconverge/localhost:1337"));
        assertThat(serviceResponse.getStatus(), is(200));
        assertEquals("{" +
                             "\"debug\":{" +
                             "\"host\":\"localhost:1337\"," +
                             "\"wantedGeneration\":3," +
                             "\"currentGeneration\":3}," +
                             "\"url\":\"http://foo:234/serviceconverge/localhost:1337\"," +
                             "\"converged\":true}",
                     SessionHandlerTest.getRenderedString(serviceResponse));

        ServiceResponse hostMissingResponse = checker.serviceConvergenceCheck(application,
                                                                              "notPresent:1337",
                                                                              URI.create("http://foo:234/serviceconverge/notPresent:1337"));
        assertThat(hostMissingResponse.getStatus(), is(410));
        assertEquals("{\"debug\":{" +
                             "\"host\":\"notPresent:1337\"," +
                             "\"wantedGeneration\":3," +
                             "\"problem\":\"Host:port (service) no longer part of application, refetch list of services.\"}," +
                             "\"url\":\"http://foo:234/serviceconverge/notPresent:1337\"}",
                     SessionHandlerTest.getRenderedString(hostMissingResponse));
    }

    private JsonNode string2json(String data) {
        try {
            return mapper.readTree(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Model with two services, one that does not have a state port
    private static class MockModel implements Model {
        private final String hostname;
        private final int statePort;

        MockModel(String hostname, int statePort) {
            this.hostname = hostname;
            this.statePort = statePort;
        }

        @Override
        public ConfigPayload getConfig(ConfigKey<?> configKey, ConfigDefinition targetDef, ConfigPayload override) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigPayload getConfig(ConfigKey<?> configKey, InnerCNode targetDef, ConfigPayload override) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<ConfigKey<?>> allConfigsProduced() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<HostInfo> getHosts() {
            ServiceInfo container = createServiceInfo(hostname, "container", "container",
                                                      ClusterSpec.Type.container, statePort, "state");
            ServiceInfo serviceNoStatePort = createServiceInfo(hostname, "logserver", "logserver",
                                                               ClusterSpec.Type.admin, 1234, "logtp");
            return Collections.singleton(new HostInfo(hostname, Arrays.asList(container, serviceNoStatePort)));
        }

        ServiceInfo createServiceInfo(String hostname, String name, String type, ClusterSpec.Type clusterType, int port, String portTags) {
            PortInfo portInfo = new PortInfo(port, Collections.singleton(portTags));
            Map<String, String> properties = new HashMap<>();
            properties.put("clustername", "default");
            properties.put("clustertype", clusterType.name());
            return new ServiceInfo(name, type, Collections.singleton(portInfo), properties, "", hostname);
        }

        @Override
        public Set<String> allConfigIds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void distributeFiles(FileDistribution fileDistribution) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProvisionInfo> getProvisionInfo() {
            throw new UnsupportedOperationException();
        }
    }
}
