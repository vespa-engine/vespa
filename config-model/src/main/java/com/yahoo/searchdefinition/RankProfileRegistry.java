// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

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
 * Global rank profiles are represented by the Search key null.
 *
 * @author Ulf Lilleengen
 */
public class RankProfileRegistry {

    private final Map<RankProfile, ImmutableSearch> rankProfileToSearch = new LinkedHashMap<>();
    private final Map<ImmutableSearch, Map<String, RankProfile>> rankProfiles = new LinkedHashMap<>();

    /* These rank profiles can be overridden: 'default' rank profile, as that is documented to work. And 'unranked'. */
    static final Set<String> overridableRankProfileNames = new HashSet<>(Arrays.asList("default", "unranked"));

    public static RankProfileRegistry createRankProfileRegistryWithBuiltinRankProfiles(Search search) {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        rankProfileRegistry.add(new DefaultRankProfile(search, rankProfileRegistry));
        rankProfileRegistry.add(new UnrankedRankProfile(search, rankProfileRegistry));
        return rankProfileRegistry;
    }

    /**
     * Adds a rank profile to this registry
     *
     * @param rankProfile the rank profile to add
     */
    public void add(RankProfile rankProfile) {
        if ( ! rankProfiles.containsKey(rankProfile.getSearch())) {
            rankProfiles.put(rankProfile.getSearch(), new LinkedHashMap<>());
        }
        checkForDuplicate(rankProfile);
        rankProfiles.get(rankProfile.getSearch()).put(rankProfile.getName(), rankProfile);
        rankProfileToSearch.put(rankProfile, rankProfile.getSearch());
    }

    private void checkForDuplicate(RankProfile rankProfile) {
        String rankProfileName = rankProfile.getName();
        RankProfile existingRangProfileWithSameName = rankProfiles.get(rankProfile.getSearch()).get(rankProfileName);
        if (existingRangProfileWithSameName == null) return;

        if ( ! overridableRankProfileNames.contains(rankProfileName)) {
            throw new IllegalArgumentException("Cannot add rank profile '" + rankProfileName + "' in search definition '"
                                               + rankProfile.getSearch().getName() + "', since it already exists");
        }
    }

    /**
     * Returns a named rank profile, null if the search definition doesn't have one with the given name
     *
     * @param search the {@link Search} that owns the rank profile.
     * @param name the name of the rank profile
     * @return the RankProfile to return.
     */
    public RankProfile get(ImmutableSearch search, String name) {
        Map<String, RankProfile> profiles = rankProfiles.get(search);
        if (profiles == null) return null;
        return profiles.get(name);
    }

    /**
     * Rank profiles that are collected across clusters.
     * @return A set of global {@link RankProfile} instances.
     */
    public Set<RankProfile> all() {
        return rankProfileToSearch.keySet();
    }

    /**
     * Returns the rank profiles of a given search definition.
     *
     * @param search {@link Search} to get rank profiles for
     * @return a collection of {@link RankProfile} instances
     */
    public Collection<RankProfile> rankProfilesOf(ImmutableSearch search) {
        Map<String, RankProfile> mapping = rankProfiles.get(search);
        if (mapping == null) {
            return Collections.emptyList();
        }
        return mapping.values();
    }

}
