// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.collections.Pair;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.*;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RankingExpressionsTestCase extends SearchDefinitionTestCase {

    @Test
    public void testMacros() throws IOException, ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        Search search = SearchBuilder.createFromDirectory("src/test/examples/rankingexpressionfunction",
                                                          rankProfileRegistry,
                                                          new QueryProfileRegistry()).getSearch();
        final RankProfile macrosRankProfile = rankProfileRegistry.getRankProfile(search, "macros");
        macrosRankProfile.parseExpressions();
        final Map<String, RankProfile.Macro> macros = macrosRankProfile.getMacros();
        assertEquals(2, macros.get("titlematch$").getFormalParams().size());
        assertEquals("var1", macros.get("titlematch$").getFormalParams().get(0));
        assertEquals("var2", macros.get("titlematch$").getFormalParams().get(1));
        assertEquals("var1 * var2 + 890", macros.get("titlematch$").getTextualExpression().trim());
        assertEquals("var1 * var2 + 890", macros.get("titlematch$").getRankingExpression().getRoot().toString());
        assertEquals("0.8+0.2*titlematch$(4,5)+0.8*titlematch$(7,8)*closeness(distance)", macrosRankProfile.getFirstPhaseRankingString().trim());
        assertEquals("78 + closeness(distance)", macros.get("artistmatch").getTextualExpression().trim());
        assertEquals(0, macros.get("artistmatch").getFormalParams().size());

        List<Pair<String, String>> rankProperties = new RawRankProfile(macrosRankProfile,
                                                                       new QueryProfileRegistry(),
                                                                       new AttributeFields(search)).configProperties();
        assertEquals(6, rankProperties.size());

        assertEquals("rankingExpression(titlematch$).rankingScript", rankProperties.get(0).getFirst());
        assertEquals("var1 * var2 + 890", rankProperties.get(0).getSecond());

        assertEquals("rankingExpression(artistmatch).rankingScript", rankProperties.get(1).getFirst());
        assertEquals("78 + closeness(distance)", rankProperties.get(1).getSecond());

        assertEquals("rankingExpression(firstphase).rankingScript", rankProperties.get(5).getFirst());
        assertEquals("0.8 + 0.2 * rankingExpression(titlematch$@126063073eb2deb.ab95cd69909927c) + 0.8 * rankingExpression(titlematch$@c7e4c2d0e6d9f2a1.1d4ed08e56cce2e6) * closeness(distance)", rankProperties.get(5).getSecond());

        assertEquals("rankingExpression(titlematch$@c7e4c2d0e6d9f2a1.1d4ed08e56cce2e6).rankingScript", rankProperties.get(3).getFirst());
        assertEquals("7 * 8 + 890", rankProperties.get(3).getSecond());

        assertEquals("rankingExpression(titlematch$@126063073eb2deb.ab95cd69909927c).rankingScript", rankProperties.get(2).getFirst());
        assertEquals("4 * 5 + 890", rankProperties.get(2).getSecond());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThatIncludingFileInSubdirFails() throws IOException, ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        Search search = SearchBuilder.createFromDirectory("src/test/examples/rankingexpressioninfile",
                                                          registry,
                                                          new QueryProfileRegistry()).getSearch();
        new DerivedConfiguration(search, registry, new QueryProfileRegistry()); // rank profile parsing happens during deriving
    }

}
