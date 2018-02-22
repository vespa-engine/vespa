// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.expressiontransforms.ExpressionTransforms;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mapping from name to {@link RankProfile} as well as a reverse mapping of {@link RankProfile} to {@link Search}.
 * Having both of these mappings consolidated here make it easier to remove dependencies on these mappings at
 * run time, since it is essentially only used when building rank profile config at deployment time.
 *
 * TODO: Reconsider the difference between local and global maps. Right now, the local maps might better be
 *       served from a different class owned by SearchBuilder.
 *
 * @author Ulf Lilleengen
 */
public class RankProfileRegistry {

    private final Map<RankProfile, Search> rankProfileToSearch = new LinkedHashMap<>();
    private final Map<Search, Map<String, RankProfile>> rankProfiles = new LinkedHashMap<>();

    private final ExpressionTransforms expressionTransforms = new ExpressionTransforms();

    /* These rank profiles can be overridden: 'default' rank profile, as that is documented to work. And 'unranked'. */
    static final Set<String> overridableRankProfileNames = new HashSet<>(Arrays.asList("default", "unranked"));

    public RankProfileRegistry() {
    }

    public static RankProfileRegistry createRankProfileRegistryWithBuiltinRankProfiles(Search search) {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        rankProfileRegistry.addRankProfile(new DefaultRankProfile(search, rankProfileRegistry));
        rankProfileRegistry.addRankProfile(new UnrankedRankProfile(search, rankProfileRegistry));
        return rankProfileRegistry;
    }

    /**
     * Adds a rank profile to this registry
     *
     * @param rankProfile the rank profile to add
     */
    public void addRankProfile(RankProfile rankProfile) {
        if (!rankProfiles.containsKey(rankProfile.getSearch())) {
            rankProfiles.put(rankProfile.getSearch(), new LinkedHashMap<>());
        }
        checkForDuplicateRankProfile(rankProfile);
        rankProfiles.get(rankProfile.getSearch()).put(rankProfile.getName(), rankProfile);
        rankProfileToSearch.put(rankProfile, rankProfile.getSearch());
    }

    private void checkForDuplicateRankProfile(RankProfile rankProfile) {
        final String rankProfileName = rankProfile.getName();
        RankProfile existingRangProfileWithSameName = rankProfiles.get(rankProfile.getSearch()).get(rankProfileName);
        if (existingRangProfileWithSameName == null) return;

        if (!overridableRankProfileNames.contains(rankProfileName)) {
            throw new IllegalArgumentException("Cannot add rank profile '" + rankProfileName + "' in search definition '"
                    + rankProfile.getSearch().getName() + "', since it already exists");
        }
    }

    /**
     * Returns a named rank profile, null if the search definition doesn't have one with the given name
     *
     * @param search The {@link Search} that owns the rank profile.
     * @param name The name of the rank profile
     * @return The RankProfile to return.
     */
    public RankProfile getRankProfile(Search search, String name) {
        return rankProfiles.get(search).get(name);
    }

    /**
     * Rank profiles that are collected across clusters.
     * @return A set of global {@link RankProfile} instances.
     */
    public Set<RankProfile> allRankProfiles() {
        return rankProfileToSearch.keySet();
    }

    /**
     * Rank profiles that are collected for a given search definition
     * @param search {@link Search} to get rank profiles for.
     * @return A collection of local {@link RankProfile} instances.
     */
    public Collection<RankProfile> localRankProfiles(Search search) {
        Map<String, RankProfile> mapping = rankProfiles.get(search);
        if (mapping == null) {
            return Collections.emptyList();
        }
        return mapping.values();
    }

    /**
     * Returns the ranking expression transforms that should be performed on all expressions in this.
     * This instance (and hence the instances of individual transformers) has the lifetime of a single
     * application package deployment.
     */
    public ExpressionTransforms expressionTransforms() { return expressionTransforms; }

}
