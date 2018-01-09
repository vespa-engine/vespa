// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;


import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

/**
 * A low-cost ranking profile to use for watcher queries etc.
 *
 * @author Vegard Havdal
 */
public class UnrankedRankProfile extends RankProfile {

    public UnrankedRankProfile(Search search, RankProfileRegistry rankProfileRegistry) {
        super("unranked", search, rankProfileRegistry);
        try {
            RankingExpression exp = new RankingExpression("value(0)");
            this.setFirstPhaseRanking(exp);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse the ranking expression 'value(0)' when setting up " +
                                               "the 'unranked' rank profile");
        }
        this.setIgnoreDefaultRankFeatures(true);
        this.setKeepRankCount(0);
        this.setRerankCount(0);
    }

}
