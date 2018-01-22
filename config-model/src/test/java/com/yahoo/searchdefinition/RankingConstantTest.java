// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static com.yahoo.config.model.test.TestUtil.joinLines;

/**
 * @author gjoranv
 */
public class RankingConstantTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void tensor_constant_properties_are_set() throws Exception {
        final String TENSOR_NAME = "my_global_tensor";
        final String TENSOR_FILE = "path/my-tensor-file.json";
        final String TENSOR_TYPE = "tensor(x{})";
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  rank-profile my_rank_profile {",
                "    first-phase {",
                "      expression: sum(constant(my_global_tensor))",
                "    }",
                "  }",
                "  constant " + TENSOR_NAME + " {",
                "    file: " + TENSOR_FILE,
                "    type: " + TENSOR_TYPE,
                "  }",
                "}"
        ));
        searchBuilder.build();
        Search search = searchBuilder.getSearch();

        Iterator<RankingConstant> constantIterator = search.getRankingConstants().values().iterator();
        RankingConstant constant = constantIterator.next();
        assertEquals(TENSOR_NAME, constant.getName());
        assertEquals(TENSOR_FILE, constant.getFileName());
        assertEquals(TENSOR_TYPE, constant.getType());
        assertEquals(RankingConstant.PathType.FILE, constant.getPathType());

        assertFalse(constantIterator.hasNext());
    }

    @Test
    public void tensor_constant_must_have_a_type() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("must have a type");
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  constant foo {",
                "    file: bar.baz",
                "  }",
                "}"
        ));
    }

    @Test
    public void tensor_constant_must_have_a_file() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("must have a file");
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x[])",
                "  }",
                "}"
        ));
    }

    @Test
    public void constant_file_does_not_need_path_or_ending() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    file: simplename",
                "  }",
                "}"
        ));
        searchBuilder.build();
        Search search = searchBuilder.getSearch();
        RankingConstant constant = search.getRankingConstants().values().iterator().next();
        assertEquals("simplename", constant.getFileName());
    }

    @Test
    public void constant_uri_is_allowed() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: http://somwhere.far.away/in/another-galaxy",
                "  }",
                "}"
        ));
        searchBuilder.build();
        Search search = searchBuilder.getSearch();
        RankingConstant constant = search.getRankingConstants().values().iterator().next();
        assertEquals(RankingConstant.PathType.URI, constant.getPathType());
        assertEquals("http://somwhere.far.away/in/another-galaxy", constant.getUri());
    }
    @Test
    public void constant_uri_with_port_is_allowed() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: http://somwhere.far.away:4080/in/another-galaxy",
                "  }",
                "}"
        ));
        searchBuilder.build();
        Search search = searchBuilder.getSearch();
        RankingConstant constant = search.getRankingConstants().values().iterator().next();
        assertEquals(RankingConstant.PathType.URI, constant.getPathType());
        assertEquals("http://somwhere.far.away:4080/in/another-galaxy", constant.getUri());
    }
    @Test
    public void constant_uri_no_dual_slashes_is_allowed() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: http:somwhere.far.away/in/another-galaxy",
                "  }",
                "}"
        ));
        searchBuilder.build();
        Search search = searchBuilder.getSearch();
        RankingConstant constant = search.getRankingConstants().values().iterator().next();
        assertEquals(RankingConstant.PathType.URI, constant.getPathType());
        assertEquals("http:somwhere.far.away/in/another-galaxy", constant.getUri());
    }
    @Test
    public void constant_uri_only_supports_http() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
        thrown.expect(ParseException.class);
        thrown.expectMessage("Encountered \" <IDENTIFIER> \"ftp \"\" at line 5, column 10.\n" +
                "Was expecting:\n" +
                "    <URI_PATH> ...");
        searchBuilder.importString(joinLines(
                "search test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: ftp:somwhere.far.away/in/another-galaxy",
                "  }",
                "}"
        ));
    }

}
