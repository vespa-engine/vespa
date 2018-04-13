// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.container.search.LegacyEmulationConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of a back-end document database.
 *
 * @author geirst
 */
public class DocumentDatabase {

    // TODO: What about name conflicts when different search defs have the same rank profile/docsum?

    public static final String MATCH_PROPERTY = "match";
    public static final String SEARCH_DOC_TYPE_KEY = "documentdb.searchdoctype";

    private final String name;
    private final DocsumDefinitionSet docsumDefSet;

    private final Map<String, RankProfile> rankProfiles;

    public DocumentDatabase(DocumentdbInfoConfig.Documentdb documentDb, LegacyEmulationConfig emulConfig) {
        this.name = documentDb.name();
        this.docsumDefSet = new DocsumDefinitionSet(documentDb, emulConfig);
        this.rankProfiles = ImmutableMap.copyOf(toRankProfiles(documentDb.rankprofile()));
    }

    public String getName() {
        return name;
    }

    public DocsumDefinitionSet getDocsumDefinitionSet() {
        return docsumDefSet;
    }

    /** Returns an unmodifiable map of all the rank profiles in this indexed by rank profile name */
    public Map<String, RankProfile> rankProfiles() { return rankProfiles; }

    private Map<String, RankProfile> toRankProfiles(List<DocumentdbInfoConfig.Documentdb.Rankprofile> rankProfileConfigList) {
        Map<String, RankProfile> rankProfiles = new HashMap<>();
        for (DocumentdbInfoConfig.Documentdb.Rankprofile c : rankProfileConfigList) {
            rankProfiles.put(c.name(), new RankProfile(c.name(), c.hasSummaryFeatures(), c.hasRankFeatures()));
        }
        return rankProfiles;
    }

}
