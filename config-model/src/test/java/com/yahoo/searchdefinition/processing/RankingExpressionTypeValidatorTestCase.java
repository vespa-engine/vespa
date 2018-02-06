// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RankingExpressionTypeValidatorTestCase {

    @Test
    public void tensorTypeValidation() throws Exception {
        try {
            RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
            SearchBuilder searchBuilder = new SearchBuilder(rankProfileRegistry);
            searchBuilder.importString(joinLines(
                    "search test {",
                    "  document test { ",
                    "    field a type tensor(x[],y[]) {",
                    "      indexing: attribute",
                    "    }",
                    "  }",
                    "  rank-profile my_rank_profile {",
                    "    first-phase {",
                    "      expression: attribute(a)",
                    "    }",
                    "  }",
                    "}"
            ));
            searchBuilder.build();
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The first-phase expression must produce a double (a tensor with no dimensions), but produces tensor(x[],y[])",
                         Exceptions.toMessageString(expected));
        }
    }

}
