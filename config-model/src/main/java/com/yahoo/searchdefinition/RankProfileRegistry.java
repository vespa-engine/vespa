// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDDocumentType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final Map<String, Map<String, RankProfile>> rankProfiles = new LinkedHashMap<>();
    private final String MAGIC_GLOBAL_RANKPROFILES = "[MAGIC_GLOBAL_RANKPROFILES]";

    /* These rank profiles can be overridden: 'default' rank profile, as that is documented to work. And 'unranked'. */
    static final Set<String> overridableRankProfileNames = new HashSet<>(Arrays.asList("default", "unranked"));

    public static RankProfileRegistry createRankProfileRegistryWithBuiltinRankProfiles(Search search) {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        rankProfileRegistry.add(new DefaultRankProfile(search, rankProfileRegistry, search.rankingConstants()));
        rankProfileRegistry.add(new UnrankedRankProfile(search, rankProfileRegistry, search.rankingConstants()));
        return rankProfileRegistry;
    }

    private String extractName(ImmutableSearch search) {
        return search != null ? search.getName() : MAGIC_GLOBAL_RANKPROFILES;
    }

    /**
     * Adds a rank profile to this registry
     *
     * @param rankProfile the rank profile to add
     */
    public void add(RankProfile rankProfile) {
        String searchName = extractName(rankProfile.getSearch());
        if ( ! rankProfiles.containsKey(searchName)) {
            rankProfiles.put(searchName, new LinkedHashMap<>());
        }
        checkForDuplicate(rankProfile);
        rankProfiles.get(searchName).put(rankProfile.getName(), rankProfile);
    }

    private void checkForDuplicate(RankProfile rankProfile) {
        String rankProfileName = rankProfile.getName();
        RankProfile existingRankProfileWithSameName = rankProfiles.get(extractName(rankProfile.getSearch())).get(rankProfileName);
        if (existingRankProfileWithSameName == null) return;

        if ( ! overridableRankProfileNames.contains(rankProfileName)) {
            throw new IllegalArgumentException("Duplicate rank profile '" + rankProfileName + "' in " +
                                               rankProfile.getSearch());
        }
    }

    /**
     * Returns a named rank profile, null if the search definition doesn't have one with the given name
     *
     * @param search the {@link Search} that owns the rank profile.
     * @param name the name of the rank profile
     * @return the RankProfile to return.
     */
    public RankProfile get(String search, String name) {
        Map<String, RankProfile> profiles = rankProfiles.get(search);
        if (profiles == null) return null;
        return profiles.get(name);
    }
    public RankProfile get(ImmutableSearch search, String name) {
        return get(search.getName(), name);
    }

    public RankProfile getGlobal(String name) {
        Map<String, RankProfile> profiles = rankProfiles.get(MAGIC_GLOBAL_RANKPROFILES);
        if (profiles == null) return null;
        return profiles.get(name);
    }

    public RankProfile resolve(SDDocumentType docType, String name) {
        RankProfile rankProfile = get(docType.getName(), name);
        if (rankProfile != null) return rankProfile;
        for (var parent : docType.getInheritedTypes()) {
            RankProfile parentProfile = resolve(parent, name);
            if (parentProfile != null) return parentProfile;
        }
        return get(MAGIC_GLOBAL_RANKPROFILES, name);
    }

    /**
     * Rank profiles that are collected across clusters.
     * @return A set of global {@link RankProfile} instances.
     */
    public Collection<RankProfile> all() {
        List<RankProfile> all = new ArrayList<>();
        for (var entry : rankProfiles.values()) {
            all.addAll(entry.values());
        }
        return all;
    }

    /**
     * Returns the rank profiles of a given search definition.
     *
     * @param search the searchdefinition to get rank profiles for
     * @return a collection of {@link RankProfile} instances
     */
    public Collection<RankProfile> rankProfilesOf(String search) {
        Map<String, RankProfile> mapping = rankProfiles.get(search);
        if (mapping == null) {
            return Collections.emptyList();
        }
        return mapping.values();
    }

    /**
     * Retrieve all rank profiles for a search definition
     * @param search search definition to fetch rank profiles for, or null for the global ones
     * @return Collection of RankProfiles
     */
    public Collection<RankProfile> rankProfilesOf(ImmutableSearch search) {
        return rankProfilesOf(extractName(search));
    }

}
