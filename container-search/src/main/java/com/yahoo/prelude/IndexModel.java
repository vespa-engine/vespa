// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.container.QrSearchersConfig;

/**
 * Parameter class used for construction IndexFacts.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public final class IndexModel {

    private static final Logger log = Logger.getLogger(IndexModel.class.getName());

    private final Map<String, List<String>> masterClusters;
    private final Map<String, SearchDefinition> searchDefinitions;
    private final SearchDefinition unionSearchDefinition;

    /** Create an index model for a single search definition */
    public IndexModel(SearchDefinition searchDefinition) {
        this(Collections.emptyMap(), Collections.singleton(searchDefinition));
    }

    public IndexModel(Map<String, List<String>> masterClusters, Collection<SearchDefinition> searchDefinitions) {
        this.masterClusters = masterClusters;
        this.searchDefinitions = new LinkedHashMap<>();
        this.unionSearchDefinition = unionOf(searchDefinitions);
        for (var sd : searchDefinitions) {
            this.searchDefinitions.put(sd.getName(), sd);
        }
    }

    public IndexModel(IndexInfoConfig indexInfo, QrSearchersConfig clusters) {
        this(indexInfo, toClusters(clusters));
    }

    public IndexModel(IndexInfoConfig indexInfo, Map<String, List<String>> clusters) {
        if (indexInfo != null) {
            searchDefinitions = toSearchDefinitions(indexInfo);
            unionSearchDefinition = unionOf(searchDefinitions.values());
        } else {
            searchDefinitions = null;
            unionSearchDefinition = null;
        }
        this.masterClusters = clusters;
    }

    private static Map<String, List<String>> toClusters(QrSearchersConfig config) {
        if (config == null) return new HashMap<>();

        Map<String, List<String>> clusters = new HashMap<>();
        for (int i = 0; i < config.searchcluster().size(); ++i) {
            List<String> docTypes = new ArrayList<>();
            String clusterName = config.searchcluster(i).name();
            for (int j = 0; j < config.searchcluster(i).searchdef().size(); ++j) {
                docTypes.add(config.searchcluster(i).searchdef(j));
            }
            clusters.put(clusterName, docTypes);
        }
        return clusters;
    }

    private static Map<String, SearchDefinition> toSearchDefinitions(IndexInfoConfig c) {
        Map<String, SearchDefinition> searchDefinitions = new HashMap<>();

        for (Iterator<IndexInfoConfig.Indexinfo> i = c.indexinfo().iterator(); i.hasNext();) {
            IndexInfoConfig.Indexinfo info = i.next();
            SearchDefinition sd = new SearchDefinition(info.name());
            for (Iterator<IndexInfoConfig.Indexinfo.Command> j = info.command().iterator(); j.hasNext();) {
                IndexInfoConfig.Indexinfo.Command command = j.next();
                sd.addCommand(command.indexname(),command.command());
            }
            searchDefinitions.put(info.name(), sd);
        }

        for (IndexInfoConfig.Indexinfo info : c.indexinfo()) {
            SearchDefinition sd = searchDefinitions.get(info.name());
            for (IndexInfoConfig.Indexinfo.Alias alias : info.alias()) {
                String aliasString = alias.alias();
                String indexString = alias.indexname();

                sd.addAlias(aliasString, indexString);
            }
        }
        return searchDefinitions;
    }

    private SearchDefinition unionOf(Collection<SearchDefinition> searchDefinitions) {
        SearchDefinition union = new SearchDefinition(IndexFacts.unionName);

        for (SearchDefinition sd : searchDefinitions) {
            for (Index index : sd.indices().values()) {
                union.getOrCreateIndex(index.getName());
                for (String command : index.allCommands())
                    union.addCommand(index.getName(), command);
                for (String alias : index.aliases()) {
                    try {
                        union.addAlias(alias, index.getName());
                    }
                    catch (IllegalArgumentException e) {
                        log.fine("Conflicting alias '" + alias  + " of " + index + " in " + sd +
                                 " will not take effect for queries which does not specify that search definition");
                    }
                }
            }

        }
        return union;
    }

    public Map<String, List<String>> getMasterClusters() { return masterClusters; }

    public Map<String, SearchDefinition> getSearchDefinitions() { return searchDefinitions; }

    SearchDefinition getUnionSearchDefinition() { return unionSearchDefinition; }

}
