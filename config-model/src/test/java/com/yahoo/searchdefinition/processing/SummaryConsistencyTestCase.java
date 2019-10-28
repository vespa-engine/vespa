// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SummaryConsistencyTestCase {

    @Test
    public void attribute_combiner_transform_is_set_when_source_is_array_of_struct_with_only_struct_field_attributes() throws ParseException {
        String sd = "search structmemorysummary {\n" +
                "  document structmemorysummary {\n" +
                "      struct elem {\n" +
                "        field name type string {}\n" +
                "        field weight type int {}\n" +
                "      }\n" +
                "      field elem_array type array<elem> {\n" +
                "          indexing: summary\n" +
                "          struct-field name {\n" +
                "              indexing: attribute\n" +
                "          }\n" +
                "          struct-field weight {\n" +
                "              indexing: attribute\n" +
                "          }\n" +
                "      }\n" +
                "  }\n" +
                "  document-summary unfiltered {\n" +
                "      summary elem_array_unfiltered type array<elem> {\n" +
                "          source: elem_array\n" +
                "      }\n" +
                "  }\n" +
                "\n" +
                "}";
        Search search = SearchBuilder.createFromString(sd).getSearch();
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, search.getSummaryField("elem_array_unfiltered").getTransform());
    }
}
