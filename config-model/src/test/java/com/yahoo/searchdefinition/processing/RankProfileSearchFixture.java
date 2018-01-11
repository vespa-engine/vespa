package com.yahoo.searchdefinition.processing;

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
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        String sdContent = "search test {\n" +
                           "  document test {\n" +
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
