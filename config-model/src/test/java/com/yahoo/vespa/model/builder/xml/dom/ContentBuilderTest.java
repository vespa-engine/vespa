// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.config.search.core.PartitionsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.config.GenericConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.engines.ProtonEngine;
import com.yahoo.vespa.model.search.*;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

/**
 * @author baldersheim
 */
public class ContentBuilderTest extends DomBuilderTest {
    private ContentCluster createContent(String xml) throws Exception {
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
                                                                .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
                                                                .build())
                .create();

        return m.getContentClusters().isEmpty()
                ? null
                : m.getContentClusters().values().iterator().next();
    }
    private ContentCluster createContentWithBooksToo(String xml) throws Exception {
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
                                                                .withSearchDefinitions(Arrays.asList(MockApplicationPackage.MUSIC_SEARCHDEFINITION,
                                                                                                     MockApplicationPackage.BOOK_SEARCHDEFINITION))
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
                "  <jdisc version='1.0' id='qrc'>" +
                "      <search/>" +
                "      <nodes>" +
                "        <node hostalias='mockhost' />" +
                "      </nodes>" +
                "  </jdisc>" +
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

    public static void assertServices(HostResource host, String [] services) {
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

    @Test
    public void handleSingleNonSearchPersistentDummy() throws Exception {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">"+
                "    <redundancy>3</redundancy>"+
                "    <documents>" +
                "       <document type=\"music\" mode=\"store-only\"/>" +
                "    </documents>" +
                "    <engine>"+
                "      <dummy/>"+
                "    </engine>"+
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
                "</content>");

        ContentSearchCluster s = a.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertTrue(s.getClusters().isEmpty());

        assertTrue(a.getPersistence() instanceof com.yahoo.vespa.model.content.engines.DummyPersistence.Factory);
    }

    @Test
    public void handleSingleNonSearchPersistentVds() throws Exception {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">"+
                "    <redundancy>3</redundancy>"+
                "    <documents>" +
                "       <document type=\"music\" mode=\"store-only\"/>" +
                "    </documents>" +
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
                "</content>");

        ContentSearchCluster s = a.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertTrue(s.getClusters().isEmpty());

        assertTrue(a.getPersistence() instanceof ProtonEngine.Factory);

        assertEquals(1, a.getStorageNodes().getChildren().size());
    }

    @Test
    public void handleSingleNonSearchPersistentProton() throws Exception {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">"+
                "    <redundancy>3</redundancy>"+
                "    <documents>" +
                "       <document type=\"music\" mode=\"store-only\"/>" +
                "    </documents>" +
                "    <engine>"+
                "      <proton/>"+
                "    </engine>"+
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
                "</content>");

        ContentSearchCluster s = a.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertTrue(s.getClusters().isEmpty());

        assertTrue(a.getPersistence() instanceof ProtonEngine.Factory);

        assertEquals(1, a.getStorageNodes().getChildren().size());
    }

    @Test
    public void handleSingleNonSearchNonPersistentCluster() throws Exception {
        ContentCluster a = createContent(
                "<content version =\"1.0\" id=\"a\">"+
                "    <redundancy>3</redundancy>"+
                "    <documents>" +
                "       <document type=\"music\" mode=\"store-only\"/>" +
                "    </documents>" +
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
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
        assertEquals(1, a.getStorageNodes().getChildren().size());
        assertEquals("a", a.getConfigId());
    }

    @Test
    public void handleIndexedOnlyWithoutPersistence() throws Exception {
        VespaModel m = new VespaModelCreatorWithMockPkg(createAppWithMusic(getHosts(), getBasicServices())).create();

        ContentCluster c = CollectionUtil.first(m.getContentClusters().values());
        ContentSearchCluster s = c.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertEquals(1, s.getClusters().size());
        assertNotNull(s.getIndexed());
        assertEquals("clu", s.getIndexed().getClusterName());
        assertEquals(7.3, s.getIndexed().getQueryTimeout(), 0.0);

        assertTrue(c.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, c.getStorageNodes().getChildren().size());
        assertEquals("clu", c.getConfigId());
        //assertEquals("content/a/0", a.getRootGroup().getNodes().get(0).getConfigId()); // This is how it should look like in an ideal world.
        assertEquals("clu/storage/0", c.getRootGroup().getNodes().get(0).getConfigId()); // Due to reuse.
        assertEquals(1, c.getRoot().getHostSystem().getHosts().size());
        HostResource h = c.getRoot().getHostSystem().getHost("mockhost");
        String [] expectedServices = {"logd", "configproxy","config-sentinel", "qrserver", "storagenode", "searchnode", "distributor", "topleveldispatch", "transactionlogserver"};
// TODO        assertServices(h, expectedServices);
        assertEquals("clu/storage/0", h.getService("storagenode").getConfigId());
        assertEquals("clu/search/cluster.clu/0", h.getService("searchnode").getConfigId());
        assertEquals("clu/distributor/0", h.getService("distributor").getConfigId());
        assertEquals("clu/search/cluster.clu/tlds/qrc.0.tld.0", h.getService("topleveldispatch").getConfigId());
        //assertEquals("tcp/node0:19104", h.getService("topleveldispatch").getConfig("partitions", "").innerArray("dataset").value("0").innerArray("engine").value("0").getString("name_and_port"));
        PartitionsConfig partitionsConfig = new PartitionsConfig((PartitionsConfig.Builder)
                m.getConfig(new PartitionsConfig.Builder(), "clu/search/cluster.clu/tlds/qrc.0.tld.0"));
        assertTrue(partitionsConfig.dataset(0).engine(0).name_and_port().startsWith("tcp/node0:191"));
    }

    @Test
    public void testConfigIdLookup() throws Exception {
        VespaModel m = new VespaModelCreatorWithMockPkg(createAppWithMusic(getHosts(), getBasicServices())).create();

        PartitionsConfig partitionsConfig = new PartitionsConfig((PartitionsConfig.Builder)
                m.getConfig(new PartitionsConfig.Builder(), "clu/search/cluster.clu/tlds/qrc.0.tld.0"));
        assertTrue(partitionsConfig.dataset(0).engine(0).name_and_port().startsWith("tcp/node0:191"));
    }

    @Test
    public void testMultipleSearchNodesOnSameHost() throws Exception {
        String services = getServices("<node hostalias='mockhost' distribution-key='0'/>" +
                                      "<node hostalias='mockhost' distribution-key='1'/>");
        VespaModel m = new VespaModelCreatorWithMockPkg(createAppWithMusic(getHosts(), services)).create();
        PartitionsConfig partitionsConfig = new PartitionsConfig((PartitionsConfig.Builder)
                m.getConfig(new PartitionsConfig.Builder(), "clu/search/cluster.clu/tlds/qrc.0.tld.0"));
        assertTrue(partitionsConfig.dataset(0).engine(0).name_and_port().startsWith("tcp/node0:191"));
        IndexedSearchCluster sc = m.getContentClusters().get("clu").getSearch().getIndexed();
        assertEquals(2, sc.getSearchNodeCount());
    }

    @Test
    public void handleStreamingOnlyWithoutPersistence() throws Exception
    {
        final String musicClusterId = "music-cluster-id";

        ContentCluster cluster = createContent(
                "<content version='1.0' id='" + musicClusterId + "'>" +
                "    <redundancy>3</redundancy>"+
                "    <documents>"+
                "       <document type='music' mode='streaming'/>"+
                "    </documents>"+
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
                "</content>");
        ContentSearchCluster s;

        s = cluster.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertEquals(1, s.getClusters().size());
        assertNull(s.getIndexed());
        AbstractSearchCluster sc = s.getClusters().get(musicClusterId + ".music");
        assertEquals(musicClusterId + ".music", sc.getClusterName());
        assertEquals(musicClusterId, ((StreamingSearchCluster)sc).getStorageRouteSpec());

        assertTrue(cluster.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, cluster.getStorageNodes().getChildren().size());

        assertEquals(musicClusterId, cluster.getConfigId());
        //assertEquals("content/a/0", a.getRootGroup().getNodes().get(0).getConfigId());
        assertEquals(musicClusterId + "/storage/0", cluster.getRootGroup().getNodes().get(0).getConfigId()); // Due to reuse.
        assertEquals(1, cluster.getRoot().getHostSystem().getHosts().size());
        HostResource h = cluster.getRoot().getHostSystem().getHost("mockhost");
        String [] expectedServices = {
                "logd", "configproxy",
                "config-sentinel", "configserver", "logserver",
                "slobrok", "container-clustercontroller",
                "storagenode", "distributor","searchnode","transactionlogserver"
        };
        assertServices(h, expectedServices);

        assertEquals(musicClusterId + "/storage/0", h.getService("storagenode").getConfigId());

        /* Not yet
        assertNotNull(h.getService("qrserver"));
        assertNotNull(h.getService("topleveldisptach"));
        assertNotNull(h.getService("docproc"));
        */

    }

    @Test
    public void requireThatContentStreamingHandlesMultipleSearchDefinitions() throws Exception
    {
        final String musicClusterId = "music-cluster-id";

        ContentCluster cluster = createContentWithBooksToo(
                "<content version='1.0' id='" + musicClusterId + "'>" +
                "    <redundancy>3</redundancy>"+
                "    <documents>"+
                "       <document type='music' mode='streaming'/>"+
                "       <document type='book' mode='streaming'/>"+
                "    </documents>"+
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
                "</content>");
        ContentSearchCluster s;

        s = cluster.getSearch();
        assertFalse(s.hasIndexedCluster());
        assertEquals(2, s.getClusters().size());
        assertNull(s.getIndexed());
        {
            String id = musicClusterId + ".book";
            AbstractSearchCluster sc = s.getClusters().get(id);
            assertEquals(id, sc.getClusterName());
            assertEquals(musicClusterId, ((StreamingSearchCluster) sc).getStorageRouteSpec());
        }
        {
            String id = musicClusterId + ".music";
            AbstractSearchCluster sc = s.getClusters().get(id);
            assertEquals(id, sc.getClusterName());
            assertEquals(musicClusterId, ((StreamingSearchCluster) sc).getStorageRouteSpec());
        }

        assertTrue(cluster.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, cluster.getStorageNodes().getChildren().size());

        assertEquals(musicClusterId, cluster.getConfigId());
    }

    @Test
    public void handleIndexedWithoutPersistence() throws Exception
    {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                "    <redundancy>3</redundancy>"+
                "      <documents>"+
                "        <document type='music' mode='index'/>"+
                "      </documents>"+
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>"+
                "    </group>"+
                "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertEquals(1, s.getClusters().size());
        assertNotNull(s.getIndexed());
        assertEquals("b", s.getIndexed().getClusterName());

        assertTrue(b.getPersistence() instanceof ProtonEngine.Factory);
        assertEquals(1, b.getStorageNodes().getChildren().size());

        assertEquals("b", b.getConfigId());
        //assertEquals("content/a/0", a.getRootGroup().getNodes().get(0).getConfigId());
        assertEquals("b/storage/0", b.getRootGroup().getNodes().get(0).getConfigId()); // Due to reuse.
        assertEquals(1, b.getRoot().getHostSystem().getHosts().size());
        HostResource h = b.getRoot().getHostSystem().getHost("mockhost");
        assertEquals("b/storage/0", h.getService("storagenode").getConfigId());
    }

    @Test
    public void canConfigureMmapNoCoreLimit() throws Exception {
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
        assertEquals(2, b.getStorageNodes().getChildren().size());
        assertTrue(b.getRootGroup().getMmapNoCoreLimit().isPresent());
        assertEquals(200000, b.getRootGroup().getMmapNoCoreLimit().get().longValue());

        assertThat(s.getSearchNodes().size(), is(2));
        assertEquals(200000, s.getSearchNodes().get(0).getMMapNoCoreLimit());
        assertEquals(200000, s.getSearchNodes().get(1).getMMapNoCoreLimit());
        assertEquals("VESPA_MMAP_NOCORE_LIMIT=200000 ", s.getSearchNodes().get(0).getMMapNoCoreEnvVariable());
        assertEquals("VESPA_MMAP_NOCORE_LIMIT=200000 ", s.getSearchNodes().get(1).getMMapNoCoreEnvVariable());
    }

    @Test
    public void canConfigureCoreOnOOM() throws Exception {
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
        assertEquals(2, b.getStorageNodes().getChildren().size());
        assertTrue(b.getRootGroup().getCoreOnOOM().isPresent());
        assertTrue(b.getRootGroup().getCoreOnOOM().get());

        assertThat(s.getSearchNodes().size(), is(2));
        assertTrue(s.getSearchNodes().get(0).getCoreOnOOM());
        assertTrue(s.getSearchNodes().get(1).getCoreOnOOM());
        assertEquals("", s.getSearchNodes().get(0).getCoreOnOOMEnvVariable());
        assertEquals("", s.getSearchNodes().get(1).getCoreOnOOMEnvVariable());
    }

    @Test
    public void defaultCoreOnOOMIsFalse() throws Exception {
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
        assertEquals(2, b.getStorageNodes().getChildren().size());
        assertFalse(b.getRootGroup().getCoreOnOOM().isPresent());

        assertThat(s.getSearchNodes().size(), is(2));
        assertFalse(s.getSearchNodes().get(0).getCoreOnOOM());
        assertFalse(s.getSearchNodes().get(1).getCoreOnOOM());
        assertEquals("VESPA_SILENCE_CORE_ON_OOM=true ", s.getSearchNodes().get(0).getCoreOnOOMEnvVariable());
        assertEquals("VESPA_SILENCE_CORE_ON_OOM=true ", s.getSearchNodes().get(1).getCoreOnOOMEnvVariable());
    }

    @Test
    public void canConfigureMmapNoCoreLimitPerHost() throws Exception {
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
        assertEquals(2, b.getStorageNodes().getChildren().size());
        assertFalse(b.getRootGroup().getMmapNoCoreLimit().isPresent());

        assertThat(s.getSearchNodes().size(), is(2));
        assertEquals(200000, s.getSearchNodes().get(0).getMMapNoCoreLimit());
        assertEquals(-1, s.getSearchNodes().get(1).getMMapNoCoreLimit());
        assertEquals("VESPA_MMAP_NOCORE_LIMIT=200000 ", s.getSearchNodes().get(0).getMMapNoCoreEnvVariable());
        assertEquals("", s.getSearchNodes().get(1).getMMapNoCoreEnvVariable());
    }

    @Test
    public void canConfigureCoreOnOOMPerHost() throws Exception {
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
        assertEquals(2, b.getStorageNodes().getChildren().size());
        assertFalse(b.getRootGroup().getCoreOnOOM().isPresent());

        assertThat(s.getSearchNodes().size(), is(2));
        assertTrue(s.getSearchNodes().get(0).getCoreOnOOM());
        assertFalse(s.getSearchNodes().get(1).getCoreOnOOM());
        assertEquals("", s.getSearchNodes().get(0).getCoreOnOOMEnvVariable());
        assertEquals("VESPA_SILENCE_CORE_ON_OOM=true ", s.getSearchNodes().get(1).getCoreOnOOMEnvVariable());
    }

    @Test
    public void canConfigureVespaMalloc() throws Exception {
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
        assertEquals(4, b.getStorageNodes().getChildren().size());
        assertTrue(b.getRootGroup().getNoVespaMalloc().isPresent());
        assertEquals("proton", b.getRootGroup().getNoVespaMalloc().get());
        assertTrue(b.getRootGroup().getVespaMalloc().isPresent());
        assertEquals("storaged", b.getRootGroup().getVespaMalloc().get());
        assertTrue(b.getRootGroup().getVespaMallocDebug().isPresent());
        assertEquals("distributord", b.getRootGroup().getVespaMallocDebug().get());
        assertTrue(b.getRootGroup().getVespaMallocDebugStackTrace().isPresent());
        assertEquals("all", b.getRootGroup().getVespaMallocDebugStackTrace().get());

        assertThat(s.getSearchNodes().size(), is(4));
        for (SearchNode n : s.getSearchNodes()) {
            assertEquals("proton", n.getNoVespaMalloc());
            assertEquals("VESPA_USE_NO_VESPAMALLOC=\"proton\" ", n.getNoVespaMallocEnvVariable());
            assertEquals("distributord", n.getVespaMallocDebug());
            assertEquals("VESPA_USE_VESPAMALLOC=\"storaged\" ", n.getVespaMallocEnvVariable());
            assertEquals("all", n.getVespaMallocDebugStackTrace());
            assertEquals("VESPA_USE_VESPAMALLOC_D=\"distributord\" ", n.getVespaMallocDebugEnvVariable());
            assertEquals("storaged", n.getVespaMalloc());
            assertEquals("VESPA_USE_VESPAMALLOC_DST=\"all\" ", n.getVespaMallocDebugStackTraceEnvVariable());
            assertEquals("VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_NO_VESPAMALLOC=\"proton\" VESPA_USE_VESPAMALLOC=\"storaged\" VESPA_USE_VESPAMALLOC_D=\"distributord\" VESPA_USE_VESPAMALLOC_DST=\"all\" ", n.getEnvVariables());
        }
    }

    @Test
    public void canConfigureVespaMallocPerHost() throws Exception {
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
        assertEquals(4, b.getStorageNodes().getChildren().size());
        assertFalse(b.getRootGroup().getNoVespaMalloc().isPresent());
        assertFalse(b.getRootGroup().getVespaMalloc().isPresent());
        assertFalse(b.getRootGroup().getVespaMallocDebug().isPresent());
        assertFalse(b.getRootGroup().getVespaMallocDebugStackTrace().isPresent());

        assertThat(s.getSearchNodes().size(), is(4));
        assertEquals("VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_NO_VESPAMALLOC=\"proton\" ", s.getSearchNodes().get(0).getEnvVariables());
        assertEquals("VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_VESPAMALLOC_D=\"distributord\" ", s.getSearchNodes().get(1).getEnvVariables());
        assertEquals("VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_VESPAMALLOC_DST=\"all\" ", s.getSearchNodes().get(2).getEnvVariables());
        assertEquals("VESPA_SILENCE_CORE_ON_OOM=true VESPA_USE_VESPAMALLOC=\"storaged\" ", s.getSearchNodes().get(3).getEnvVariables());
    }

    @Test
    public void canConfigureCpuAffinity() throws Exception
    {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                "    <redundancy>2</redundancy>"+
                "      <documents>"+
                "        <document type='music' mode='index'/>"+
                "      </documents>"+
                "    <group>"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\" cpu-socket=\"0\" />"+
                "      <node hostalias=\"mockhost\" distribution-key=\"1\" cpu-socket=\"1\" />"+
                "    </group>"+
                "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(2, b.getStorageNodes().getChildren().size());
        assertTrue(b.getStorageNodes().getChildren().get("0").getAffinity().isPresent());
        assertThat(b.getStorageNodes().getChildren().get("0").getAffinity().get().cpuSocket(), is(0));
        assertTrue(b.getStorageNodes().getChildren().get("1").getAffinity().isPresent());
        assertThat(b.getStorageNodes().getChildren().get("1").getAffinity().get().cpuSocket(), is(1));

        assertThat(s.getSearchNodes().size(), is(2));
        assertTrue(s.getSearchNodes().get(0).getAffinity().isPresent());
        assertThat(s.getSearchNodes().get(0).getAffinity().get().cpuSocket(), is(0));
        assertTrue(s.getSearchNodes().get(1).getAffinity().isPresent());
        assertThat(s.getSearchNodes().get(1).getAffinity().get().cpuSocket(), is(1));
    }

    @Test
    public void canConfigureCpuAffinityAutomatically() throws Exception
    {
        ContentCluster b = createContent(
                "<content version =\"1.0\" id=\"b\">" +
                "    <redundancy>2</redundancy>"+
                "      <documents>"+
                "        <document type='music' mode='index'/>"+
                "      </documents>"+
                "    <group cpu-socket-affinity=\"true\">"+
                "      <node hostalias=\"mockhost\" distribution-key=\"0\" />"+
                "      <node hostalias=\"mockhost\" distribution-key=\"1\" />"+
                "      <node hostalias=\"mockhost\" distribution-key=\"2\" />"+
                "      <node hostalias=\"mockhost2\" distribution-key=\"3\" />"+
                "      <node hostalias=\"mockhost2\" distribution-key=\"4\" />"+
                "      <node hostalias=\"mockhost3\" distribution-key=\"5\" />"+
                "    </group>"+
                "</content>");
        ContentSearchCluster s;

        s = b.getSearch();
        assertTrue(s.hasIndexedCluster());
        assertNotNull(s.getIndexed());
        assertEquals(6, b.getStorageNodes().getChildren().size());
        assertTrue(b.getRootGroup().useCpuSocketAffinity());

        assertThat(s.getSearchNodes().size(), is(6));
        assertTrue(s.getSearchNodes().get(0).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(1).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(2).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(3).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(4).getAffinity().isPresent());
        assertTrue(s.getSearchNodes().get(5).getAffinity().isPresent());
        assertThat(s.getSearchNodes().get(0).getAffinity().get().cpuSocket(),is (0));
        assertThat(s.getSearchNodes().get(1).getAffinity().get().cpuSocket(),is (1));
        assertThat(s.getSearchNodes().get(2).getAffinity().get().cpuSocket(),is (2));
        assertThat(s.getSearchNodes().get(3).getAffinity().get().cpuSocket(),is (0));
        assertThat(s.getSearchNodes().get(4).getAffinity().get().cpuSocket(),is (1));
        assertThat(s.getSearchNodes().get(5).getAffinity().get().cpuSocket(),is (0));

        // TODO: Only needed for the search nodes anyway?
        assertFalse(b.getStorageNodes().getChildren().get("0").getAffinity().isPresent());
        assertFalse(b.getStorageNodes().getChildren().get("1").getAffinity().isPresent());
        assertFalse(b.getStorageNodes().getChildren().get("2").getAffinity().isPresent());
        assertFalse(b.getStorageNodes().getChildren().get("3").getAffinity().isPresent());
        assertFalse(b.getStorageNodes().getChildren().get("4").getAffinity().isPresent());
        assertFalse(b.getStorageNodes().getChildren().get("5").getAffinity().isPresent());
        //assertThat(b.getStorageNodes().getChildren().get("0").getAffinity().get().cpuSocket(), is(0));
        //assertThat(b.getStorageNodes().getChildren().get("1").getAffinity().get().cpuSocket(), is(1));
        //assertThat(b.getStorageNodes().getChildren().get("2").getAffinity().get().cpuSocket(), is(2));
        //assertThat(b.getStorageNodes().getChildren().get("3").getAffinity().get().cpuSocket(), is(0));
        //assertThat(b.getStorageNodes().getChildren().get("4").getAffinity().get().cpuSocket(), is(1));
        //assertThat(b.getStorageNodes().getChildren().get("5").getAffinity().get().cpuSocket(), is(0));

    }

    @Test
    public void requireBug5357273() throws Exception {
        try {
            createContent(
                "  <content version='1.0' id='storage'>\n" +
                "      <redundancy>3</redundancy>\n" +
                "    <documents>"+
                "       <document type='music' mode='index'/>"+
                "    </documents>" +
                "      <group>\n" +
                "        <node hostalias='mockhost' distribution-key='0' />\n" +
                "      </group>\n" +
                "      <engine>\n" +
                "       <vds/>\n" +
                "      </engine>\n" +
                "  </content>\n");

            assertFalse(true);
        } catch (Exception e) {
            e.printStackTrace();
            assertEquals("Persistence engine does not allow for indexed search. Please use <proton> as your engine.", e.getMessage());
        }
    }

    @Test
    public void handleProtonTuning() throws Exception{
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
        assertThat(cfg, containsString("summary.cache.maxbytes 8192"));
        assertThat(cfg, containsString("summary.cache.compression.level 8"));
        assertThat(cfg, containsString("summary.cache.compression.type LZ4"));
        assertThat(cfg, containsString("summary.read.io DIRECTIO"));
    }

    @Test
    public void requireThatUserConfigCanBeSpecifiedForASearchDefinition() throws Exception {
        String services =  getConfigOverrideServices(
                "<node hostalias='mockhost' distribution-key='0'/>",
                "  <config name='mynamespace.myconfig'>" +
                "    <myfield>myvalue</myfield>" +
                "  </config>"
        );

        VespaModel m = new VespaModelCreatorWithMockPkg(createAppWithMusic(getHosts(), services)).create();
        String configId = "clu/search/cluster.clu/music";
        {
            GenericConfig.GenericConfigBuilder builder = 
                    new GenericConfig.GenericConfigBuilder(new ConfigDefinitionKey("myconfig", "mynamespace"), new ConfigPayloadBuilder());
            m.getConfig(builder, configId);
            assertEquals(builder.getPayload().getSlime().get().field("myfield").asString(), "myvalue");
        }
    }

    @Test
    public void requireOneTldPerSearchContainer() throws Exception {
        ContentCluster content = createContent(
                "  <content version='1.0' id='storage'>\n" +
                "    <redundancy>1</redundancy>\n" +
                "    <documents>" +
                "       <document type='music' mode='index'/>" +
                "    </documents>" +
                "    <group>\n" +
                "      <node hostalias='mockhost' distribution-key='0' />\n" +
                "    </group>\n" +
                "  </content>\n" +
                "  <jdisc version='1.0' id='qrc'>" +
                "      <search/>" +
                "      <nodes>" +
                "        <node hostalias='mockhost' />" +
                "      </nodes>" +
                "  </jdisc>" +
                "  <jdisc version='1.0' id='qrc2'>" +
                "      <http>" +
                "      <server id ='server1' port='5000' />" +
                "      </http>" +
                "      <search/>" +
                "      <nodes>" +
                "        <node hostalias='mockhost' />" +
                "        <node hostalias='mockhost2' />" +
                "      </nodes>" +
                "  </jdisc>"

        );
        List<Dispatch> tlds = content.getSearch().getIndexed().getTLDs();

        assertThat(tlds.get(0).getHostname(), is("node0"));
        assertThat(tlds.get(1).getHostname(), is("node0"));
        assertThat(tlds.get(2).getHostname(), is("node1"));

        assertThat(tlds.size(), is(3));
    }

    @Test
    @Ignore
    public void ensureOverrideAppendedOnlyOnce() throws Exception {
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
        assertThat(config.search().mmap().options().size(), is(1));
        assertThat(config.search().mmap().options(0), is(ProtonConfig.Search.Mmap.Options.POPULATE));
    }

    @Test
    public void ensurePruneRemovedDocumentsAgeForHostedVespa() throws Exception {
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
            String hostedXml = "<services>" +
                    "<content version='1.0' id='search'>" +
                    "  <redundancy>1</redundancy>" +
                    "  <documents>" +
                    "    <document type='music' mode='index'/>" +
                    "  </documents>" +
                    "  <nodes count='1'/>" +
                    "</content>" +
                    "</services>";

            DeployState.Builder deployStateBuilder = new DeployState.Builder().properties(
                    new DeployProperties.Builder()
                            .hostedVespa(true)
                            .build());
            VespaModel model = new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                                                                    .withServices(hostedXml)
                                                                    .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
                                                                    .build())
                    .create(deployStateBuilder);
            ProtonConfig config = getProtonConfig(model.getContentClusters().values().iterator().next());
            assertEquals(349260.0, config.pruneremoveddocumentsage(), 0.001);
        }
    }

    private ProtonConfig getProtonConfig(ContentCluster content) {
        ProtonConfig.Builder configBuilder = new ProtonConfig.Builder();
        content.getSearch().getIndexed().getSearchNode(0).cascadeConfig(configBuilder);
        content.getSearch().getIndexed().getSearchNode(0).addUserConfig(configBuilder);

        return new ProtonConfig(configBuilder);
    }

    ApplicationPackage createAppWithMusic(String hosts, String services) {
        return new MockApplicationPackage.Builder()
                .withHosts(hosts)
                .withServices(services)
                .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
                .build();
    }
}
