// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDDocumentType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mapping from name to {@link RankProfile} as well as a reverse mapping of {@link RankProfile} to {@link Schema}.
 * Having both of these mappings consolidated here make it easier to remove dependencies on these mappings at
 * run time, since it is essentially only used when building rank profile config at deployment time.
 *
 * Global rank profiles are represented by the Search key null.
 *
 * @author Ulf Lilleengen
 */
public class RankProfileRegistry {

    private final Map<String, Map<String, RankProfile>> rankProfiles = new LinkedHashMap<>();
    private static final String globalRankProfilesKey = "[global]";

    /* These rank profiles can be overridden: 'default' rank profile, as that is documented to work. And 'unranked'. */
    static final Set<String> overridableRankProfileNames = new HashSet<>(Arrays.asList("default", "unranked"));

    public static RankProfileRegistry createRankProfileRegistryWithBuiltinRankProfiles(Schema schema) {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        rankProfileRegistry.add(new DefaultRankProfile(schema, rankProfileRegistry));
        rankProfileRegistry.add(new UnrankedRankProfile(schema, rankProfileRegistry));
        return rankProfileRegistry;
    }

    private String extractName(ImmutableSchema search) {
        return search != null ? search.getName() : globalRankProfilesKey;
    }

    /** Adds a rank profile to this registry */
    public void add(RankProfile rankProfile) {
        String schemaName = extractName(rankProfile.schema());
        if ( ! rankProfiles.containsKey(schemaName)) {
            rankProfiles.put(schemaName, new LinkedHashMap<>());
        }
        checkForDuplicate(rankProfile);
        rankProfiles.get(schemaName).put(rankProfile.name(), rankProfile);
    }

    private void checkForDuplicate(RankProfile rankProfile) {
        String rankProfileName = rankProfile.name();
        RankProfile existingRankProfileWithSameName = rankProfiles.get(extractName(rankProfile.schema())).get(rankProfileName);
        if (existingRankProfileWithSameName == null) return;

        if ( ! overridableRankProfileNames.contains(rankProfileName)) {
            throw new IllegalArgumentException("Duplicate rank profile '" + rankProfileName + "' in " +
                                               rankProfile.schema());
        }
    }

    /**
     * Returns a named rank profile, null if the search definition doesn't have one with the given name
     *
     * @param schema the {@link Schema} that owns the rank profile
     * @param name the name of the rank profile
     * @return the RankProfile to return.
     */
    public RankProfile get(String schema, String name) {
        Map<String, RankProfile> profiles = rankProfiles.get(schema);
        if (profiles == null) return null;
        return profiles.get(name);
    }

    public RankProfile get(ImmutableSchema schema, String name) {
        var profile = get(schema.getName(), name);
        if (profile != null) return profile;
        if (schema.inherited().isPresent()) return get(schema.inherited().get(), name);
        return null;
    }

    public RankProfile getGlobal(String name) {
        Map<String, RankProfile> profiles = rankProfiles.get(globalRankProfilesKey);
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
        return get(globalRankProfilesKey, name);
    }

    /**
     * Rank profiles that are collected across clusters.
     *
     * @return a set of global {@link RankProfile} instances
     */
    public Collection<RankProfile> all() {
        List<RankProfile> all = new ArrayList<>();
        for (var entry : rankProfiles.values()) {
            all.addAll(entry.values());
        }
        return all;
    }

    /**
     * Retrieve all rank profiles for a schema
     *
     * @param schema the schema to fetch rank profiles for, or null for the global ones
     * @return a collection of {@link RankProfile} instances
     */
    public Collection<RankProfile> rankProfilesOf(ImmutableSchema schema) {
        String key = schema == null ? globalRankProfilesKey : schema.getName();

        if ( ! rankProfiles.containsKey(key)) return List.of();

        var profiles = new LinkedHashMap<>(rankProfiles.get(key));
        // Add all profiles in inherited schemas, unless they are already present (overridden)
        while (schema != null && schema.inherited().isPresent()) {
            schema = schema.inherited().get();
            var inheritedProfiles = rankProfiles.get(schema.getName());
            if (inheritedProfiles != null) {
                for (Map.Entry<String, RankProfile> inheritedProfile : inheritedProfiles.entrySet()) {
                    profiles.putIfAbsent(inheritedProfile.getKey(), inheritedProfile.getValue());
                }
            }
        }
        return profiles.values();
    }

}
