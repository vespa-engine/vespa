// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;

public class SummaryConsistencyTestCase {

    @Test
    public void attribute_combiner_transform_is_set_when_source_is_array_of_struct_with_only_struct_field_attributes() throws ParseException {
        String sd = joinLines(
                "search structmemorysummary {",
                "  document structmemorysummary {",
                "      struct elem {",
                "        field name type string {}",
                "        field weight type int {}\n",
                "      }",
                "      field elem_array type array<elem> {",
                "          indexing: summary",
                "          struct-field name {",
                "              indexing: attribute",
                "          }",
                "          struct-field weight {",
                "              indexing: attribute",
                "          }",
                "      }",
                "  }",
                "  document-summary unfiltered {",
                "      summary elem_array_unfiltered type array<elem> {",
                "          source: elem_array",
                "      }",
                "  }",
                "",
                "}"
        );
        Search search = SearchBuilder.createFromString(sd).getSearch();
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, search.getSummaryField("elem_array_unfiltered").getTransform());
    }
}
