// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.model.VespaModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author lulf
 * @since 5.9
 */
public class SuperModelRequestHandlerTest {

    private static final File testApp = new File("src/test/resources/deploy/app");
    private SuperModelManager manager;
    private SuperModelGenerationCounter counter;
    private SuperModelRequestHandler controller;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        counter = new SuperModelGenerationCounter(new MockCurator());
        ConfigserverConfig configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder());
        manager = new SuperModelManager(configserverConfig, emptyNodeFlavors(), counter);
        controller = new SuperModelRequestHandler(new TestConfigDefinitionRepo(), configserverConfig, manager);
    }

    @Test
    public void test_super_model_reload() throws IOException, SAXException {
        TenantName tenantA = TenantName.from("a");
        assertNotNull(controller.getHandler());
        long gen = counter.increment();
        controller.reloadConfig(tenantA, createApp(tenantA, "foo", 3l, 1));
        assertNotNull(controller.getHandler());
        assertThat(controller.getHandler().getGeneration(), is(gen));
        controller.reloadConfig(tenantA, createApp(tenantA, "foo", 4l, 2));
        assertThat(controller.getHandler().getGeneration(), is(gen));
        // Test that a new app is used when there already exist an application with the same id
        ApplicationId appId = new ApplicationId.Builder().tenant(tenantA).applicationName("foo").build();
        assertThat(controller.getHandler().getSuperModel().applicationModels().get(tenantA).get(appId).getGeneration(), is(4l));
        gen = counter.increment();
        controller.reloadConfig(tenantA, createApp(tenantA, "bar", 2l, 3));
        assertThat(controller.getHandler().getGeneration(), is(gen));
    }

    @Test
    public void test_super_model_remove() throws IOException, SAXException {
        TenantName tenantA = TenantName.from("a");
        TenantName tenantB = TenantName.from("b");
        long gen = counter.increment();
        controller.reloadConfig(tenantA, createApp(tenantA, "foo", 3l, 1));
        controller.reloadConfig(tenantA, createApp(tenantA, "bar", 30l, 2));
        controller.reloadConfig(tenantB, createApp(tenantB, "baz", 9l, 3));
        assertThat(controller.getHandler().getGeneration(), is(gen));
        assertThat(controller.getHandler().getSuperModel().applicationModels().size(), is(2));
        assertThat(controller.getHandler().getSuperModel().applicationModels().get(TenantName.from("a")).size(), is(2));
        controller.removeApplication(
                new ApplicationId.Builder().tenant("a").applicationName("unknown").build());
        assertThat(controller.getHandler().getGeneration(), is(gen));
        assertThat(controller.getHandler().getSuperModel().applicationModels().size(), is(2));
        assertThat(controller.getHandler().getSuperModel().applicationModels().get(TenantName.from("a")).size(), is(2));
        gen = counter.increment();
        controller.removeApplication(
                new ApplicationId.Builder().tenant("a").applicationName("bar").build());
        assertThat(controller.getHandler().getSuperModel().applicationModels().size(), is(2));
        assertThat(controller.getHandler().getSuperModel().applicationModels().get(TenantName.from("a")).size(), is(1));
        assertThat(controller.getHandler().getGeneration(), is(gen));
    }

    @Test
    public void test_super_model_master_generation() throws IOException, SAXException {
        TenantName tenantA = TenantName.from("a");
        long masterGen = 10;
        ConfigserverConfig configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder().masterGeneration(masterGen));
        manager = new SuperModelManager(configserverConfig, emptyNodeFlavors(), counter);
        controller = new SuperModelRequestHandler(new TestConfigDefinitionRepo(), configserverConfig, manager);

        long gen = counter.increment();
        controller.reloadConfig(tenantA, createApp(tenantA, "foo", 3L, 1));
        assertThat(controller.getHandler().getGeneration(), is(masterGen + gen));
    }

    @Test
    public void test_super_model_has_application_when_enabled() {
        assertFalse(controller.hasApplication(ApplicationId.global(), Optional.empty()));
        controller.enable();
        assertTrue(controller.hasApplication(ApplicationId.global(), Optional.empty()));
    }

    private ApplicationSet createApp(TenantName tenant, String application, long generation, long version) throws IOException, SAXException {
        return ApplicationSet.fromSingle(
                new TestApplication(
                        new VespaModel(FilesApplicationPackage.fromFile(testApp)),
                        new ServerCache(),
                        generation,
                        new ApplicationId.Builder().tenant(tenant).applicationName(application).build(),
                        version));
    }

    private static class TestApplication extends Application {

        public TestApplication(VespaModel vespaModel, ServerCache cache, long appGeneration, ApplicationId app, long version) {
            super(vespaModel, cache, appGeneration, false, Version.fromIntValues(1, 2, 3), MetricUpdater.createTestUpdater(), app);
        }

    }

    public static NodeFlavors emptyNodeFlavors() {
        return new NodeFlavors(new FlavorsConfig(new FlavorsConfig.Builder()));
    }

}
