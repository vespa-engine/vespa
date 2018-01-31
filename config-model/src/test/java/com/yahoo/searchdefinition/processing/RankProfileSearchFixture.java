// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Helper class for setting up and asserting over a Search instance with a rank profile given literally
 * in the search definition language.
 *
 * @author geirst
 */
class RankProfileSearchFixture {

    private RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
    private Search search;

    RankProfileSearchFixture(String rankProfiles) throws ParseException {
        this(MockApplicationPackage.createEmpty(), rankProfiles);
    }

    RankProfileSearchFixture(ApplicationPackage applicationpackage, String rankProfiles) throws ParseException {
        this(applicationpackage, rankProfiles, null, null);
    }

    RankProfileSearchFixture(ApplicationPackage applicationpackage,
                             String rankProfiles, String constant, String field)
            throws ParseException {
        SearchBuilder builder = new SearchBuilder(applicationpackage, rankProfileRegistry);
        String sdContent = "search test {\n" +
                           "  " + (constant != null ? constant : "") + "\n" +
                           "  document test {\n" +
                           "    " + (field != null ? field : "") + "\n" +
                           "  }\n" +
                           rankProfiles +
                           "\n" +
                           "}";
        builder.importString(sdContent);
        builder.build();
        search = builder.getSearch();
    }

    public void assertFirstPhaseExpression(String expExpression, String rankProfile) {
        assertEquals(expExpression, rankProfile(rankProfile).getFirstPhaseRanking().getRoot().toString());
    }

    public void assertSecondPhaseExpression(String expExpression, String rankProfile) {
        assertEquals(expExpression, rankProfile(rankProfile).getSecondPhaseRanking().getRoot().toString());
    }

    public void assertRankProperty(String expValue, String name, String rankProfile) {
        List<RankProfile.RankProperty> rankPropertyList = rankProfile(rankProfile).getRankPropertyMap().get(name);
        assertEquals(1, rankPropertyList.size());
        assertEquals(expValue, rankPropertyList.get(0).getValue());
    }

    public void assertMacro(String expExpression, String macroName, String rankProfile) {
        assertEquals(expExpression, rankProfile(rankProfile).getMacros().get(macroName).getRankingExpression().getRoot().toString());
    }

    public RankProfile rankProfile(String rankProfile) {
        return rankProfileRegistry.getRankProfile(search, rankProfile).compile();
    }

    public Search search() { return search; }

}
