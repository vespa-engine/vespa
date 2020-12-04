// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SuperModelRequestHandlerTest {

    private static final File testApp = new File("src/test/resources/deploy/app");
    private SuperModelManager manager;
    private SuperModelGenerationCounter counter;
    private SuperModelRequestHandler controller;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() {
        counter = new SuperModelGenerationCounter(new MockCurator());
        ConfigserverConfig configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder());
        manager = new SuperModelManager(configserverConfig, Zone.defaultZone(), counter, new InMemoryFlagSource());
        controller = new SuperModelRequestHandler(new TestConfigDefinitionRepo(), configserverConfig, manager);
    }

    @Test
    public void test_super_model_reload() throws IOException, SAXException {
        ApplicationId foo = applicationId("a", "foo");
        ApplicationId bar = applicationId("a", "foo");

        assertNotNull(controller.getHandler());
        long gen = counter.get();
        controller.reloadConfig(createApp(foo, 3L));
        assertNotNull(controller.getHandler());
        assertThat(controller.getHandler().getGeneration(), is(gen + 1));
        controller.reloadConfig(createApp(foo, 4L));
        assertThat(controller.getHandler().getGeneration(), is(gen + 2));
        // Test that a new app is used when there already exist an application with the same id
        assertThat(controller.getHandler().getSuperModel().applicationModels().get(foo).getGeneration(), is(4L));
        controller.reloadConfig(createApp(bar, 2L));
        assertThat(controller.getHandler().getGeneration(), is(gen + 3));
    }

    @Test
    public void test_super_model_remove() throws IOException, SAXException {
        ApplicationId foo = applicationId("a", "foo");
        ApplicationId bar = applicationId("a", "bar");
        ApplicationId baz = applicationId("b", "baz");

        long gen = counter.get();
        controller.reloadConfig(createApp(foo, 3L));
        controller.reloadConfig(createApp(bar, 30L));
        controller.reloadConfig(createApp(baz, 9L));
        assertThat(controller.getHandler().getGeneration(), is(gen + 3));
        assertThat(controller.getHandler().getSuperModel().applicationModels().size(), is(3));
        assertEquals(Arrays.asList(foo, bar, baz), new ArrayList<>(controller.getHandler().getSuperModel().applicationModels().keySet()));
        controller.removeApplication(new ApplicationId.Builder().tenant("a").applicationName("unknown").build());
        assertThat(controller.getHandler().getGeneration(), is(gen + 4));
        assertThat(controller.getHandler().getSuperModel().applicationModels().size(), is(3));
        assertEquals(Arrays.asList(foo, bar, baz), new ArrayList<>(controller.getHandler().getSuperModel().applicationModels().keySet()));
        controller.removeApplication(bar);
        assertThat(controller.getHandler().getSuperModel().applicationModels().size(), is(2));
        assertEquals(Arrays.asList(foo, baz), new ArrayList<>(controller.getHandler().getSuperModel().applicationModels().keySet()));
        assertThat(controller.getHandler().getGeneration(), is(gen + 5));
    }

    @Test
    public void test_super_model_master_generation() throws IOException, SAXException {
        ApplicationId foo = applicationId("a", "foo");
        long masterGen = 10;
        ConfigserverConfig configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder().masterGeneration(masterGen));
        manager = new SuperModelManager(configserverConfig, Zone.defaultZone(), counter, new InMemoryFlagSource());
        controller = new SuperModelRequestHandler(new TestConfigDefinitionRepo(), configserverConfig, manager);

        long gen = counter.get();
        controller.reloadConfig(createApp(foo, 3L));
        assertThat(controller.getHandler().getGeneration(), is(masterGen + gen + 1));
    }

    @Test
    public void test_super_model_has_application_when_enabled() {
        assertFalse(controller.hasApplication(ApplicationId.global(), Optional.empty()));
        controller.enable();
        assertTrue(controller.hasApplication(ApplicationId.global(), Optional.empty()));
    }

    private ApplicationSet createApp(ApplicationId applicationId, long generation) throws IOException, SAXException {
        return ApplicationSet.from(
                new TestApplication(
                        new VespaModel(FilesApplicationPackage.fromFile(testApp)),
                        new ServerCache(),
                        generation,
                        applicationId));
    }

    private static class TestApplication extends Application {

        TestApplication(VespaModel vespaModel, ServerCache cache, long appGeneration, ApplicationId app) {
            super(vespaModel, cache, appGeneration, new Version(1, 2, 3), MetricUpdater.createTestUpdater(), app);
        }

    }

    private ApplicationId applicationId(String tenantName, String applicationName) {
        return ApplicationId.from(tenantName, applicationName, "default");
    }

}
