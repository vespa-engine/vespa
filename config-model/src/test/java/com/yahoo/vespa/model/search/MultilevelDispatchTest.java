// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.model.test.TestRoot;
import com.yahoo.vespa.config.search.core.PartitionsConfig;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.content.DispatchSpec;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.search.utils.DispatchUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createClusterXml;
import static com.yahoo.vespa.model.search.utils.DispatchUtils.getDataset;
import static com.yahoo.vespa.model.search.utils.DispatchUtils.getFdispatchrcConfig;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Unit tests for multi-level dispatchers in an indexed content cluster.
 *
 * @author geirst
 */
public class MultilevelDispatchTest {

    private static class EngineAsserter {
        private List<PartitionsConfig.Dataset.Engine> engines;
        private int engineIdx = 0;
        public EngineAsserter(int numParts, int numEngines, Dispatch dispatch) {
           PartitionsConfig.Dataset dataset = getDataset(dispatch);
           assertEquals(numParts, dataset.numparts());
           assertEquals(PartitionsConfig.Dataset.Querydistribution.AUTOMATIC, dataset.querydistribution());
           engines = dataset.engine();
           assertEquals(numEngines, engines.size());
        }
        EngineAsserter assertEngine(int rowId, int partitionId, String connectSpec) {
            DispatchUtils.assertEngine(rowId, partitionId, connectSpec, engines.get(engineIdx++));
            return this;
        }
    }

    private String getGroupXml() {
         return "  <group>\n" +
                "    <node distribution-key='10' hostalias='mh0'/>\n" +
                "    <node distribution-key='11' hostalias='mh1'/>\n" +
                "    <node distribution-key='12' hostalias='mh2'/>\n" +
                "    <node distribution-key='13' hostalias='mh3'/>\n" +
                "    <node distribution-key='14' hostalias='mh4'/>\n" +
                "    <node distribution-key='15' hostalias='mh5'/>\n" +
                "  </group>\n";
    }

    private String getSimpleDispatchXml() {
        return  "  <dispatch>\n" +
                "    <num-dispatch-groups>2</num-dispatch-groups>\n" +
                "  </dispatch>\n";
    }

    private String getDispatchXml() {
        return "  <dispatch>\n" +
               "    <group>\n" +
               "      <node distribution-key='10'/>\n" +
               "      <node distribution-key='12'/>\n" +
               "      <node distribution-key='14'/>\n" +
               "    </group>\n" +
               "    <group>\n" +
               "      <node distribution-key='11'/>\n" +
               "      <node distribution-key='13'/>\n" +
               "      <node distribution-key='15'/>\n" +
               "    </group>\n" +
               "  </dispatch>\n";
    }

    private ContentCluster createCluster(String dispatchXml) throws Exception {
        String[] hosts = {"mh0", "mh1", "mh2", "mh3", "mh4", "mh5"};
        MockRoot root = ContentClusterUtils.createMockRoot(hosts);
        ContentCluster cluster = ContentClusterUtils.createCluster(createClusterXml(getGroupXml(), Optional.of(dispatchXml), 1, 1), root);

        AbstractConfigProducer<Dispatch> dispatchParent = new SimpleConfigProducer<>(root, "tlds");
        HostResource hostResource = new HostResource(new Host(root, "mockhost"));
        IndexedSearchCluster index = cluster.getSearch().getIndexed();
        index.addTld(dispatchParent, hostResource);
        index.setupDispatchGroups();

        root.freezeModelTopology();
        cluster.validate();
        return cluster;
    }

    private List<Dispatch> getDispatchers(Dispatch tld) {
        DispatchGroup group = tld.getDispatchGroup();
        List<Dispatch> dispatchers = new ArrayList<>();
        for (SearchInterface dispatch : group.getSearchersIterable()) {
            dispatchers.add((Dispatch)dispatch);
        }
        return dispatchers;
    }

    private void assertDispatchAndSearchNodes(int partId, Dispatch[] dispatchers, String[] connectSpecs, SearchNode[] searchNodes) {
        assertEquals(dispatchers.length, connectSpecs.length);
        assertEquals(connectSpecs.length, searchNodes.length);
        int searchNodeIdx = 0;
        for (int rowId = 0; rowId < dispatchers.length; ++rowId) {
            assertDispatchAndSearchNodes(rowId, partId, searchNodes[searchNodeIdx++].getDistributionKey(),
                    dispatchers[rowId], connectSpecs, searchNodes);
        }
    }

    private void assertDispatchAndSearchNodes(int expRowId, int expPartId, int expDistributionKey, Dispatch dispatch, String[] connectSpecs, SearchNode[] searchNodes) {
        assertEquals(expRowId, dispatch.getNodeSpec().groupIndex());
        assertEquals(expPartId, dispatch.getNodeSpec().partitionId());
        assertEquals("mycluster/search/cluster.mycluster/dispatchers/dispatch." + expDistributionKey, dispatch.getConfigId());
        assertEquals(expPartId, getFdispatchrcConfig(dispatch).partition());
        assertEquals(1, getFdispatchrcConfig(dispatch).dispatchlevel());

        int numEngines = connectSpecs.length;
        EngineAsserter ea = new EngineAsserter(numEngines, numEngines, dispatch);
        for (int i = 0; i < numEngines; ++i) {
            ea.assertEngine(0, i, connectSpecs[i]);
            assertEquals(i, searchNodes[i].getNodeSpec().partitionId());
        }
    }

    @Test
    public void requireThatDispatchGroupsCanBeAutomaticallySetup() throws Exception {
        ContentCluster cr = createCluster(getSimpleDispatchXml());
        IndexedSearchCluster ix = cr.getSearch().getIndexed();
        Dispatch tld = cr.getSearch().getIndexed().getTLDs().get(0);

        assertEquals("tlds/tld.0", tld.getConfigId());
        assertEquals(0, getFdispatchrcConfig(tld).dispatchlevel());
        new EngineAsserter(2, 6, tld).
                assertEngine(0, 0, "tcp/mh0:19113").
                assertEngine(1, 0, "tcp/mh1:19113").
                assertEngine(2, 0, "tcp/mh2:19113").
                assertEngine(0, 1, "tcp/mh3:19113").
                assertEngine(1, 1, "tcp/mh4:19113").
                assertEngine(2, 1, "tcp/mh5:19113");

        List<Dispatch> ds = getDispatchers(tld);
        assertEquals(6, ds.size());
        { // dispatch group 1
            Dispatch[] dispatchers = {ds.get(0), ds.get(1), ds.get(2)};
            String[] specs = {"tcp/mh0:19104", "tcp/mh1:19104", "tcp/mh2:19104"};
            SearchNode[] searchNodes = {ix.getSearchNode(0), ix.getSearchNode(1), ix.getSearchNode(2)};
            assertDispatchAndSearchNodes(0, dispatchers, specs, searchNodes);
        }
        { // dispatch group 2
            Dispatch[] dispatchers = {ds.get(3), ds.get(4), ds.get(5)};
            String[] specs = {"tcp/mh3:19104", "tcp/mh4:19104", "tcp/mh5:19104"};
            SearchNode[] searchNodes = {ix.getSearchNode(3), ix.getSearchNode(4), ix.getSearchNode(5)};
            assertDispatchAndSearchNodes(1, dispatchers, specs, searchNodes);
        }
    }

    @Test
    public void requireThatMaxHitsIsScaled() throws Exception {
        ContentCluster cr = createCluster(getSimpleDispatchXml() + getMaxhitsTuning());
        IndexedSearchCluster ix = cr.getSearch().getIndexed();
        Dispatch tld = cr.getSearch().getIndexed().getTLDs().get(0);
        PartitionsConfig.Builder builder = new PartitionsConfig.Builder();
        tld.getConfig(builder);
        PartitionsConfig config = new PartitionsConfig(builder);
        assertThat(config.dataset().size(), is(1));
        assertThat(config.dataset(0).maxhitspernode(), is(300));
        for (Dispatch dispatch : getDispatchers(tld)) {
            PartitionsConfig.Builder b = new PartitionsConfig.Builder();
            dispatch.getConfig(b);
            PartitionsConfig c= new PartitionsConfig(b);
            assertThat(c.dataset().size(), is(1));
            assertThat(c.dataset(0).maxhitspernode(), is(100));
        }
    }

    private String getMaxhitsTuning() {
        return "<tuning>" +
               "  <dispatch>" +
               "    <max-hits-per-partition>100</max-hits-per-partition>" +
               "  </dispatch>" +
                "</tuning>";
    }


    @Test
    public void requireThatSearchCoverageIsSetInMultilevelSetup() throws Exception {
        ContentCluster cr = createCluster(getSimpleDispatchXml() + getCoverage());
        Dispatch tld = cr.getSearch().getIndexed().getTLDs().get(0);
        PartitionsConfig.Builder builder = new PartitionsConfig.Builder();
        tld.getConfig(builder);
        PartitionsConfig config = new PartitionsConfig(builder);
        assertThat(config.dataset().size(), is(1));
        assertEquals(95.0, config.dataset(0).minimal_searchcoverage(), 0.1);
        for (Dispatch dispatch : getDispatchers(tld)) {
            PartitionsConfig.Builder b = new PartitionsConfig.Builder();
            dispatch.getConfig(b);
            PartitionsConfig c= new PartitionsConfig(b);
            assertThat(c.dataset().size(), is(1));
            assertEquals(95.0, c.dataset(0).minimal_searchcoverage(), 0.1);
        }
    }

    @Test
    public void requireThatSearchCoverageIsSetInSingleLevelSetup() throws Exception {
        TestRoot root = new TestDriver(true).buildModel(new MockApplicationPackage.Builder()
                                                                .withServices("<services version='1.0'>" +
                                                                                      "<content id='stateful' version='1.0'>" +
                                                                                      "  <redundancy>1</redundancy>" +
                                                                                      "  <documents><document mode='index' type='music' /></documents>" +
                                                                                      "  <nodes>" +
                                                                                      "    <node distribution-key='1' hostalias='mockroot' />" +
                                                                                      "  </nodes>" +
                                                                                      "  <search><coverage><minimum>0.95</minimum></coverage></search>" +
                                                                                      "</content>" +
                                                                                      "<jdisc id='foo' version='1.0'>" +
                                                                                      "  <search />" +
                                                                                      "  <nodes><node hostalias='mockroot' /></nodes>" +
                                                                                      "</jdisc>" +
                                                                                      "</services>")
                                                                .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
                                                                .build());
        PartitionsConfig config = root.getConfig(PartitionsConfig.class, "stateful/search/cluster.stateful/tlds/foo.0.tld.0");
        assertThat(config.dataset().size(), is(1));
        assertEquals(95.0, config.dataset(0).minimal_searchcoverage(), 0.1);
    }

    private String getCoverage() {
        return "<search>" +
                "  <coverage>" +
                "    <minimum>0.95</minimum>" +
                "  </coverage>" +
                "</search>";
    }

    @Test
    public void requireThatDispatchGroupsCanBeExplicitlySpecified() throws Exception {
        ContentCluster cr = createCluster(getDispatchXml());
        IndexedSearchCluster ix = cr.getSearch().getIndexed();
        Dispatch tld = cr.getSearch().getIndexed().getTLDs().get(0);

        assertEquals("tlds/tld.0", tld.getConfigId());
        assertEquals(0, getFdispatchrcConfig(tld).dispatchlevel());
        new EngineAsserter(2, 6, tld).
                assertEngine(0, 0, "tcp/mh0:19113").
                assertEngine(1, 0, "tcp/mh2:19113").
                assertEngine(2, 0, "tcp/mh4:19113").
                assertEngine(0, 1, "tcp/mh1:19113").
                assertEngine(1, 1, "tcp/mh3:19113").
                assertEngine(2, 1, "tcp/mh5:19113");

        List<Dispatch> ds = getDispatchers(tld);
        assertEquals(6, ds.size());
        { // dispatch group 1
            Dispatch[] dispatchers = {ds.get(0), ds.get(1), ds.get(2)};
            String[] specs = {"tcp/mh0:19104", "tcp/mh2:19104", "tcp/mh4:19104"};
            SearchNode[] searchNodes = {ix.getSearchNode(0), ix.getSearchNode(2), ix.getSearchNode(4)};
            assertDispatchAndSearchNodes(0, dispatchers, specs, searchNodes);
        }
        { // dispatch group 2
            Dispatch[] dispatchers = {ds.get(3), ds.get(4), ds.get(5)};
            String[] specs = {"tcp/mh1:19104", "tcp/mh3:19104", "tcp/mh5:19104"};
            SearchNode[] searchNodes = {ix.getSearchNode(1), ix.getSearchNode(3), ix.getSearchNode(5)};
            assertDispatchAndSearchNodes(1, dispatchers, specs, searchNodes);
        }
    }

    @Test
    public void requireThatUnevenDispatchGroupsCanBeCreated() {
        List<SearchNode> searchNodes = createSearchNodes(5);
        List<DispatchSpec.Group> groups = DispatchGroupBuilder.createDispatchGroups(searchNodes, 3);
        assertEquals(3, groups.size());
        assertGroup(new int[]{0, 1}, groups.get(0));
        assertGroup(new int[]{2, 3}, groups.get(1));
        assertGroup(new int[]{4}, groups.get(2));
    }

    private List<SearchNode> createSearchNodes(int numNodes) {
        List<SearchNode> searchNodes = new ArrayList<>();
        MockRoot root = new MockRoot("");
        for (int i = 0; i < numNodes; ++i) {
            searchNodes.add(SearchNode.create(root, "mynode" + i, i, new NodeSpec(0, i), "mycluster", null, false, Optional.empty()));
        }
        return searchNodes;
    }

    private void assertGroup(int[] nodes, DispatchSpec.Group group) {
        assertEquals(nodes.length, group.getNodes().size());
        for (int i = 0; i < nodes.length; ++i) {
            assertEquals(nodes[i], group.getNodes().get(i).getDistributionKey());
        }
    }

    private ContentCluster createIllegalSetupWithMultipleNodeReferences() throws Exception {
        String dispatchXml = "  <dispatch>\n" +
               "    <group>\n" +
               "      <node distribution-key='10'/>\n" +
               "      <node distribution-key='11'/>\n" +
               "      <node distribution-key='12'/>\n" +
               "    </group>\n" +
               "    <group>\n" +
               "      <node distribution-key='12'/>\n" +
               "      <node distribution-key='13'/>\n" +
               "      <node distribution-key='14'/>\n" +
               "    </group>\n" +
               "  </dispatch>\n";
        return createCluster(dispatchXml);
    }

    private ContentCluster createIllegalSetupWithMissingNodeReferences() throws Exception {
        String dispatchXml = "  <dispatch>\n" +
               "    <group>\n" +
               "      <node distribution-key='10'/>\n" +
               "      <node distribution-key='11'/>\n" +
               "    </group>\n" +
               "    <group>\n" +
               "      <node distribution-key='13'/>\n" +
               "      <node distribution-key='14'/>\n" +
               "    </group>\n" +
               "  </dispatch>\n";
        return createCluster(dispatchXml);
    }

    private ContentCluster createIllegalSetupWithIllegalNodeReference() throws Exception {
        String dispatchXml = "  <dispatch>\n" +
               "    <group>\n" +
               "      <node distribution-key='10'/>\n" +
               "      <node distribution-key='11'/>\n" +
               "      <node distribution-key='12'/>\n" +
               "    </group>\n" +
               "    <group>\n" +
               "      <node distribution-key='13'/>\n" +
               "      <node distribution-key='14'/>\n" +
               "      <node distribution-key='15'/>\n" +
               "      <node distribution-key='19'/>\n" +
               "    </group>\n" +
               "  </dispatch>\n";
        return createCluster(dispatchXml);
    }

    @Test
    public void requireThatWeReferenceNodesOnlyOnceWhenSettingUpDispatchGroups() {
        try {
            createIllegalSetupWithMultipleNodeReferences();
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("node with distribution key '12' is referenced multiple times"));
        }
    }

    @Test
    public void requireThatWeReferenceAllNodesWhenSettingUpDispatchGroups() {
        try {
            createIllegalSetupWithMissingNodeReferences();
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("2 node(s) with distribution keys [12, 15] are not referenced"));
        }
    }

    @Test
    public void requireThatWeReferenceValidNodesWhenSettingUpDispatchGroups() throws Exception {
        try {
            createIllegalSetupWithIllegalNodeReference();
            assertFalse("Did not get expected Exception", true);
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("node with distribution key '19' does not exists"));
        }
    }

}
