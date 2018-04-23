// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.container.search.LegacyEmulationConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private final ImmutableMap<String, RankProfile> rankProfiles;

    public DocumentDatabase(DocumentdbInfoConfig.Documentdb documentDb, LegacyEmulationConfig emulConfig) {
        this(documentDb.name(), new DocsumDefinitionSet(documentDb, emulConfig), toRankProfiles(documentDb.rankprofile()));
    }

    public DocumentDatabase(String name, DocsumDefinitionSet docsumDefinitionSet, Collection<RankProfile> rankProfiles) {
        this.name = name;
        this.docsumDefSet = docsumDefinitionSet;
        this.rankProfiles = ImmutableMap.copyOf(rankProfiles.stream().collect(Collectors.toMap(RankProfile::getName, p -> p)));
    }

    public String getName() {
        return name;
    }

    public DocsumDefinitionSet getDocsumDefinitionSet() {
        return docsumDefSet;
    }

    /** Returns an unmodifiable map of all the rank profiles in this indexed by rank profile name */
    public Map<String, RankProfile> rankProfiles() { return rankProfiles; }

    private static ImmutableMap<String, RankProfile> toMap(Collection<RankProfile> rankProfiles) {
        return ImmutableMap.copyOf(rankProfiles.stream().collect(Collectors.toMap(RankProfile::getName, p -> p)));
    }

    private static Collection<RankProfile> toRankProfiles(Collection<DocumentdbInfoConfig.Documentdb.Rankprofile> rankProfileConfigList) {
        List<RankProfile> rankProfiles = new ArrayList<>();
        for (DocumentdbInfoConfig.Documentdb.Rankprofile c : rankProfileConfigList)
            rankProfiles.add(new RankProfile(c.name(), c.hasSummaryFeatures(), c.hasRankFeatures()));
        return rankProfiles;
    }

}
