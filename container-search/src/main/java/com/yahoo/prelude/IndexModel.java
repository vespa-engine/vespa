// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.yahoo.log.LogLevel;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.container.QrSearchersConfig;

/**
 * Parameter class used for construction IndexFacts.
 *
 * @author Steinar Knutsen
 */
public final class IndexModel {

    private static final Logger log = Logger.getLogger(IndexModel.class.getName());

    // Copied from MasterClustersInfoUpdater. It's a temporary workaround for IndexFacts.
    private Map<String, List<String>> masterClusters;
    private Map<String, SearchDefinition> searchDefinitions;
    private SearchDefinition unionSearchDefinition;

    /**
     * Use IndexModel as a pure wrapper for the parameters given.
     */
    public IndexModel(Map<String, List<String>> masterClusters,
                      Map<String, SearchDefinition> searchDefinitions,
                      SearchDefinition unionSearchDefinition) {
        this.masterClusters = masterClusters;
        this.searchDefinitions = searchDefinitions;
        this.unionSearchDefinition = unionSearchDefinition;
    }

    public IndexModel(IndexInfoConfig indexInfo, Map<String, List<String>> clusters) {
        if (indexInfo != null) {
            setDefinitions(indexInfo);
        } else {
            searchDefinitions = null;
            unionSearchDefinition = null;
        }
        this.masterClusters = clusters;
    }

    public IndexModel(IndexInfoConfig indexInfo, QrSearchersConfig clusters) {
        if (indexInfo != null) {
            setDefinitions(indexInfo);
        } else {
            searchDefinitions = null;
            unionSearchDefinition = null;
        }
        if (clusters != null) {
            setMasterClusters(clusters);
        } else {
            masterClusters = null;
        }
    }

    private void setMasterClusters(QrSearchersConfig config) {
        masterClusters = new HashMap<>();
        for (int i = 0; i < config.searchcluster().size(); ++i) {
            List<String> docTypes = new ArrayList<>();
            String clusterName = config.searchcluster(i).name();
            for (int j = 0; j < config.searchcluster(i).searchdef().size(); ++j) {
                docTypes.add(config.searchcluster(i).searchdef(j));
            }
            masterClusters.put(clusterName, docTypes);
        }
    }

    @SuppressWarnings("deprecation")
    private void setDefinitions(IndexInfoConfig c) {
        searchDefinitions = new HashMap<>();
        unionSearchDefinition = new SearchDefinition(IndexFacts.unionName);

        for (Iterator<IndexInfoConfig.Indexinfo> i = c.indexinfo().iterator(); i.hasNext();) {
            IndexInfoConfig.Indexinfo info = i.next();

            SearchDefinition sd = new SearchDefinition(info.name());

            for (Iterator<IndexInfoConfig.Indexinfo.Command> j = info.command().iterator(); j.hasNext();) {
                IndexInfoConfig.Indexinfo.Command command = j.next();
                sd.addCommand(command.indexname(),command.command());
                unionSearchDefinition.addCommand(command.indexname(),command.command());
            }

            sd.fillMatchGroups();
            searchDefinitions.put(info.name(), sd);
        }
        unionSearchDefinition.fillMatchGroups();

        for (IndexInfoConfig.Indexinfo info : c.indexinfo()) {

            SearchDefinition sd = searchDefinitions.get(info.name());

            for (IndexInfoConfig.Indexinfo.Alias alias : info.alias()) {
                String aliasString = alias.alias();
                String indexString = alias.indexname();

                sd.addAlias(aliasString, indexString);
                try {
                    unionSearchDefinition.addAlias(aliasString, indexString);
                } catch (RuntimeException e) {
                    log.log(LogLevel.WARNING,
                            "Ignored the alias \""
                                    + aliasString
                                    + "\" for \""
                                    + indexString
                                    + "\" in the union of all search definitions,"
                                    + " source has to be explicitly set to \""
                                    + sd.getName()
                                    + "\" for that alias to work.", e);
                }
            }
        }
    }

    public Map<String, List<String>> getMasterClusters() {
        return masterClusters;
    }

    public Map<String, SearchDefinition> getSearchDefinitions() {
        return searchDefinitions;
    }

    public SearchDefinition getUnionSearchDefinition() {
        return unionSearchDefinition;
    }

}
