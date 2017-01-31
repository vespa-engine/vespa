// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

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
        Model mockModel = new MockModel(1337);
        application = new Application(mockModel, new ServerCache(), 3, Version.fromIntValues(0, 0, 0), MetricUpdater.createTestUpdater(), appId);
    }

    @Test
    public void converge() throws IOException, SAXException {
        ApplicationConvergenceChecker checker = new ApplicationConvergenceChecker((client, serviceUri) -> () -> string2json("{\"config\":{\"generation\":3}}"));
        final HttpResponse httpResponse = checker.listConfigConvergence(application, URI.create("http://foo:234/serviceconvergence"));
        assertThat(httpResponse.getStatus(), is(200));
        assertJsonResponseEquals(httpResponse, "{\"services\":[" +
                "{\"port\":1337,\"host\":\"localhost\"," +
                "\"url\":\"http://foo:234/serviceconvergence/localhost:1337\"," +
                "\"type\":\"container\"}]," +
                "\"debug\":{\"wantedVersion\":3}," +
                "\"url\":\"http://foo:234/serviceconvergence\"}");
        final HttpResponse nodeHttpResponse = checker.nodeConvergenceCheck(application, "localhost:1337", URI.create("http://foo:234/serviceconvergence"));
        assertThat(nodeHttpResponse.getStatus(), is(200));
        assertJsonResponseEquals(nodeHttpResponse, "{" +
                "\"converged\":true," +
                "\"debug\":{\"wantedGeneration\":3," +
                "\"currentGeneration\":3," +
                "\"host\":\"localhost:1337\"}," +
                "\"url\":\"http://foo:234/serviceconvergence\"}");
        final HttpResponse hostMissingHttpResponse = checker.nodeConvergenceCheck(application, "notPresent:1337", URI.create("http://foo:234/serviceconvergence"));
        assertThat(hostMissingHttpResponse.getStatus(), is(410));
        assertJsonResponseEquals(hostMissingHttpResponse, "{\"debug\":{" +
                "\"problem\":\"Host:port (service) no longer part of application, refetch list of services.\"," +
                "\"wantedGeneration\":3," +
                "\"host\":\"notPresent:1337\"}," +
                "\"url\":\"http://foo:234/serviceconvergence\"}");
    }

    private void assertJsonResponseEquals(HttpResponse httpResponse, String expected) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpResponse.render(out);
        String response = out.toString(StandardCharsets.UTF_8.name());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonResponse = mapper.readTree(response);
        JsonNode jsonExpected = mapper.readTree(expected);
        if (jsonExpected.equals(jsonResponse)) {
            return;
        }
        fail("Not equal, response is '" + response + "' expected '"+ expected + "'");
    }

    private JsonNode string2json(String data) {
        try {
            return mapper.readTree(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class MockModel implements Model {
        private final int statePort;
        MockModel(int statePort) {
            this.statePort = statePort;
        }

        @Override
        public ConfigPayload getConfig(ConfigKey<?> configKey, ConfigDefinition targetDef, ConfigPayload override) throws IOException {
            if (configKey.equals(new ConfigKey<>(ModelConfig.class, ""))) {
                return createModelConfig();
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigPayload getConfig(ConfigKey<?> configKey, InnerCNode targetDef, ConfigPayload override) throws IOException {
            return getConfig(configKey, (ConfigDefinition)null, override);
        }

        private ConfigPayload createModelConfig() {
            ModelConfig.Builder builder = new ModelConfig.Builder();
            ModelConfig.Hosts.Builder hostBuilder = new ModelConfig.Hosts.Builder();
            hostBuilder.name("localhost");
            ModelConfig.Hosts.Services.Builder serviceBuilder = new ModelConfig.Hosts.Services.Builder();
            serviceBuilder.type("container");
            serviceBuilder.ports(new ModelConfig.Hosts.Services.Ports.Builder().number(statePort).tags("state"));
            hostBuilder.services(serviceBuilder);
            builder.hosts(hostBuilder);
            ModelConfig config = new ModelConfig(builder);
            return ConfigPayload.fromInstance(config);
        }

        @Override
        public Set<ConfigKey<?>> allConfigsProduced() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<HostInfo> getHosts() {
            throw new UnsupportedOperationException();
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
