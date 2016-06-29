// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster.dispatchprototype;

import com.google.common.annotations.Beta;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.search.LegacyEmulationConfig;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.prelude.cluster.QrMonitorConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.config.dispatchprototype.SearchNodesConfig;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;
import com.yahoo.vespa.config.search.DispatchConfig;

import static com.yahoo.container.QrSearchersConfig.Searchcluster;

/**
 * This class modifies ClusterSearcher behavior to talk directly to search nodes instead of dispatchers.
 *
 * This means that queries are sent to a single search node only. Obviously, this will not give correct
 * results - it is just a single step towards eliminating top-level dispatch as a separate process.
 *
 * @author bakksjo
 */
@Beta
@After("*")
public class DispatchClusterSearcher extends Searcher {
    private final ClusterSearcher clusterSearcher;

    public DispatchClusterSearcher(
            final ComponentId id,
            final SearchNodesConfig searchNodesConfig,
            final QrSearchersConfig qrsConfig,
            final ClusterConfig clusterConfig,
            final DocumentdbInfoConfig documentDbConfig,
            final LegacyEmulationConfig emulationConfig,
            final QrMonitorConfig monitorConfig,
            final DispatchConfig dispatchConfig,
            final Statistics manager,
            final FS4ResourcePool listeners,
            final ComponentRegistry<ClusterSearcher> otherClusterSearchers,
            final VipStatus vipStatus) {

        clusterSearcher = new ClusterSearcher(
                id,
                makeQrSearchersConfigWithSearchNodesInsteadOfDispatcherNodes(
                        qrsConfig,
                        searchNodesConfig,
                        clusterConfig.clusterName()),
                clusterConfig,
                documentDbConfig,
                emulationConfig,
                monitorConfig,
                dispatchConfig,
                manager,
                listeners,
                vipStatus);

        //Prevent the ClusterSearcher(s) implicitly set up by the model from warning that it can't contact
        //the c++ TLD when we disable it in the system test.
        otherClusterSearchers.allComponents().stream()
                .forEach(ClusterSearcher::deconstruct);
    }


    @Override
    public Result search(Query query, Execution execution) {
        return clusterSearcher.search(query, execution);
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        clusterSearcher.fill(result, summaryClass, execution);
    }

    private static QrSearchersConfig makeQrSearchersConfigWithSearchNodesInsteadOfDispatcherNodes(
            final QrSearchersConfig qrsConfig,
            final SearchNodesConfig searchNodesConfig,
            final String clusterName) {
        final QrSearchersConfig.Builder qrSearchersConfigBuilder = new QrSearchersConfig.Builder();
        copyEverythingExceptSearchclusters(qrsConfig, qrSearchersConfigBuilder);

        // We only "copy" (with modifications) a single Searchcluster.
        final Searchcluster originalSearchcluster = getSearchclusterByName(qrsConfig, clusterName);
        final Searchcluster.Builder searchclusterBuilder = new Searchcluster.Builder();
        copyEverythingExceptDispatchers(originalSearchcluster, searchclusterBuilder);
        // Here comes the trick: Substitute search nodes for dispatchers.
        for (final SearchNodesConfig.Search_node searchNodeConfig : searchNodesConfig.search_node()) {
            searchclusterBuilder.dispatcher(
                    new Searchcluster.Dispatcher.Builder()
                            .host(searchNodeConfig.host())
                            .port(searchNodeConfig.port()));
        }
        qrSearchersConfigBuilder.searchcluster(searchclusterBuilder);

        return new QrSearchersConfig(qrSearchersConfigBuilder);
    }

    private static void copyEverythingExceptSearchclusters(
            final QrSearchersConfig source,
            final QrSearchersConfig.Builder destination) {
        destination.tag(new QrSearchersConfig.Tag.Builder(source.tag()));
        destination.com(new QrSearchersConfig.Com.Builder(source.com()));
        destination.customizedsearchers(new QrSearchersConfig.Customizedsearchers.Builder(source.customizedsearchers()));
        for (final QrSearchersConfig.External external : source.external()) {
            destination.external(new QrSearchersConfig.External.Builder(external));
        }
    }

    private static Searchcluster getSearchclusterByName(final QrSearchersConfig qrsConfig, final String clusterName) {
        return qrsConfig.searchcluster().stream()
                .filter(cluster -> clusterName.equals(cluster.name()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("No cluster found with name " + clusterName));
    }

    private static void copyEverythingExceptDispatchers(
            final Searchcluster source,
            final Searchcluster.Builder destination) {
        destination
                .name(source.name())
                .searchdef(source.searchdef())
                .rankprofiles(new Searchcluster.Rankprofiles.Builder(source.rankprofiles()))
                .indexingmode(source.indexingmode())
                // Deliberately excluding storagecluster here because it's not relevant.
                .rowbits(source.rowbits());
    }
}
