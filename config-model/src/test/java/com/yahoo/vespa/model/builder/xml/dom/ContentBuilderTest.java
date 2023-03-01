// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.engines.ProtonEngine;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.vespa.model.search.StreamingSearchCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author baldersheim
 */
public class ContentBuilderTest extends DomBuilderTest {

    @Test
    void handleSingleNonSearchPersistentDummy() {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">" +
                        "    <redundancy>3</redundancy>" +
                        "    <documents>" +
                        "       <document type=\"music\" mode=\"store-only\"/>" +
                        "    </documents>" +
                        "    <engine>" +
                        "      <dummy/>" +
                        "    </engine>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>");

        ContentSearchCluster s = a.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertTrue(s.getClusters().isEmpty());

        assertTrue(a.getPersistence() instanceof com.yahoo.vespa.model.content.engines.DummyPersistence.Factory);
    }

    @Test
    void handleSingleNonSearchPersistentVds() {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">" +
                        "    <redundancy>3</redundancy>" +
                        "    <documents>" +
                        "       <document type=\"music\" mode=\"store-only\"/>" +
                        "    </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>");

        ContentSearchCluster s = a.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertTrue(s.getClusters().isEmpty());

        assertTrue(a.getPersistence() instanceof ProtonEngine.Factory);

        assertEquals(1, a.getStorageCluster().getChildren().size());
    }

    @Test
    void handleSingleNonSearchPersistentProton() {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">" +
                        "    <redundancy>3</redundancy>" +
                        "    <documents>" +
                        "       <document type=\"music\" mode=\"store-only\"/>" +
                        "    </documents>" +
                        "    <engine>" +
                        "      <proton/>" +
                        "    </engine>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>");

        ContentSearchCluster s = a.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertTrue(s.getClusters().isEmpty());

        assertTrue(a.getPersistence() instanceof ProtonEngine.Factory);

        assertEquals(1, a.getStorageCluster().getChildren().size());
    }

    @Test
    void handleSingleNonSearchNonPersistentCluster() {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">" +
                        "    <redundancy>3</redundancy>" +
                        "    <documents>" +
                        "       <document type=\"music\" mode=\"store-only\"/>" +
                        "    </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>");

        ContentSearchCluster s = a.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertTrue(s.getClusters().isEmpty());
        assertNull(s.getIndexed());

        assertNull(a.getRootGroup().getName());
        assertNull(a.getRootGroup().getIndex());
        assertTrue(a.getRootGroup().getSubgroups().isEmpty());
        assertEquals(1, a.getRootGroup().getNodes().size());
        assertEquals("node0", a.getRootGroup().getNodes().get(0).getHostName());

        assertTrue(a.getPersistence() instanceof  ProtonEngine.Factory);
        assertEquals(1, a.getStorageCluster().getChildren().size());
        assertEquals("a", a.getConfigId());
    }

    @Test
    void handleIndexedOnlyWithoutPersistence() {
        VespaModel m = new VespaModelCreatorWithMockPkg(createAppWithMusic(getHosts(), getBasicServices())).create();

        ContentCluster c = CollectionUtil.first(m.getContentClusters().values());
        ContentSearchCluster s = c.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertEquals(1, s.getClusters().size());
        assertNotNull(s.getIndexed());
        assertEquals("clu", s.getIndexed().getClusterName());
        assertEquals(7.3, s.getIndexed().getQueryTimeout(), 0.0);

        assertTrue(c.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, c.getStorageCluster().getChildren().size());
        assertEquals("clu", c.getConfigId());
        //assertEquals("content/a/0", a.getRootGroup().getNodes().get(0).getConfigId()); // This is how it should look like in an ideal world.
        assertEquals("clu/storage/0", c.getRootGroup().getNodes().get(0).getConfigId()); // Due to reuse.
        assertEquals(1, c.getRoot().hostSystem().getHosts().size());
        HostResource h = c.getRoot().hostSystem().getHost("mockhost");
        String [] expectedServices = {"configserver", "logserver", "logd", "container-clustercontroller", "metricsproxy-container", "slobrok", "configproxy", "config-sentinel", "container", "storagenode", "searchnode", "distributor", "transactionlogserver"};
        assertServices(h, expectedServices);
        assertEquals("clu/storage/0", h.getService("storagenode").getConfigId());
        assertEquals("clu/search/cluster.clu/0", h.getService("searchnode").getConfigId());
        assertEquals("clu/distributor/0", h.getService("distributor").getConfigId());
    }

    @Test
    void testMultipleSearchNodesOnSameHost() {
        String services = getServices("<node hostalias='mockhost' distribution-key='0'/>" +
                "<node hostalias='mockhost' distribution-key='1'/>");
        VespaModel m = new VespaModelCreatorWithMockPkg(createAppWithMusic(getHosts(), services)).create();
        IndexedSearchCluster sc = m.getContentClusters().get("clu").getSearch().getIndexed();
        assertEquals(2, sc.getSearchNodeCount());
    }

    @Test
    void handleStreamingOnlyWithoutPersistence() {
        final String musicClusterId = "music-cluster-id";

        ContentCluster cluster = createContent(
                "<content version='1.0' id='" + musicClusterId + "'>" +
                        "    <redundancy>3</redundancy>" +
                        "    <documents>" +
                        "       <document type='music' mode='streaming'/>" +
                        "    </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = cluster.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertEquals(1, s.getClusters().size());
        assertNull(s.getIndexed());
        SearchCluster sc = s.getClusters().get(musicClusterId + ".music");
        assertEquals(musicClusterId + ".music", sc.getClusterName());
        assertEquals(musicClusterId, ((StreamingSearchCluster) sc).getStorageRouteSpec());

        assertTrue(cluster.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, cluster.getStorageCluster().getChildren().size());

        assertEquals(musicClusterId, cluster.getConfigId());
        //assertEquals("content/a/0", a.getRootGroup().getNodes().get(0).getConfigId());
        assertEquals(musicClusterId + "/storage/0", cluster.getRootGroup().getNodes().get(0).getConfigId()); // Due to reuse.
        assertEquals(1, cluster.getRoot().hostSystem().getHosts().size());
        HostResource h = cluster.getRoot().hostSystem().getHost("mockhost");
        String [] expectedServices = {
                "logd", "configproxy", "config-sentinel", "configserver", "logserver",
                "slobrok", "storagenode", "distributor", "searchnode", "transactionlogserver",
                CLUSTERCONTROLLER_CONTAINER.serviceName, METRICS_PROXY_CONTAINER.serviceName
        };
        assertServices(h, expectedServices);

        assertEquals(musicClusterId + "/storage/0", h.getService("storagenode").getConfigId());
    }

    @Test
    void requireThatContentStreamingHandlesMultipleSchemas() {
        String musicClusterId = "music-cluster-id";

        ContentCluster cluster = createContentWithBooksToo(
                "<content version='1.0' id='" + musicClusterId + "'>" +
                        "    <redundancy>3</redundancy>" +
                        "    <documents>" +
                        "       <document type='music' mode='streaming'/>" +
                        "       <document type='book' mode='streaming'/>" +
                        "    </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = cluster.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertEquals(2, s.getClusters().size());
        assertNull(s.getIndexed());
        {
            String id = musicClusterId + ".book";
            SearchCluster sc = s.getClusters().get(id);
            assertEquals(id, sc.getClusterName());
            assertEquals(musicClusterId, ((StreamingSearchCluster) sc).getStorageRouteSpec());
        }
        {
            String id = musicClusterId + ".music";
            SearchCluster sc = s.getClusters().get(id);
            assertEquals(id, sc.getClusterName());
            assertEquals(musicClusterId, ((StreamingSearchCluster) sc).getStorageRouteSpec());
        }

        assertTrue(cluster.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, cluster.getStorageCluster().getChildren().size());

        assertEquals(musicClusterId, cluster.getConfigId());
    }

    @Test
    void handleIndexedWithoutPersistence() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>3</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertEquals(1, s.getClusters().size());
        assertNotNull(s.getIndexed());
        assertEquals("b", s.getIndexed().getClusterName());

        assertTrue(b.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, b.getStorageCluster().getChildren().size());

        assertEquals("b", b.getConfigId());
        //assertEquals("content/a/0", a.getRootGroup().getNodes().get(0).getConfigId());
        assertEquals("b/storage/0", b.getRootGroup().getNodes().get(0).getConfigId()); // Due to reuse.
        assertEquals(1, b.getRoot().hostSystem().getHosts().size());
        HostResource h = b.getRoot().hostSystem().getHost("mockhost");
        assertEquals("b/storage/0", h.getService("storagenode").getConfigId());
    }

    @Test
    void canConfigureMmapNoCoreLimit() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group mmap-core-limit=\"200000\">" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" />" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" />" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(2, b.getStorageCluster().getChildren().size());
        assertTrue(b.getRootGroup().getMmapNoCoreLimit().isPresent());
        assertEquals(200000, b.getRootGroup().getMmapNoCoreLimit().get().longValue());

        assertEquals(2, s.getSearchNodes().size());
        assertTrue(s.getSearchNodes().get(0).getEnvStringForTesting().contains("VESPA_MMAP_NOCORE_LIMIT=200000"));
        assertTrue(s.getSearchNodes().get(1).getEnvStringForTesting().contains("VESPA_MMAP_NOCORE_LIMIT=200000"));
    }

    @Test
    void canAddEnvironmentVariable() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>1</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" />" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(1, b.getStorageCluster().getChildren().size());

        assertEquals(1, s.getSearchNodes().size());
        AbstractService node = s.getSearchNodes().get(0);
        node.addEnvironmentVariable("MY_ENV_1", 7);
        node.addEnvironmentVariable("MY_ENV_2", "7 8");
        assertTrue(node.getEnvStringForTesting().contains("MY_ENV_1=7"));
        assertTrue(node.getEnvStringForTesting().contains("MY_ENV_2=\"7 8\""));
        node.addEnvironmentVariable("MY_PARSED_ENV_1=7");
        node.addEnvironmentVariable("MY_PARSED_ENV_2=7 8");
        assertTrue(node.getEnvStringForTesting().contains("MY_PARSED_ENV_1=\"7\""));
        assertTrue(node.getEnvStringForTesting().contains("MY_PARSED_ENV_2=\"7 8\""));
    }

    @Test
    void addsEnvironmentVariablesfromFeatureFlag() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>1</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" />" +
                        "    </group>" +
                        "</content>", new TestProperties().setEnvironmentVariables(List.of("MY_1_ENV=xyz abc", "MY_2_ENV=2")));
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(1, b.getStorageCluster().getChildren().size());

        assertEquals(1, s.getSearchNodes().size());
        AbstractService node = s.getSearchNodes().get(0);
        assertTrue(node.getEnvStringForTesting().contains("MY_1_ENV=\"xyz abc\""));
        assertTrue(node.getEnvStringForTesting().contains("MY_2_ENV=\"2\""));
    }

    @Test
    void canConfigureCoreOnOOM() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group core-on-oom=\"true\">" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" />" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" />" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(2, b.getStorageCluster().getChildren().size());
        assertTrue(b.getRootGroup().getCoreOnOOM().isPresent());
        assertTrue(b.getRootGroup().getCoreOnOOM().get());

        assertEquals(2, s.getSearchNodes().size());
        assertFalse(s.getSearchNodes().get(0).getEnvStringForTesting().contains("VESPA_SILENCE_CORE_ON_OOM=true"));
        assertFalse(s.getSearchNodes().get(1).getEnvStringForTesting().contains("VESPA_SILENCE_CORE_ON_OOM=true"));
    }

    @Test
    void defaultCoreOnOOMIsFalse() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" />" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" />" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(2, b.getStorageCluster().getChildren().size());
        assertFalse(b.getRootGroup().getCoreOnOOM().isPresent());

        assertEquals(2, s.getSearchNodes().size());
        assertTrue(s.getSearchNodes().get(0).getEnvStringForTesting().contains("VESPA_SILENCE_CORE_ON_OOM=true"));
        assertTrue(s.getSearchNodes().get(1).getEnvStringForTesting().contains("VESPA_SILENCE_CORE_ON_OOM=true"));
    }

    @Test
    void canConfigureMmapNoCoreLimitPerHost() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"  mmap-core-limit=\"200000\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" />" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(2, b.getStorageCluster().getChildren().size());
        assertFalse(b.getRootGroup().getMmapNoCoreLimit().isPresent());

        assertEquals(2, s.getSearchNodes().size());
        assertTrue(s.getSearchNodes().get(0).getEnvStringForTesting().contains("VESPA_MMAP_NOCORE_LIMIT=200000"));
        assertFalse(s.getSearchNodes().get(1).getEnvStringForTesting().contains("VESPA_MMAP_NOCORE_LIMIT="));
    }

    @Test
    void canConfigureCoreOnOOMPerHost() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" core-on-oom=\"true\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" core-on-oom=\"false\"/>" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(2, b.getStorageCluster().getChildren().size());
        assertFalse(b.getRootGroup().getCoreOnOOM().isPresent());

        assertEquals(2, s.getSearchNodes().size());
        assertFalse(s.getSearchNodes().get(0).getEnvStringForTesting().contains("VESPA_SILENCE_CORE_ON_OOM=true"));
        assertTrue(s.getSearchNodes().get(1).getEnvStringForTesting().contains("VESPA_SILENCE_CORE_ON_OOM=true"));
    }

    @Test
    void canConfigureVespaMalloc() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group no-vespamalloc=\"proton\" vespamalloc-debug=\"distributord\" vespamalloc-debug-stacktrace=\"all\" vespamalloc=\"storaged\">" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"2\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"3\"/>" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(4, b.getStorageCluster().getChildren().size());
        assertTrue(b.getRootGroup().getNoVespaMalloc().isPresent());
        assertEquals("proton", b.getRootGroup().getNoVespaMalloc().get());
        assertTrue(b.getRootGroup().getVespaMalloc().isPresent());
        assertEquals("storaged", b.getRootGroup().getVespaMalloc().get());
        assertTrue(b.getRootGroup().getVespaMallocDebug().isPresent());
        assertEquals("distributord", b.getRootGroup().getVespaMallocDebug().get());
        assertTrue(b.getRootGroup().getVespaMallocDebugStackTrace().isPresent());
        assertEquals("all", b.getRootGroup().getVespaMallocDebugStackTrace().get());

        assertEquals(4, s.getSearchNodes().size());
        for (SearchNode n : s.getSearchNodes()) {
            assertEquals("OMP_NUM_THREADS=1 VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_NO_VESPAMALLOC=\"proton\" VESPA_USE_VESPAMALLOC=\"storaged\" VESPA_USE_VESPAMALLOC_D=\"distributord\" VESPA_USE_VESPAMALLOC_DST=\"all\"", n.getEnvStringForTesting());
        }
    }

    @Test
    void canConfigureVespaMallocPerHost() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" no-vespamalloc=\"proton\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" vespamalloc-debug=\"distributord\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"2\" vespamalloc-debug-stacktrace=\"all\"/>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"3\" vespamalloc=\"storaged\"/>" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(4, b.getStorageCluster().getChildren().size());
        assertFalse(b.getRootGroup().getNoVespaMalloc().isPresent());
        assertFalse(b.getRootGroup().getVespaMalloc().isPresent());
        assertFalse(b.getRootGroup().getVespaMallocDebug().isPresent());
        assertFalse(b.getRootGroup().getVespaMallocDebugStackTrace().isPresent());

        assertEquals(4, s.getSearchNodes().size());
        assertEquals("OMP_NUM_THREADS=1 VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_NO_VESPAMALLOC=\"proton\"", s.getSearchNodes().get(0).getEnvStringForTesting());
        assertEquals("OMP_NUM_THREADS=1 VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_VESPAMALLOC_D=\"distributord\"", s.getSearchNodes().get(1).getEnvStringForTesting());
        assertEquals("OMP_NUM_THREADS=1 VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_VESPAMALLOC_DST=\"all\"", s.getSearchNodes().get(2).getEnvStringForTesting());
        assertEquals("OMP_NUM_THREADS=1 VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_VESPAMALLOC=\"storaged\"", s.getSearchNodes().get(3).getEnvStringForTesting());
    }

    @Test
    void canConfigureCpuAffinity() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" cpu-socket=\"0\" />" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" cpu-socket=\"1\" />" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(2, b.getStorageCluster().getChildren().size());
        assertTrue(b.getStorageCluster().getChildren().get("0").getAffinity().isPresent());
        assertEquals(0, b.getStorageCluster().getChildren().get("0").getAffinity().get().cpuSocket());
        assertTrue(b.getStorageCluster().getChildren().get("1").getAffinity().isPresent());
        assertEquals(1, b.getStorageCluster().getChildren().get("1").getAffinity().get().cpuSocket());

        assertEquals(2, s.getSearchNodes().size());
        assertTrue(s.getSearchNodes().get(0).getAffinity().isPresent());
        assertEquals(0, s.getSearchNodes().get(0).getAffinity().get().cpuSocket());
        assertTrue(s.getSearchNodes().get(1).getAffinity().isPresent());
        assertEquals(1, s.getSearchNodes().get(1).getAffinity().get().cpuSocket());
    }

    @Test
    void canConfigureCpuAffinityAutomatically() {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                        "    <redundancy>2</redundancy>" +
                        "      <documents>" +
                        "        <document type='music' mode='index'/>" +
                        "      </documents>" +
                        "    <group cpu-socket-affinity=\"true\">" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\" />" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\" />" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"2\" />" +
                        "      <node hostalias=\"mockhost2\" distribution-key=\"3\" />" +
                        "      <node hostalias=\"mockhost2\" distribution-key=\"4\" />" +
                        "      <node hostalias=\"mockhost3\" distribution-key=\"5\" />" +
                        "    </group>" +
                        "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(6, b.getStorageCluster().getChildren().size());
        assertTrue(b.getRootGroup().useCpuSocketAffinity());

        assertEquals(6, s.getSearchNodes().size());
        assertTrue(s.getSearchNodes().get(0).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(1).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(2).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(3).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(4).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(5).getAffinity().isPresent());
        assertEquals(0, s.getSearchNodes().get(0).getAffinity().get().cpuSocket());
        assertEquals(1, s.getSearchNodes().get(1).getAffinity().get().cpuSocket());
        assertEquals(2, s.getSearchNodes().get(2).getAffinity().get().cpuSocket());
        assertEquals(0, s.getSearchNodes().get(3).getAffinity().get().cpuSocket());
        assertEquals(1, s.getSearchNodes().get(4).getAffinity().get().cpuSocket());
        assertEquals(0, s.getSearchNodes().get(5).getAffinity().get().cpuSocket());

        // TODO: Only needed for the search nodes anyway?
        assertFalse(b.getStorageCluster().getChildren().get("0").getAffinity().isPresent());
        assertFalse(b.getStorageCluster().getChildren().get("1").getAffinity().isPresent());
        assertFalse(b.getStorageCluster().getChildren().get("2").getAffinity().isPresent());
        assertFalse(b.getStorageCluster().getChildren().get("3").getAffinity().isPresent());
        assertFalse(b.getStorageCluster().getChildren().get("4").getAffinity().isPresent());
        assertFalse(b.getStorageCluster().getChildren().get("5").getAffinity().isPresent());
        //assertEquals(0, b.getStorageNodes().getChildren().get("0").getAffinity().get().cpuSocket());
        //assertEquals(1, b.getStorageNodes().getChildren().get("1").getAffinity().get().cpuSocket());
        //assertEquals(2, b.getStorageNodes().getChildren().get("2").getAffinity().get().cpuSocket());
        //assertEquals(0, b.getStorageNodes().getChildren().get("3").getAffinity().get().cpuSocket());
        //assertEquals(1, b.getStorageNodes().getChildren().get("4").getAffinity().get().cpuSocket());
        //assertEquals(0, b.getStorageNodes().getChildren().get("5").getAffinity().get().cpuSocket());

    }

    @Test
    void requireBug5357273() {
        try {
            createContent(
                    "  <content version='1.0' id='storage'>\n" +
                            "      <redundancy>3</redundancy>\n" +
                            "    <documents>" +
                            "       <document type='music' mode='index'/>" +
                            "    </documents>" +
                            "      <group>\n" +
                            "        <node hostalias='mockhost' distribution-key='0' />\n" +
                            "      </group>\n" +
                            "      <engine>\n" +
                            "       <dummy/>\n" +
                            "      </engine>\n" +
                            "  </content>\n");

            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertEquals("Persistence engine does not allow for indexed search. Please use <proton> as your engine.", e.getMessage());
        }
    }

    @Test
    void handleProtonTuning() {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">" +
                        "    <redundancy>3</redundancy>" +
                        "    <engine>" +
                        "      <proton>" +
                        "        <tuning>" +
                        "          <searchnode>" +
                        "            <summary>" +
                        "              <store>" +
                        "                <cache>" +
                        "                  <maxsize>8192</maxsize>" +
                        "                  <compression>" +
                        "                    <type>lz4</type>" +
                        "                    <level>8</level>" +
                        "                  </compression>" +
                        "                </cache>" +
                        "              </store>" +
                        "              <io>" +
                        "                <read>directio</read>" +
                        "              </io>" +
                        "            </summary>" +
                        "          </searchnode>" +
                        "        </tuning>" +
                        "      </proton>" +
                        "    </engine>" +
                        "    <documents>" +
                        "       <document type='music' mode='index'/>" +
                        "    </documents>" +
                        "    <group>" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                        "    </group>" +
                        "</content>"
        );

        assertTrue(a.getPersistence() instanceof  ProtonEngine.Factory);
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        a.getSearch().getConfig(pb);
        List<String> serialize = ConfigInstance.serialize(new ProtonConfig(pb));
        String cfg = StringUtilities.implode(serialize.toArray(new String[serialize.size()]), "\n");
        assertTrue(cfg.contains("summary.cache.maxbytes 8192"));
        assertTrue(cfg.contains("summary.cache.compression.level 8"));
        assertTrue(cfg.contains("summary.cache.compression.type LZ4"));
        assertTrue(cfg.contains("summary.read.io DIRECTIO"));
    }

    @Test
    @Disabled
    void ensureOverrideAppendedOnlyOnce() {
        ContentCluster content = createContent(
                "<content version='1.0' id='search'>" +
                        "  <config name=\"vespa.config.search.core.proton\">" +
                        "    <numthreadspersearch>1</numthreadspersearch>" +
                        "    <search>" +
                        "      <mmap>" +
                        "        <options><item>POPULATE</item></options>" +
                        "      </mmap>" +
                        "    </search>" +
                        "  </config>" +
                        "  <redundancy>2</redundancy>" +
                        "  <documents>" +
                        "    <document type='music' mode='index'/>" +
                        "  </documents>" +
                        "  <group>" +
                        "    <node hostalias='mockhost' distribution-key='0'/>" +
                        "  </group>" +
                        "</content>");
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        content.getSearch().getIndexed().getSearchNode(0).cascadeConfig(builder);
        content.getSearch().getIndexed().getSearchNode(0).addUserConfig(builder);
        ProtonConfig config = new ProtonConfig(builder);
        assertEquals(1, config.search().mmap().options().size());
        assertEquals(ProtonConfig.Search.Mmap.Options.POPULATE, config.search().mmap().options(0));
    }

    private String singleNodeContentXml() {
        return  "<services>" +
                "<content version='1.0' id='search'>" +
                "  <redundancy>1</redundancy>" +
                "  <documents>" +
                "    <document type='music' mode='index'/>" +
                "  </documents>" +
                "  <nodes count='1'/>" +
                "</content>" +
                "</services>";
    }

    @Test
    void ensurePruneRemovedDocumentsAgeForHostedVespa() {
        {
            ContentCluster contentNonHosted = createContent(
                    "<content version='1.0' id='search'>" +
                            "  <redundancy>1</redundancy>" +
                            "  <documents>" +
                            "    <document type='music' mode='index'/>" +
                            "  </documents>" +
                            "  <nodes>" +
                            "    <node hostalias='mockhost' distribution-key='0'/>" +
                            "  </nodes>" +
                            "</content>");
            ProtonConfig configNonHosted = getProtonConfig(contentNonHosted);
            ProtonConfig defaultConfig = new ProtonConfig(new ProtonConfig.Builder());
            assertEquals(defaultConfig.pruneremoveddocumentsage(), configNonHosted.pruneremoveddocumentsage(), 0.001);
        }

        {
            String hostedXml = singleNodeContentXml();

            DeployState.Builder deployStateBuilder = new DeployState.Builder().properties(new TestProperties().setHostedVespa(true));
            VespaModel model = new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                    .withServices(hostedXml)
                    .withSearchDefinition(MockApplicationPackage.MUSIC_SCHEMA)
                    .build())
                    .create(deployStateBuilder);
            ProtonConfig config = getProtonConfig(model.getContentClusters().values().iterator().next());
            assertEquals(349260.0, config.pruneremoveddocumentsage(), 0.001);
        }
    }

    private String xmlWithVisibilityDelay(Double visibilityDelay) {
        return "<services>" +
                "<content version='1.0' id='search'>" +
                "  <redundancy>1</redundancy>" +
                "  <search>" +
                ((visibilityDelay != null) ? "    <visibility-delay>" + visibilityDelay + "</visibility-delay>" : "") +
                "  </search>" +
                "  <documents>" +
                "    <document type='music' mode='index'/>" +
                "  </documents>" +
                "  <nodes count='1'/>" +
                "</content>" +
                "</services>";
    }

    private ProtonConfig resolveProtonConfig(TestProperties props, String hostedXml) {
        var deployStateBuilder = new DeployState.Builder().properties(props);
        var model = new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                .withServices(hostedXml)
                .withSearchDefinition(MockApplicationPackage.MUSIC_SCHEMA)
                .build())
                .create(deployStateBuilder);
        return getProtonConfig(model.getContentClusters().values().iterator().next());
    }

    private void verifyFeedSequencer(String input, String expected) {
        verifyFeedSequencer(input, expected, 0);
    }

    private void verifyFeedSequencer(String input, String expected, double visibilityDelay) {
        String hostedXml = xmlWithVisibilityDelay(visibilityDelay);
        var config = resolveProtonConfig(new TestProperties().setFeedSequencerType(input), hostedXml);
        assertEquals(expected, config.indexing().optimize().toString());
    }

    @Test
    void ensureFeedSequencerIsControlledByFlag() {
        verifyFeedSequencer("LATENCY", "LATENCY");
        verifyFeedSequencer("ADAPTIVE", "ADAPTIVE");
        verifyFeedSequencer("THROUGHPUT", "THROUGHPUT", 0);
        verifyFeedSequencer("THROUGHPUT", "THROUGHPUT", 0.1);

        verifyFeedSequencer("THOUGHPUT", "LATENCY");
        verifyFeedSequencer("adaptive", "LATENCY");

    }

    private void verifyThatFeatureFlagControlsVisibilityDelayDefault(Double xmlOverride, double expected) {
        String hostedXml = xmlWithVisibilityDelay(xmlOverride);
        var config = resolveProtonConfig(new TestProperties(), hostedXml);
        assertEquals(expected, config.documentdb(0).visibilitydelay(), 0.0);
    }

    @Test
    void verifyThatFeatureFlagControlsVisibilityDelayDefault() {
        verifyThatFeatureFlagControlsVisibilityDelayDefault(null, 0.0);
        verifyThatFeatureFlagControlsVisibilityDelayDefault(0.5, 0.5);
        verifyThatFeatureFlagControlsVisibilityDelayDefault(0.6, 0.6);
    }

    @Test
    void failWhenNoDocumentsElementSpecified() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            createContent(
                    "<content version =\"1.0\" id=\"a\">" +
                            "    <redundancy>3</redundancy>" +
                            "    <engine>" +
                            "      <dummy/>" +
                            "    </engine>" +
                            "    <group>" +
                            "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>" +
                            "    </group>" +
                            "</content>");
        });
        assertEquals("The <documents> element is mandatory in content cluster 'a'", exception.getMessage());
    }

    private ProtonConfig getProtonConfig(ContentCluster content) {
        ProtonConfig.Builder configBuilder = new ProtonConfig.Builder();
        content.getSearch().getIndexed().getSearchNode(0).cascadeConfig(configBuilder);
        content.getSearch().getIndexed().getSearchNode(0).addUserConfig(configBuilder);

        return new ProtonConfig(configBuilder);
    }

    private ApplicationPackage createAppWithMusic(String hosts, String services) {
        return new MockApplicationPackage.Builder()
                .withHosts(hosts)
                .withServices(services)
                .withSearchDefinition(MockApplicationPackage.MUSIC_SCHEMA)
                .build();
    }

    private ContentCluster createContent(String xml) {
        return createContent(xml, new TestProperties());
    }
    private ContentCluster createContent(String xml, TestProperties props) {
        String combined =  "" +
                "<services>"+
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='mockhost'/>" +
                "  </admin>" +
                xml +
                "</services>";


        var deployStateBuilder = new DeployState.Builder().properties(props);
        VespaModel m = new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                .withHosts(getHosts())
                .withServices(combined)
                .withSearchDefinition(MockApplicationPackage.MUSIC_SCHEMA)
                .build())
                .create(deployStateBuilder);

        return m.getContentClusters().isEmpty()
                ? null
                : m.getContentClusters().values().iterator().next();
    }
    private ContentCluster createContentWithBooksToo(String xml) {
        String combined =  "" +
                "<services>"+
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='mockhost'/>" +
                "  </admin>" +
                xml +
                "</services>";

        VespaModel m = new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                                                                .withHosts(getHosts())
                                                                .withServices(combined)
                                                                .withSchemas(Arrays.asList(MockApplicationPackage.MUSIC_SCHEMA,
                                                                                           MockApplicationPackage.BOOK_SCHEMA))
                                                                .build())
                .create();

        return m.getContentClusters().isEmpty()
                ? null
                : m.getContentClusters().values().iterator().next();
    }

    private String getHosts() {
        return "<?xml version='1.0' encoding='utf-8' ?>" +
                "<hosts>" +
                "  <host name='node0'>" +
                "    <alias>mockhost</alias>" +
                "  </host>" +
                "  <host name='node1'>" +
                "    <alias>mockhost2</alias>" +
                "  </host>" +
                "  <host name='node2'>" +
                "    <alias>mockhost3</alias>" +
                "  </host>" +
                "</hosts>";
    }

    private String getServices(String groupXml) {
        return getConfigOverrideServices(groupXml, "");
    }

    private String getConfigOverrideServices(String groupXml, String documentOverrides) {
        return "" +
                "<services>"+
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='mockhost'/>" +
                "  </admin>" +
                "  <container version='1.0' id='qrc'>" +
                "      <search/>" +
                "      <nodes>" +
                "        <node hostalias='mockhost' />" +
                "      </nodes>" +
                "  </container>" +
                "  <content version='1.0' id='clu'>" +
                "    <documents>" +
                "      <document type='music' mode='index'>" +
                documentOverrides +
                "      </document>" +
                "    </documents>" +
                "    <redundancy>3</redundancy>"+
                "    <engine>" +
                "      <proton>" +
                "        <query-timeout>7.3</query-timeout>" +
                "      </proton>" +
                "    </engine>" +
                "    <group>"+
                groupXml +
                "    </group>"+
                "  </content>" +
                "</services>";
    }

    private String getBasicServices() {
        return getServices("<node hostalias='mockhost' distribution-key='0'/>");
    }

    private static void assertServices(HostResource host, String[] services) {
        String missing = "";

        for (String s : services) {
            if (host.getService(s) == null) {
                missing += s + ",";
            }
        }

        String extra = "";
        for (Service s : host.getServices()) {
            boolean found = false;
            for (String n : services) {
                if (n.equals(s.getServiceName())) {
                    found = true;
                }
            }

            if (!found) {
                extra += s.getServiceName() + ",";
            }
        }

        assertEquals("Missing:  Extra: ", "Missing: " + missing+ " Extra: " + extra);

        assertEquals(services.length, host.getServices().size());
    }

}
