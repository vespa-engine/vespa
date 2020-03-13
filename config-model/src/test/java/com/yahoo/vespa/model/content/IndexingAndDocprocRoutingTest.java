// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.HopBlueprint;
import com.yahoo.messagebus.routing.PolicyDirective;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingTable;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.routing.DocumentProtocol;
import com.yahoo.vespa.model.routing.Protocol;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class IndexingAndDocprocRoutingTest extends ContentBaseTest {
    @Test
    public void oneContentOneDoctypeImplicitIndexingClusterImplicitIndexingChain()
            throws IOException, SAXException, ParseException {
        final String CLUSTERNAME = "musiccluster";
        SearchClusterSpec searchCluster = new SearchClusterSpec(CLUSTERNAME, null, null);
        searchCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));
        VespaModel model = getIndexedContentVespaModel(Collections.<DocprocClusterSpec>emptyList(), Arrays.asList(searchCluster));
        assertIndexing(model, new DocprocClusterSpec("container", new DocprocChainSpec("container/chain.indexing")));
        assertFeedingRoute(model, CLUSTERNAME, "container/chain.indexing");
    }

    @Test
    public void oneContentTwoDoctypesImplicitIndexingClusterImplicitIndexingChain()
            throws IOException, SAXException, ParseException {
        final String CLUSTERNAME = "musicandbookscluster";
        SearchClusterSpec searchCluster = new SearchClusterSpec(CLUSTERNAME, null, null);
        searchCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));
        searchCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));
        VespaModel model = getIndexedContentVespaModel(Collections.<DocprocClusterSpec>emptyList(), Arrays.asList(searchCluster));
        assertIndexing(model, new DocprocClusterSpec("container", new DocprocChainSpec("container/chain.indexing")));
        assertFeedingRoute(model, CLUSTERNAME, "container/chain.indexing");
    }

    @Test
    public void twoContentTwoDoctypesImplicitIndexingClusterImplicitIndexingChain()
            throws IOException, SAXException, ParseException {
        final String MUSIC = "musiccluster";
        SearchClusterSpec musicCluster = new SearchClusterSpec(MUSIC, null, null);
        musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

        final String BOOKS = "bookscluster";
        SearchClusterSpec booksCluster = new SearchClusterSpec(BOOKS, null, null);
        booksCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));

        VespaModel model = getIndexedContentVespaModel(Collections.<DocprocClusterSpec>emptyList(), Arrays.asList(musicCluster, booksCluster));

        assertIndexing(model,
                new DocprocClusterSpec("container", new DocprocChainSpec("container/chain.indexing")));

        assertFeedingRoute(model, MUSIC, "container/chain.indexing");
        assertFeedingRoute(model, BOOKS, "container/chain.indexing");
    }


    @Test
    public void oneContentOneDoctypeExplicitIndexingClusterImplicitIndexingChain()
            throws IOException, SAXException, ParseException {
        final String CLUSTERNAME = "musiccluster";
        SearchClusterSpec searchCluster = new SearchClusterSpec(CLUSTERNAME, "dpcluster", null);
        searchCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));
        VespaModel model = getIndexedContentVespaModel(Arrays.asList(new DocprocClusterSpec("dpcluster")), Arrays.asList(searchCluster));
        assertIndexing(model, new DocprocClusterSpec("dpcluster", new DocprocChainSpec("dpcluster/chain.indexing")));
        assertFeedingRoute(model, CLUSTERNAME, "dpcluster/chain.indexing");
    }

    @Test
    public void oneSearchOneDoctypeExplicitIndexingClusterExplicitIndexingChain()
            throws IOException, SAXException, ParseException {
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
    public void twoContentTwoDoctypesExplicitIndexingInSameIndexingCluster()
            throws IOException, SAXException, ParseException {
        final String MUSIC = "musiccluster";
        SearchClusterSpec musicCluster = new SearchClusterSpec(MUSIC, "dpcluster", null);
        musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

        final String BOOKS = "bookscluster";
        SearchClusterSpec booksCluster = new SearchClusterSpec(BOOKS, "dpcluster", null);
        booksCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));

        VespaModel model = getIndexedContentVespaModel(Arrays.asList(new DocprocClusterSpec("dpcluster")),
                                                       Arrays.asList(musicCluster, booksCluster));

        assertIndexing(model, new DocprocClusterSpec("dpcluster", new DocprocChainSpec("dpcluster/chain.indexing")));
        assertFeedingRoute(model, MUSIC, "dpcluster/chain.indexing");
        assertFeedingRoute(model, BOOKS, "dpcluster/chain.indexing");
    }

    @Test
    public void noContentClustersOneDocprocCluster() {
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
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(),
                services, sds).create();
        assertIndexing(model, new DocprocClusterSpec("dokprok"));
    }

    @Test
    public void twoContentTwoDoctypesExplicitIndexingInDifferentIndexingClustersExplicitChain()
            throws IOException, SAXException, ParseException {
        final String MUSIC = "musiccluster";
        SearchClusterSpec musicCluster = new SearchClusterSpec(MUSIC, "dpmusiccluster", "dpmusicchain");
        musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

        final String BOOKS = "bookscluster";
        SearchClusterSpec booksCluster = new SearchClusterSpec(BOOKS, "dpbookscluster", "dpbookschain");
        booksCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));

        DocprocClusterSpec dpMusicCluster = new DocprocClusterSpec("dpmusiccluster", new DocprocChainSpec("dpmusicchain", "indexing"));
        DocprocClusterSpec dpBooksCluster = new DocprocClusterSpec("dpbookscluster", new DocprocChainSpec("dpbookschain", "indexing"));
        VespaModel model = getIndexedContentVespaModel(Arrays.asList(
                dpMusicCluster,
                dpBooksCluster),
                                                       Arrays.asList(
                                                               musicCluster,
                                                               booksCluster));

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

    @Test(expected = IllegalArgumentException.class)
    public void twoContentTwoDoctypesExplicitIndexingInDifferentIndexingClustersExplicitChainIncorrectInheritance()
            throws IOException, SAXException, ParseException {
        final String MUSIC = "musiccluster";
        SearchClusterSpec musicCluster = new SearchClusterSpec(MUSIC, "dpmusiccluster", "dpmusicchain");
        musicCluster.searchDefs.add(new SearchDefSpec("music", "artist", "album"));

        final String BOOKS = "bookscluster";
        SearchClusterSpec booksCluster = new SearchClusterSpec(BOOKS, "dpbookscluster", "dpbookschain");
        booksCluster.searchDefs.add(new SearchDefSpec("book", "author", "title"));

        DocprocClusterSpec dpMusicCluster = new DocprocClusterSpec("dpmusiccluster", new DocprocChainSpec("dpmusicchain"));
        DocprocClusterSpec dpBooksCluster = new DocprocClusterSpec("dpbookscluster", new DocprocChainSpec("dpbookschain"));
        VespaModel model = getIndexedContentVespaModel(Arrays.asList(
                dpMusicCluster,
                dpBooksCluster),
                                                       Arrays.asList(
                                                               musicCluster,
                                                               booksCluster));

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

    private void assertIndexing(VespaModel model, DocprocClusterSpec... expectedDocprocClusters) {
        Map<String, ContainerCluster> docprocClusters = getDocprocClusters(model);
        assertThat(docprocClusters.size(), is(expectedDocprocClusters.length));

        for (DocprocClusterSpec expectedDocprocCluster : expectedDocprocClusters) {
            ContainerCluster docprocCluster = docprocClusters.get(expectedDocprocCluster.name);
            assertThat(docprocCluster, not(nullValue()));
            assertThat(docprocCluster.getName(), is(expectedDocprocCluster.name));
            ContainerDocproc containerDocproc = docprocCluster.getDocproc();
            assertThat(containerDocproc, not(nullValue()));
            List<DocprocChain> chains = containerDocproc.getChains().allChains().allComponents();
            assertThat(chains.size(), is(expectedDocprocCluster.chains.size()));
            List<String> actualDocprocChains = new ArrayList<>();
            for (DocprocChain chain : chains) {
                actualDocprocChains.add(chain.getServiceName());
            }
            List<String> expectedDocprocChainStrings = new ArrayList<>();
            for (DocprocChainSpec spec : expectedDocprocCluster.chains) {
                expectedDocprocChainStrings.add(spec.name);
            }

            assertThat(actualDocprocChains, hasItems(expectedDocprocChainStrings.toArray(new String[0])));
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

        assertThat(indexingHop, not(nullValue()));

        assertThat(indexingHop.getNumDirectives(), is(1));
        assertThat(indexingHop.getDirective(0), instanceOf(PolicyDirective.class));
        assertThat(indexingHop.getDirective(0).toString(), is("[DocumentRouteSelector]"));
        //assertThat(indexingHop.getNumRecipients(), is(1));
        //assertThat(indexingHop.getRecipient(0).getServiceName(), is(searchClusterName));

        Route route = table.getRoute(searchClusterName);
        assertNotNull(route);

        assertThat(route.getNumHops(), is(1));
        Hop messageTypeHop = route.getHop(0);
        assertThat(messageTypeHop.getNumDirectives(), is(1));
        assertThat(messageTypeHop.getDirective(0), instanceOf(PolicyDirective.class));
        assertThat(messageTypeHop.getDirective(0).toString(), is("[MessageType:" + searchClusterName + "]"));
        PolicyDirective messageTypeDirective = (PolicyDirective) messageTypeHop.getDirective(0);
        assertThat(messageTypeDirective.getName(), is("MessageType"));
        assertThat(messageTypeDirective.getParam(), is(searchClusterName));

        String indexingRouteName = DocumentProtocol.getIndexedRouteName(model.getContentClusters().get(searchClusterName).getConfigId());
        Route indexingRoute = table.getRoute(indexingRouteName);

        assertThat(indexingRoute.getNumHops(), is(2));
        assertThat(indexingRoute.getHop(0).getServiceName(), is(indexingHopName));
        assertThat(indexingRoute.getHop(1), not(nullValue()));
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
        assertThat(indexingRoute.getNumHops(), is(2));
        assertThat(indexingRoute.getHop(0).toString(), is(indexingHopName));
        assertThat(indexingRoute.getHop(1).toString(), is("[Content:cluster=" + searchClusterName + "]"));
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

    private String createVespaServicesWithContent(List<DocprocClusterSpec> docprocClusterSpecs, List<SearchClusterSpec> searchClusterSpecs) {
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

            if (docprocClusterSpec.chains != null && docprocClusterSpec.chains.size() > 0) {
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
        List<String> sds = ApplicationPackageUtils.generateSchemas("music", "album", "artist");
        return new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();
    }

    private VespaModel getIndexedContentVespaModel(List<DocprocClusterSpec> docprocClusterSpecs, List<SearchClusterSpec> searchClusterSpecs) {
        List<String> sds = new ArrayList<>();

        for (SearchClusterSpec cluster : searchClusterSpecs) {
            for (SearchDefSpec def : cluster.searchDefs) {
                sds.add(ApplicationPackageUtils.generateSearchDefinition(def.typeName, def.field1Name, def.field2Name));
            }
        }

        return new VespaModelCreatorWithMockPkg(getHosts(),
                createVespaServicesWithContent(docprocClusterSpecs, searchClusterSpecs), sds).create();
    }

    private class SearchClusterSpec {
        private final String name;
        private List<SearchDefSpec> searchDefs = new ArrayList<>(2);
        private String indexingClusterName;
        private String indexingChainName;

        private SearchClusterSpec(String name, String indexingClusterName, String indexingChainName) {
            this.name = name;
            this.indexingClusterName = indexingClusterName;
            this.indexingChainName = indexingChainName;
        }
    }

    private class SearchDefSpec {
        private String typeName;
        private String field1Name;
        private String field2Name;

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

    private class DocprocChainSpec {
        private final String name;
        private final List<String> inherits = new ArrayList<>();

        private DocprocChainSpec(String name, String ... inherits) {
            this.name = name;
            this.inherits.addAll(Arrays.asList(inherits));
        }
    }
}
