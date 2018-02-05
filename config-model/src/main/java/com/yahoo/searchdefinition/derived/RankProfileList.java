// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.Search;
import java.util.Map;

/**
 * The derived rank profiles of a search definition
 *
 * @author  bratseth
 */
public class RankProfileList extends Derived implements RankProfilesConfig.Producer {

    private Map<String, RawRankProfile> rankProfiles = new java.util.LinkedHashMap<>();

    /**
     * Creates a rank profile
     *
     * @param search the search definition this is a rank profile from
     * @param attributeFields the attribute fields to create a ranking for
     */
    public RankProfileList(Search search,
                           AttributeFields attributeFields,
                           RankProfileRegistry rankProfileRegistry,
                           QueryProfileRegistry queryProfiles) {
        setName(search.getName());
        deriveRankProfiles(rankProfileRegistry, queryProfiles, search, attributeFields);
    }

    private void deriveRankProfiles(RankProfileRegistry rankProfileRegistry,
                                    QueryProfileRegistry queryProfiles,
                                    Search search,
                                    AttributeFields attributeFields) {
        RawRankProfile defaultProfile = new RawRankProfile(rankProfileRegistry.getRankProfile(search, "default"),
                                                           queryProfiles,
                                                           attributeFields);
        rankProfiles.put(defaultProfile.getName(), defaultProfile);

        for (RankProfile rank : rankProfileRegistry.localRankProfiles(search)) {
            if ("default".equals(rank.getName())) continue;

            RawRankProfile rawRank = new RawRankProfile(rank, queryProfiles, attributeFields);
            rankProfiles.put(rawRank.getName(), rawRank);
        }
    }

    public Map<String, RawRankProfile> getRankProfiles() {
        return rankProfiles;
    }

    /** Returns the raw rank profile with the given name, or null if it is not present */
    public RawRankProfile getRankProfile(String name) {
        return rankProfiles.get(name);
    }

    @Override
    public String getDerivedName() { return "rank-profiles"; }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        for (RawRankProfile rank : rankProfiles.values() ) {
            rank.getConfig(builder);
        }
    }
}
