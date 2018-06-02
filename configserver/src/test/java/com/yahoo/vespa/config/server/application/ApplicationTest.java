// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.ModelStub;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.UnknownConfigDefinitionException;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ApplicationTest {

    @Test
    public void testThatApplicationIsInitialized() throws IOException, SAXException {
        ApplicationId appId = ApplicationId.from(TenantName.defaultName(),
                                              ApplicationName.from("foobar"), InstanceName.defaultName());
        ServerCache cache = new ServerCache();
        Version vespaVersion = Version.fromIntValues(1, 2, 3);
        Application app = new Application(new ModelStub(), cache, 1337L, false, vespaVersion, MetricUpdater.createTestUpdater(), appId);
        assertThat(app.getApplicationGeneration(), is(1337l));
        assertNotNull(app.getModel());
        assertThat(app.getCache(), is(cache));
        assertThat(app.getId().application().value(), is("foobar"));
        assertThat(app.getVespaVersion(), is(vespaVersion));
        assertThat(app.toString(), is("application 'foobar', generation 1337, vespa version 1.2.3"));
    }

    private static final String[] emptySchema = new String[0];

    private Application handler;

    @Before
    public void setupHandler() throws IOException, SAXException {
        File testApp = new File("src/test/apps/app");
        ServerCache cache = createCacheAndAddContent();
        VespaModel model = new VespaModel(FilesApplicationPackage.fromFile(testApp));
        ApplicationId applicationId = new ApplicationId.Builder().tenant("foo").applicationName("foo").build();
        handler = new Application(model, cache, 1L, false, Version.fromIntValues(1, 2, 3),
                                  new MetricUpdater(Metrics.createTestMetrics(), Metrics.createDimensions(applicationId)), applicationId);
    }

    private static ServerCache createCacheAndAddContent() {
        ServerCache cache = new ServerCache();

        ConfigDefinitionKey key = new ConfigDefinitionKey(SimpletypesConfig.CONFIG_DEF_NAME, SimpletypesConfig.CONFIG_DEF_NAMESPACE);
        com.yahoo.vespa.config.buildergen.ConfigDefinition def = getDef(key, SimpletypesConfig.CONFIG_DEF_SCHEMA);
        // TODO Why do we have to use empty def md5 here?
        cache.addDef(key, def);

        ConfigDefinitionKey key2 = new ConfigDefinitionKey(SlobroksConfig.CONFIG_DEF_NAME, SlobroksConfig.CONFIG_DEF_NAMESPACE);
        com.yahoo.vespa.config.buildergen.ConfigDefinition def2 = getDef(key2, SlobroksConfig.CONFIG_DEF_SCHEMA);
        cache.addDef(key2, def2);

        ConfigDefinitionKey key3 = new ConfigDefinitionKey(LogdConfig.CONFIG_DEF_NAME, LogdConfig.CONFIG_DEF_NAMESPACE);
        com.yahoo.vespa.config.buildergen.ConfigDefinition def3 = getDef(key3, LogdConfig.CONFIG_DEF_SCHEMA);
        cache.addDef(key3, def3);

        return cache;
    }

    private static com.yahoo.vespa.config.buildergen.ConfigDefinition getDef(ConfigDefinitionKey key, String[] schema) {
        return new com.yahoo.vespa.config.buildergen.ConfigDefinition(key.getName(), schema);
    }

    @Test(expected = UnknownConfigDefinitionException.class)
    public void require_that_def_file_must_exist() {
        handler.resolveConfig(createRequest("unknown", "namespace", "a", emptySchema));
    }

    @Test
    public void require_that_known_config_defs_are_found() throws IOException, SAXException {
        handler.resolveConfig(createSimpleConfigRequest(emptySchema));
    }

    @Test
    public void require_that_build_config_can_be_resolved() throws IOException, SAXException {
        List<String> payload = handler.resolveConfig(createRequest(ModelConfig.CONFIG_DEF_NAME, ModelConfig.CONFIG_DEF_NAMESPACE, ModelConfig.CONFIG_DEF_MD5, ModelConfig.CONFIG_DEF_SCHEMA)).getLegacyPayload();
        assertTrue(payload.get(1).contains("host"));
    }

    @Test
    public void require_that_non_existent_fields_in_schema_is_skipped() throws IOException, SAXException {
        // Ask for config without schema and check that we get correct default value back
        List<String> payload = handler.resolveConfig(createSimpleConfigRequest(emptySchema)).getLegacyPayload();
        assertThat(payload.get(0), is("boolval false"));
        // Ask for config with wrong schema
        String[] schema = new String[1];
        schema[0] = "boolval bool default=true"; // changed to be true, original is false
        payload = handler.resolveConfig(createRequest(SimpletypesConfig.CONFIG_DEF_NAME, SimpletypesConfig.CONFIG_DEF_NAMESPACE, "", schema)).getLegacyPayload();
        assertThat(payload.size(), is(1));
        assertThat(payload.get(0), is("boolval true"));
    }

    @Test
    public void require_that_configs_are_cached() {
        ConfigResponse response = handler.resolveConfig(createRequest(ModelConfig.CONFIG_DEF_NAME, ModelConfig.CONFIG_DEF_NAMESPACE, ModelConfig.CONFIG_DEF_MD5, ModelConfig.CONFIG_DEF_SCHEMA));
        assertNotNull(response);
        ConfigResponse cached_response = handler.resolveConfig(createRequest(ModelConfig.CONFIG_DEF_NAME, ModelConfig.CONFIG_DEF_NAMESPACE, ModelConfig.CONFIG_DEF_MD5, ModelConfig.CONFIG_DEF_SCHEMA));
        assertNotNull(cached_response);
        assertTrue(response == cached_response);
    }

    private static GetConfigRequest createRequest(String name, String namespace, String defMd5, String[] schema, String configId) {
        Request request = JRTClientConfigRequestV3.
                createWithParams(new ConfigKey<>(name, configId, namespace, defMd5, null), DefContent.fromArray(schema),
                                 "fromHost", "", 0, 100, Trace.createDummy(), CompressionType.UNCOMPRESSED,
                                 Optional.empty()).getRequest();
        return JRTServerConfigRequestV3.createFromRequest(request);
    }

    private static GetConfigRequest createRequest(String name, String namespace, String defMd5, String[] schema) {
        return createRequest(name, namespace, defMd5, schema, "admin/model");
    }

    private static GetConfigRequest createSimpleConfigRequest(String[] schema) {
        return createRequest(SimpletypesConfig.CONFIG_DEF_NAME, SimpletypesConfig.CONFIG_DEF_NAMESPACE, SimpletypesConfig.CONFIG_DEF_MD5, schema);
    }
}
