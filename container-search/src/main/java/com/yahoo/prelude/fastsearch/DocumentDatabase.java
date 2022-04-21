// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.config.RankProfile;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Representation of a document database realizing a schema in a content cluster.
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

    public DocumentDatabase(DocumentdbInfoConfig.Documentdb documentDb) {
        this(documentDb.name(), new DocsumDefinitionSet(documentDb), toRankProfiles(documentDb.rankprofile()));
    }

    public DocumentDatabase(String name, DocsumDefinitionSet docsumDefinitionSet, Collection<RankProfile> rankProfiles) {
        this.name = name;
        this.docsumDefSet = docsumDefinitionSet;
        this.rankProfiles = Map.copyOf(rankProfiles.stream().collect(Collectors.toMap(RankProfile::name, p -> p)));
    }

    public String getName() {
        return name;
    }

    public DocsumDefinitionSet getDocsumDefinitionSet() {
        return docsumDefSet;
    }

    /** Returns an unmodifiable map of all the rank profiles in this indexed by rank profile name */
    public Map<String, RankProfile> rankProfiles() { return rankProfiles; }

    private static Collection<RankProfile> toRankProfiles(Collection<DocumentdbInfoConfig.Documentdb.Rankprofile> rankProfileConfigList) {
        List<RankProfile> rankProfiles = new ArrayList<>();
        for (var profileConfig : rankProfileConfigList) {
            var builder = new RankProfile.Builder(profileConfig.name());
            builder.setHasSummaryFeatures(profileConfig.hasSummaryFeatures());
            builder.setHasRankFeatures(profileConfig.hasRankFeatures());
            for (var inputConfig : profileConfig.input())
                builder.addInput(inputConfig.name(), TensorType.fromSpec(inputConfig.type()));
            rankProfiles.add(builder.build());
        }
        return rankProfiles;
    }

}
