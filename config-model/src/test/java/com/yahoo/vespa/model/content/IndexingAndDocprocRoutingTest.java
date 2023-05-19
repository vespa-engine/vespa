// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.component.ComponentId;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.JSONAccessLog;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.HopBlueprint;
import com.yahoo.messagebus.routing.PolicyDirective;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingTable;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.routing.DocumentProtocol;
import com.yahoo.vespa.model.routing.Protocol;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Einar M R Rosenvinge
 */
public class IndexingAndDocprocRoutingTest extends ContentBaseTest {

    @Test
    void oneContentOneDoctypeImplicitIndexingClusterImplicitIndexingChain() {
        final String CLUSTERNAME = "musiccluster";
        SearchClusterSpec searchCluster = new SearchClusterSpec(CLUSTERNAME, null, null);
        searchCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));
        VespaModel model = getIndexedContentVespaModel(List.of(), List.of(searchCluster));
        assertIndexing(model, new DocprocClusterSpec("container", new DocprocChainSpec("container/chain.indexing")));
        assertFeedingRoute(model, CLUSTERNAME, "container/chain.indexing");
    }

    @Test
    void oneContentTwoDoctypesImplicitIndexingClusterImplicitIndexingChain() {
        final String CLUSTERNAME = "musicandbookscluster";
        SearchClusterSpec searchCluster = new SearchClusterSpec(CLUSTERNAME, null, null);
        searchCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));
        searchCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));
        VespaModel model = getIndexedContentVespaModel(List.of(), List.of(searchCluster));
        assertIndexing(model, new DocprocClusterSpec("container", new DocprocChainSpec("container/chain.indexing")));
        assertFeedingRoute(model, CLUSTERNAME, "container/chain.indexing");
    }

    @Test
    void twoContentTwoDoctypesImplicitIndexingClusterImplicitIndexingChain() {
        final String MUSIC = "musiccluster";
        SearchClusterSpec musicCluster = new SearchClusterSpec(MUSIC, null, null);
        musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

        final String BOOKS = "bookscluster";
        SearchClusterSpec booksCluster = new SearchClusterSpec(BOOKS, null, null);
        booksCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));

        VespaModel model = getIndexedContentVespaModel(List.of(), List.of(musicCluster, booksCluster));

        assertIndexing(model,
                new DocprocClusterSpec("container", new DocprocChainSpec("container/chain.indexing")));

        assertFeedingRoute(model, MUSIC, "container/chain.indexing");
        assertFeedingRoute(model, BOOKS, "container/chain.indexing");
    }


    @Test
    void oneContentOneDoctypeExplicitIndexingClusterImplicitIndexingChain() {
        final String CLUSTERNAME = "musiccluster";
        SearchClusterSpec searchCluster = new SearchClusterSpec(CLUSTERNAME, "dpcluster", null);
        searchCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));
        VespaModel model = getIndexedContentVespaModel(List.of(new DocprocClusterSpec("dpcluster")), List.of(searchCluster));
        assertIndexing(model, new DocprocClusterSpec("dpcluster", new DocprocChainSpec("dpcluster/chain.indexing")));
        assertFeedingRoute(model, CLUSTERNAME, "dpcluster/chain.indexing");
    }

    @Test
    void oneSearchOneDoctypeExplicitIndexingClusterExplicitIndexingChain() {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<services version=\"1.0\">\n" +
                        "  <admin version=\"2.0\">\n" +
                        "    <adminserver hostalias=\"node0\"/>    \n" +
                        "  </admin>\n" +
                        "\n" +
                        "    <content id=\"searchcluster\" version=\"1.0\">\n" +
                        "            <redundancy>2</redundancy>\n" +
                        "            <documents>\n" +
                        "                <document-processing cluster='dpcluster' chain='fooindexing'/>\n" +
                        "                <document type=\"music\" mode=\"index\"/>\n" +
                        "            </documents>\n" +
                        "                <nodes>\n" +
                        "                    <node hostalias=\"node0\" distribution-key=\"0\"/>\n" +
                        "                </nodes>\n" +
                        "    </content>\n" +
                        "  \n" +
                        "  <container version='1.0' id='dpcluster'>\n" +
                        "    <document-processing>\n" +
                        "      <chain id='fooindexing' inherits='indexing '/>\n" +
                        "    </document-processing>\n" +
                        "    <nodes>\n" +
                        "      <node hostalias='node0'/>\n" +
                        "    </nodes>\n" +
                        "    <http>\n" +
                        "      <server id='dpcluster' port='8000'/>\n" +
                        "    </http>\n" +
                        "  </container>\n" +
                        "</services>\n";
        VespaModel model = getIndexedSearchVespaModel(xml);
        assertIndexing(model, new DocprocClusterSpec("dpcluster", new DocprocChainSpec("dpcluster/chain.fooindexing", "indexing"),
                new DocprocChainSpec("dpcluster/chain.indexing")));
        assertFeedingRouteIndexed(model, "searchcluster", "dpcluster/chain.fooindexing");
    }

    @Test
    void twoContentTwoDoctypesExplicitIndexingInSameIndexingCluster() {
        final String MUSIC = "musiccluster";
        SearchClusterSpec musicCluster = new SearchClusterSpec(MUSIC, "dpcluster", null);
        musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

        final String BOOKS = "bookscluster";
        SearchClusterSpec booksCluster = new SearchClusterSpec(BOOKS, "dpcluster", null);
        booksCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));

        VespaModel model = getIndexedContentVespaModel(List.of(new DocprocClusterSpec("dpcluster")),
                List.of(musicCluster, booksCluster));

        assertIndexing(model, new DocprocClusterSpec("dpcluster", new DocprocChainSpec("dpcluster/chain.indexing")));
        assertFeedingRoute(model, MUSIC, "dpcluster/chain.indexing");
        assertFeedingRoute(model, BOOKS, "dpcluster/chain.indexing");
    }

    @Test
    void noContentClustersOneDocprocCluster() {
        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services version='1.0'>\n" +
                        "  <admin version='2.0'>\n" +
                        "    <adminserver hostalias='node0'/>\n" +
                        "  </admin>\n" +
                        "  <container version='1.0' id='dokprok'>\n" +
                        "    <document-processing />\n" +
                        "    <nodes>\n" +
                        "      <node hostalias='node0'/>\n" +
                        "    </nodes>\n" +
                        "  </container>\n" +
                        "</services>\n";

        List<String> sds = ApplicationPackageUtils.generateSchemas("music", "title", "artist");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), services, sds).create();
        assertIndexing(model, new DocprocClusterSpec("dokprok"));
    }

    @Test
    void twoContentTwoDoctypesExplicitIndexingInDifferentIndexingClustersExplicitChain() {
        final String MUSIC = "musiccluster";
        SearchClusterSpec musicCluster = new SearchClusterSpec(MUSIC, "dpmusiccluster", "dpmusicchain");
        musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

        final String BOOKS = "bookscluster";
        SearchClusterSpec booksCluster = new SearchClusterSpec(BOOKS, "dpbookscluster", "dpbookschain");
        booksCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));

        DocprocClusterSpec dpMusicCluster = new DocprocClusterSpec("dpmusiccluster", new DocprocChainSpec("dpmusicchain", "indexing"));
        DocprocClusterSpec dpBooksCluster = new DocprocClusterSpec("dpbookscluster", new DocprocChainSpec("dpbookschain", "indexing"));
        VespaModel model = getIndexedContentVespaModel(List.of(dpMusicCluster, dpBooksCluster),
                List.of(musicCluster, booksCluster));

        //after we generated model, add indexing chains for validation:
        dpMusicCluster.chains.clear();
        dpMusicCluster.chains.add(new DocprocChainSpec("dpmusiccluster/chain.indexing"));
        dpMusicCluster.chains.add(new DocprocChainSpec("dpmusiccluster/chain.dpmusicchain"));

        dpBooksCluster.chains.clear();
        dpBooksCluster.chains.add(new DocprocChainSpec("dpbookscluster/chain.indexing"));
        dpBooksCluster.chains.add(new DocprocChainSpec("dpbookscluster/chain.dpbookschain"));

        assertIndexing(model, dpMusicCluster, dpBooksCluster);
        assertFeedingRoute(model, MUSIC, "dpmusiccluster/chain.dpmusicchain");
        assertFeedingRoute(model, BOOKS, "dpbookscluster/chain.dpbookschain");
    }

    @Test
    void requiresIndexingInheritance() {
        try {
            SearchClusterSpec musicCluster = new SearchClusterSpec("musiccluster",
                    "dpmusiccluster",
                    "dpmusicchain");
            musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

            DocprocClusterSpec dpMusicCluster = new DocprocClusterSpec("dpmusiccluster", new DocprocChainSpec("dpmusicchain"));
            getIndexedContentVespaModel(List.of(dpMusicCluster), List.of(musicCluster));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Docproc chain 'dpmusicchain' must inherit from the 'indexing' chain", e.getMessage());
        }
    }

    @Test
    void indexingChainShouldNotBeTheDefaultChain() {
        try {
            SearchClusterSpec musicCluster = new SearchClusterSpec("musiccluster",
                    "dpmusiccluster",
                    "default");
            musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

            DocprocClusterSpec dpMusicCluster = new DocprocClusterSpec("dpmusiccluster", new DocprocChainSpec("default", "indexing"));
            getIndexedContentVespaModel(List.of(dpMusicCluster), List.of(musicCluster));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("content cluster 'musiccluster' specifies the chain 'default' as indexing chain"));
        }
    }

    private void assertIndexing(VespaModel model, DocprocClusterSpec... expectedDocprocClusters) {
        Map<String, ContainerCluster> docprocClusters = getDocprocClusters(model);
        assertEquals(expectedDocprocClusters.length, docprocClusters.size());

        for (DocprocClusterSpec expectedDocprocCluster : expectedDocprocClusters) {
            ContainerCluster docprocCluster = docprocClusters.get(expectedDocprocCluster.name);
            assertNotNull(docprocCluster);
            assertEquals(expectedDocprocCluster.name, docprocCluster.getName());
            ContainerDocproc containerDocproc = docprocCluster.getDocproc();
            assertNotNull(containerDocproc);
            List<DocprocChain> chains = containerDocproc.getChains().allChains().allComponents();
            assertEquals(expectedDocprocCluster.chains.size(),  chains.size());
            List<String> actualDocprocChains = new ArrayList<>();
            for (DocprocChain chain : chains) {
                actualDocprocChains.add(chain.getServiceName());
            }
            List<String> expectedDocprocChainStrings = new ArrayList<>();
            for (DocprocChainSpec spec : expectedDocprocCluster.chains) {
                expectedDocprocChainStrings.add(spec.name);
            }

            assertTrue(actualDocprocChains.containsAll(expectedDocprocChainStrings));

            assertNotNull(docprocCluster.getComponentsMap().get(ComponentId.fromString(AccessLog.class.getName())));
            assertNotNull(docprocCluster.getComponentsMap().get(ComponentId.fromString(JSONAccessLog.class.getName())));
        }
    }

    private Map<String, ContainerCluster> getDocprocClusters(VespaModel model) {
        Map<String, ContainerCluster> docprocClusters = new HashMap<>();
        for (ContainerCluster containerCluster : model.getContainerClusters().values()) {
            if (containerCluster.getDocproc() != null) {
                docprocClusters.put(containerCluster.getName(), containerCluster);
            }

        }
        return docprocClusters;
    }

    private void assertFeedingRoute(VespaModel model, String searchClusterName, String indexingHopName) {
        Routing routing = model.getRouting();
        List<Protocol> protocols = routing.getProtocols();

        DocumentProtocol documentProtocol = null;
        for (Protocol protocol : protocols) {
            if (protocol instanceof DocumentProtocol) {
                documentProtocol = (DocumentProtocol) protocol;
            }
        }

        assertNotNull(documentProtocol);

        RoutingTable table = new RoutingTable(documentProtocol.getRoutingTableSpec());

        HopBlueprint indexingHop = table.getHop("indexing");

        assertNotNull(indexingHop);

        assertEquals(1, indexingHop.getNumDirectives());
        assertTrue(indexingHop.getDirective(0) instanceof PolicyDirective);
        assertEquals("[DocumentRouteSelector]", indexingHop.getDirective(0).toString());
        //assertThat(indexingHop.getNumRecipients(), is(1));
        //assertThat(indexingHop.getRecipient(0).getServiceName(), is(searchClusterName));

        Route route = table.getRoute(searchClusterName);
        assertNotNull(route);

        assertEquals(1, route.getNumHops());
        Hop messageTypeHop = route.getHop(0);
        assertEquals(1, messageTypeHop.getNumDirectives());
        assertTrue(messageTypeHop.getDirective(0) instanceof PolicyDirective);
        assertEquals("[MessageType:" + searchClusterName + "]", messageTypeHop.getDirective(0).toString());
        PolicyDirective messageTypeDirective = (PolicyDirective) messageTypeHop.getDirective(0);
        assertEquals("MessageType", messageTypeDirective.getName());
        assertEquals(searchClusterName, messageTypeDirective.getParam());

        String indexingRouteName = DocumentProtocol.getIndexedRouteName(model.getContentClusters().get(searchClusterName).getConfigId());
        Route indexingRoute = table.getRoute(indexingRouteName);

        assertEquals(2, indexingRoute.getNumHops());
        assertEquals(indexingHopName, indexingRoute.getHop(0).getServiceName());
        assertNotNull(indexingRoute.getHop(1));
    }

    private void assertFeedingRouteIndexed(VespaModel model, String searchClusterName, String indexingHopName) {
        Routing routing = model.getRouting();
        List<Protocol> protocols = routing.getProtocols();

        DocumentProtocol documentProtocol = null;
        for (Protocol protocol : protocols) {
            if (protocol instanceof DocumentProtocol) {
                documentProtocol = (DocumentProtocol) protocol;
            }
        }

        assertNotNull(documentProtocol);

        RoutingTable table = new RoutingTable(documentProtocol.getRoutingTableSpec());

        Route indexingRoute = table.getRoute("searchcluster-index");
        assertEquals(2, indexingRoute.getNumHops());
        assertEquals(indexingHopName, indexingRoute.getHop(0).toString());
        assertEquals("[Content:cluster=" + searchClusterName + "]", indexingRoute.getHop(1).toString());
    }


    private String createVespaServices(String mainPre, String contentClusterPre, String contentClusterPost,
                                       String searchClusterPre, String searchClusterPost, String searchClusterPostPost,
                                       String mainPost, List<SearchClusterSpec> searchClusterSpecs) {
        StringBuilder retval = new StringBuilder();
        retval.append(mainPre);

        for (SearchClusterSpec searchClusterSpec : searchClusterSpecs) {
            retval.append(contentClusterPre).append(searchClusterSpec.name).append(contentClusterPost);
            retval.append(searchClusterPre);
            for (SearchDefSpec searchDefSpec : searchClusterSpec.searchDefs) {
                retval.append("        <document type='")
                        .append(searchDefSpec.typeName)
                        .append("' mode='")
                        .append("index")
                        .append("' />\n");
            }
            if (searchClusterSpec.indexingClusterName != null) {
                retval.append("        <document-processing cluster='").append(searchClusterSpec.indexingClusterName).append("'");
                if (searchClusterSpec.indexingChainName != null) {
                    retval.append(" chain='").append(searchClusterSpec.indexingChainName).append("'");
                }
                retval.append("/>\n");
            }
            retval.append(searchClusterPost);
            retval.append(searchClusterPostPost);
        }

        retval.append(mainPost);
        System.err.println(retval);
        return retval.toString();
    }

    private String createVespaServicesWithContent(List<DocprocClusterSpec> docprocClusterSpecs,
                                                  List<SearchClusterSpec> searchClusterSpecs) {
        String mainPre =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services version='1.0'>\n" +

                        "  <admin version='2.0'>\n" +
                        "    <adminserver hostalias='node0'/>\n" +
                        "  </admin>\n" +

                        "  <container version='1.0'>\n" +
                        "    <search/>\n" +
                        "    <nodes>\n" +
                        "      <node hostalias='node0'/>\n" +
                        "    </nodes>\n" +
                        "  </container>\n";
        int clusterNo = 0;
        for (DocprocClusterSpec docprocClusterSpec : docprocClusterSpecs) {
            String docprocCluster = "";
            docprocCluster += "  <container version='1.0' id='" + docprocClusterSpec.name + "'>\n";

            if (docprocClusterSpec.chains.size() > 0) {
                docprocCluster += "    <document-processing>\n";
                for (DocprocChainSpec chain : docprocClusterSpec.chains) {
                    if (chain.inherits.isEmpty()) {
                        docprocCluster += "      <chain id='" + chain.name + "'/>\n";
                    } else {
                        docprocCluster += "      <chain id='" + chain.name + "'";
                        docprocCluster += " inherits='";

                        for (String inherit : chain.inherits) {
                            docprocCluster += inherit + " ";
                        }

                        docprocCluster += "'/>\n";
                    }
                }
                docprocCluster += "    </document-processing>\n";
            } else {
                docprocCluster += "    <document-processing/>\n";
            }

            docprocCluster += "    <http>\n" +
                    "      <server id='" + docprocClusterSpec.name + "' port='" + (8000 + 10 * clusterNo) + "'/>\n" +
                    "    </http>\n";

            docprocCluster += "    <nodes>\n" +
                    "      <node hostalias='node0'/>\n" +
                    "    </nodes>\n" +
                    "  </container>\n";
            mainPre += docprocCluster;
            clusterNo++;
        }

        String contentClusterPre =
                "  <content version='1.0' id='";

        String contentClusterPost = "'>\n";
        String searchClusterPre =
                        "     <redundancy>1</redundancy>\n" +
                        "     <documents>\n";
        String searchClusterPost =
                "     </documents>\n" +
                        "     <group>\n" +
                        "       <node hostalias='node0' distribution-key='0' />\n" +
                        "     </group>\n";

        String searchClusterPostPost = "  </content>\n";

        String mainPost =
                "</services>\n";
        return createVespaServices(mainPre, contentClusterPre, contentClusterPost, searchClusterPre,
                searchClusterPost, searchClusterPostPost, mainPost, searchClusterSpecs);
    }

    private VespaModel getIndexedSearchVespaModel(String xml) {
        List<String> sds = generateSchemas("music", "album", "artist");
        return new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();
    }

    private VespaModel getIndexedContentVespaModel(List<DocprocClusterSpec> docprocClusterSpecs, List<SearchClusterSpec> searchClusterSpecs) {
        List<String> sds = new ArrayList<>();

        for (SearchClusterSpec cluster : searchClusterSpecs) {
            for (SearchDefSpec def : cluster.searchDefs) {
                sds.add(ApplicationPackageUtils.generateSchema(def.typeName, def.field1Name, def.field2Name));
            }
        }

        return new VespaModelCreatorWithMockPkg(getHosts(),
                createVespaServicesWithContent(docprocClusterSpecs, searchClusterSpecs), sds).create();
    }

    private static class SearchClusterSpec {

        private final String name;
        private final List<SearchDefSpec> searchDefs = new ArrayList<>(2);
        private final String indexingClusterName;
        private final String indexingChainName;

        private SearchClusterSpec(String name, String indexingClusterName, String indexingChainName) {
            this.name = name;
            this.indexingClusterName = indexingClusterName;
            this.indexingChainName = indexingChainName;
        }
    }

    private static class SearchDefSpec {

        private final String typeName;
        private final String field1Name;
        private final String field2Name;

        private SearchDefSpec(String typeName, String field1Name, String field2Name) {
            this.typeName = typeName;
            this.field1Name = field1Name;
            this.field2Name = field2Name;
        }
    }

    private class DocprocClusterSpec {

        private final String name;
        private final List<DocprocChainSpec> chains = new ArrayList<>();

        private DocprocClusterSpec(String name, DocprocChainSpec ... chains) {
            this.name = name;
            this.chains.addAll(Arrays.asList(chains));
        }
    }

    private static class DocprocChainSpec {

        private final String name;
        private final List<String> inherits = new ArrayList<>();

        private DocprocChainSpec(String name, String ... inherits) {
            this.name = name;
            this.inherits.addAll(Arrays.asList(inherits));
        }
    }

    public static String generateSchema(String name, String field1, String field2) {
        return "schema " + name + " {" +
               "  document " + name + " {" +
               "    field " + field1 + " type string {\n" +
               "      indexing: index | summary\n" +
               "      summary: dynamic\n" +
               "    }\n" +
               "    field " + field2 + " type int {\n" +
               "      indexing: attribute | summary\n" +
               "      attribute: fast-access\n" +
               "    }\n" +
               "    field " + field2 + "_nfa type int {\n" +
               "      indexing: attribute \n" +
               "    }\n" +
               "  }\n" +
               "  rank-profile staticrank inherits default {" +
               "    first-phase { expression: attribute(" + field2 + ") }" +
               "  }" +
               "  rank-profile summaryfeatures inherits default {" +
               "    first-phase { expression: attribute(" + field2 + ") }\n" +
               "    summary-features: attribute(" + field2 + ")" +
               "  }" +
               "  rank-profile inheritedsummaryfeatures inherits summaryfeatures {" +
               "  }" +
               "  rank-profile rankfeatures {" +
               "    first-phase { expression: attribute(" + field2 + ") }\n" +
               "    rank-features: attribute(" + field2 + ")" +
               "  }" +
               "  rank-profile inputs {" +
               "    inputs {" +
               "      query(foo) tensor<float>(x[10])" +
               "      query(bar) tensor(key{},x[1000])" +
               "    }" +
               "  }" +
               "}";
    }

    public static List<String> generateSchemas(String ... sdNames) {
        return generateSchemas(Arrays.asList(sdNames));
    }

    public static List<String> generateSchemas(List<String> sdNames) {
        List<String> sds = new ArrayList<>();
        int i = 0;
        for (String sdName : sdNames) {
            sds.add(generateSchema(sdName, "f" + (i + 1), "f" + (i + 2)));
            i = i + 2;
        }
        return sds;
    }

}
