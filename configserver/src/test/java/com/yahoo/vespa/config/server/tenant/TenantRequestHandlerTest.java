// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.VespaVersion;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.ReloadListener;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.rpc.UncompressedConfigResponseFactory;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.deploy.ZooKeeperDeployer;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.SessionZooKeeperClient;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author Ulf Lilleengen
 */
public class TenantRequestHandlerTest {

    private static final Version vespaVersion = new VespaModelFactory(new NullConfigModelRegistry()).getVersion();
    private TenantRequestHandler server;
    private MockReloadListener listener = new MockReloadListener();
    private File app1 = new File("src/test/apps/cs1");
    private File app2 = new File("src/test/apps/cs2");
    private TenantName tenant = TenantName.from("mytenant");
    private TestComponentRegistry componentRegistry;
    private Curator curator;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ApplicationId defaultApp() {
        return new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(tenant).build();
    }

    @Before
    public void setUp() throws IOException {
        curator = new MockCurator();

        feedApp(app1, 1, defaultApp(), false);
        Metrics sh = Metrics.createTestMetrics();
        List<ReloadListener> listeners = new ArrayList<>();
        listeners.add(listener);
        server = new TenantRequestHandler(sh, tenant, listeners, new UncompressedConfigResponseFactory(), new HostRegistries());
        componentRegistry = new TestComponentRegistry.Builder().curator(curator).modelFactoryRegistry(createRegistry()).build();
    }

    private void feedApp(File appDir, long sessionId, ApplicationId appId, boolean  internalRedeploy) throws IOException {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, TenantRepository.getSessionsPath(tenant).append(String.valueOf(sessionId)));
        zkc.writeApplicationId(appId);
        File app = tempFolder.newFolder();
        IOUtils.copyDirectory(appDir, app);
        ZooKeeperDeployer deployer = zkc.createDeployer(new BaseDeployLogger());
        DeployData deployData = new DeployData("user",
                                               appDir.toString(),
                                               appId.application().toString(),
                                               0L,
                                               internalRedeploy,
                                               0L,
                                               0L);
        ApplicationPackage appPackage = FilesApplicationPackage.fromFileWithDeployData(appDir, deployData);
        deployer.deploy(appPackage,
                        Collections.singletonMap(vespaVersion, new MockFileRegistry()),
                        AllocatedHosts.withHosts(Collections.emptySet()));
    }

    private ApplicationSet reloadConfig(long sessionId, Clock clock) {
        return reloadConfig(sessionId, "default", clock);
    }

    private ApplicationSet reloadConfig(long sessionId, String application, Clock clock) {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, TenantRepository.getSessionsPath(tenant).append(String.valueOf(sessionId)));
        zkc.writeApplicationId(new ApplicationId.Builder().tenant(tenant).applicationName(application).build());
        RemoteSession session = new RemoteSession(tenant, sessionId, componentRegistry, zkc, clock);
        return session.ensureApplicationLoaded();
    }

    private ModelFactoryRegistry createRegistry() {
        return new ModelFactoryRegistry(Arrays.asList(new TestModelFactory(vespaVersion),
                new TestModelFactory(Version.fromIntValues(3, 2, 1))));
    }

    public <T extends ConfigInstance> T resolve(Class<T> clazz,
                                                TenantRequestHandler tenantRequestHandler,
                                                ApplicationId appId,
                                                Version vespaVersion,
                                                String configId) {
        ConfigResponse response = getConfigResponse(clazz, tenantRequestHandler, appId, vespaVersion, configId);
        return ConfigPayload.fromUtf8Array(response.getPayload()).toInstance(clazz, configId);
    }

    public <T extends ConfigInstance> ConfigResponse getConfigResponse(Class<T> clazz,
                                                                       TenantRequestHandler tenantRequestHandler,
                                                                       ApplicationId appId,
                                                                       Version vespaVersion,
                                                                       String configId) {
        return tenantRequestHandler.resolveConfig(appId, new GetConfigRequest() {
            @Override
            public ConfigKey<T> getConfigKey() {
                return new ConfigKey<T>(clazz, configId);
            }

            @Override
            public DefContent getDefContent() {
                return DefContent.fromClass(clazz);
            }

            @Override
            public Optional<VespaVersion> getVespaVersion() {
                return Optional.of(VespaVersion.fromString(vespaVersion.toSerializedForm()));
            }

            @Override
            public boolean noCache() {
                return false;
            }
        }, Optional.empty());
    }

    @Test
    public void testReloadConfig() throws IOException {
        Clock clock = Clock.systemUTC();
        ApplicationId applicationId = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(tenant).build();

        server.reloadConfig(reloadConfig(1, clock));
        assertThat(listener.reloaded.get(), is(1));
        // Using only payload list for this simple test
    	SimpletypesConfig config = resolve(SimpletypesConfig.class, server, defaultApp(), vespaVersion, "");
        assertThat(config.intval(), is(1337));
        assertThat(server.getApplicationGeneration(applicationId, Optional.of(vespaVersion)), is(1l));

        server.reloadConfig(reloadConfig(1L, clock));
        ConfigResponse configResponse = getConfigResponse(SimpletypesConfig.class, server, defaultApp(), vespaVersion, "");
        assertFalse(configResponse.isInternalRedeploy());
        config = resolve(SimpletypesConfig.class, server, defaultApp(), vespaVersion, "");
        assertThat(config.intval(), is(1337));
        assertThat(listener.reloaded.get(), is(2));
        assertThat(server.getApplicationGeneration(applicationId, Optional.of(vespaVersion)), is(1l));
        assertThat(listener.tenantHosts.size(), is(1));
        assertThat(server.resolveApplicationId("mytesthost"), is(applicationId));

        listener.reloaded.set(0);
        feedApp(app2, 2, defaultApp(), true);
        server.reloadConfig(reloadConfig(2L, clock));
        configResponse = getConfigResponse(SimpletypesConfig.class, server, defaultApp(), vespaVersion, "");
        assertTrue(configResponse.isInternalRedeploy());
        config = resolve(SimpletypesConfig.class, server, defaultApp(), vespaVersion,"");
        assertThat(config.intval(), is(1330));
        assertThat(listener.reloaded.get(), is(1));
        assertThat(server.getApplicationGeneration(applicationId, Optional.of(vespaVersion)), is(2l));
    }

    @Test
    public void testRemoveApplication() {
        server.reloadConfig(reloadConfig(1, Clock.systemUTC()));
        assertThat(listener.removed.get(), is(0));
        server.removeApplication(new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(tenant).build());
        assertThat(listener.removed.get(), is(1));
    }

    @Test
    public void testResolveForAppId() {
        long id = 1L;
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, TenantRepository.getSessionsPath(tenant).append(String.valueOf(id)));
        ApplicationId appId = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName("myapp").instanceName("myinst").build();
        zkc.writeApplicationId(appId);
        RemoteSession session = new RemoteSession(appId.tenant(), id, componentRegistry, zkc, Clock.systemUTC());
        server.reloadConfig(session.ensureApplicationLoaded());
        SimpletypesConfig config = resolve(SimpletypesConfig.class, server, appId, vespaVersion, "");
        assertThat(config.intval(), is(1337));
    }

    @Test
    public void testResolveMultipleApps() throws IOException {
        ApplicationId appId1 = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName("myapp1").instanceName("myinst1").build();
        ApplicationId appId2 = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName("myapp2").instanceName("myinst2").build();
        feedAndReloadApp(app1, 1, appId1);
        SimpletypesConfig config = resolve(SimpletypesConfig.class, server, appId1, vespaVersion, "");
        assertThat(config.intval(), is(1337));

        feedAndReloadApp(app2, 2, appId2);
        config = resolve(SimpletypesConfig.class, server, appId2, vespaVersion, "");
        assertThat(config.intval(), is(1330));
    }

    @Test
    public void testResolveMultipleVersions() throws IOException {
        ApplicationId appId = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName("myapp1").instanceName("myinst1").build();
        feedAndReloadApp(app1, 1, appId);
        SimpletypesConfig config = resolve(SimpletypesConfig.class, server, appId, vespaVersion, "");
        assertThat(config.intval(), is(1337));
        config = resolve(SimpletypesConfig.class, server, appId, Version.fromIntValues(3, 2, 1), "");
        assertThat(config.intval(), is(1337));
    }

    private void feedAndReloadApp(File appDir, long sessionId, ApplicationId appId) throws IOException {
        feedApp(appDir, sessionId, appId, false);
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, TenantRepository.getSessionsPath(tenant).append(String.valueOf(sessionId)));
        zkc.writeApplicationId(appId);
        RemoteSession session = new RemoteSession(tenant, sessionId, componentRegistry, zkc, Clock.systemUTC());
        server.reloadConfig(session.ensureApplicationLoaded());
    }

    public static class MockReloadListener implements ReloadListener {
        public AtomicInteger reloaded = new AtomicInteger(0);
        public AtomicInteger removed = new AtomicInteger(0);
        public Map<String, Collection<String>> tenantHosts = new LinkedHashMap<>();
        @Override
        public void configActivated(TenantName tenant, ApplicationSet application) {
            reloaded.incrementAndGet();
        }

        @Override
        public void hostsUpdated(TenantName tenant, Collection<String> newHosts) {
            tenantHosts.put(tenant.value(), newHosts);
        }

        @Override
        public void verifyHostsAreAvailable(TenantName tenant, Collection<String> newHosts) {
        }

        @Override
        public void applicationRemoved(ApplicationId applicationId) {
            removed.incrementAndGet();
        }
    }

    @Test
    public void testHasApplication() throws IOException, SAXException {
        assertdefaultAppNotFound();
        server.reloadConfig(reloadConfig(1l, Clock.systemUTC()));
        assertTrue(server.hasApplication(new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(tenant).build(),
                                         Optional.of(vespaVersion)));
    }

    private void assertdefaultAppNotFound() {
        assertFalse(server.hasApplication(ApplicationId.defaultId(), Optional.of(vespaVersion)));
    }

    @Test
    public void testMultipleApplicationsReload() {
        assertdefaultAppNotFound();
        server.reloadConfig(reloadConfig(1l, "foo", Clock.systemUTC()));
        assertdefaultAppNotFound();
        assertTrue(server.hasApplication(new ApplicationId.Builder().tenant(tenant).applicationName("foo").build(),
                                         Optional.of(vespaVersion)));
        assertThat(server.resolveApplicationId("doesnotexist"), is(ApplicationId.defaultId()));
        assertThat(server.resolveApplicationId("mytesthost"), is(new ApplicationId.Builder()
                                                                 .tenant(tenant)
                                                                 .applicationName("foo").build())); // Host set in application package.
    }

    @Test
    public void testListConfigs() throws IOException, SAXException {
        assertdefaultAppNotFound();

        VespaModel model = new VespaModel(FilesApplicationPackage.fromFile(new File("src/test/apps/app")));
        server.reloadConfig(ApplicationSet.fromSingle(new Application(model,
                                                                      new ServerCache(),
                                                                      1,
                                                                      false,
                                                                      vespaVersion,
                                                                      MetricUpdater.createTestUpdater(),
                                                                      ApplicationId.defaultId())));
        Set<ConfigKey<?>> configNames = server.listConfigs(ApplicationId.defaultId(), Optional.of(vespaVersion), false);
        assertTrue(configNames.contains(new ConfigKey<>("sentinel", "hosts", "cloud.config")));

        configNames = server.listConfigs(ApplicationId.defaultId(), Optional.of(vespaVersion), true);
        System.out.println(configNames);
        assertTrue(configNames.contains(new ConfigKey<>("feeder", "jdisc", "vespaclient.config")));
        assertTrue(configNames.contains(new ConfigKey<>("documentmanager", "jdisc", "document.config")));
        assertTrue(configNames.contains(new ConfigKey<>("documentmanager", "", "document.config")));
        assertTrue(configNames.contains(new ConfigKey<>("documenttypes", "", "document")));
        assertTrue(configNames.contains(new ConfigKey<>("documentmanager", "jdisc", "document.config")));
        assertTrue(configNames.contains(new ConfigKey<>("health-monitor", "jdisc", "container.jdisc.config")));
        assertTrue(configNames.contains(new ConfigKey<>("specific", "jdisc", "project")));
    }

    @Test
    public void testAppendIdsInNonRecursiveListing() {
        assertEquals(server.appendOneLevelOfId("search/music", "search/music/qrservers/default/qr.0"), "search/music/qrservers");
        assertEquals(server.appendOneLevelOfId("search", "search/music/qrservers/default/qr.0"), "search/music");
        assertEquals(server.appendOneLevelOfId("search/music/qrservers/default/qr.0", "search/music/qrservers/default/qr.0"), "search/music/qrservers/default/qr.0");
        assertEquals(server.appendOneLevelOfId("", "search/music/qrservers/default/qr.0"), "search");
    }
}
