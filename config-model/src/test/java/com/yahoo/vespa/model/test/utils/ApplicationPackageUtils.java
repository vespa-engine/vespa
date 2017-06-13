// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test.utils;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.search.SearchDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * For testing purposes only.
 * @author geirst
 */
public class ApplicationPackageUtils {

    public static String generateSearchDefinition(String name, String field1, String field2) {
        String sd = "" +
                "search " + name + "{" +
                "  document " + name + "{" +
                "    field " + field1 + " type string {\n" +
                "      indexing: index | summary\n" +
                "      summary: dynamic\n" +
                "      header\n" +
                "    }\n" +
                "    field " + field2 + " type int {\n" +
                "      indexing: attribute | summary\n" +
                "      attribute: fast-access\n" +
                "      header\n" +
                "    }\n" +
                "    field " + field2 + "_nfa type int {\n" +
                "      indexing: attribute \n" +
                "    }\n" +
                "  }\n" +
                "  rank-profile staticrank inherits default {" +
                "    first-phase { expression: attribute(" + field2 + ") }" +
                "  }" +
                "  rank-profile summaryfeatures inherits default {" +
                "    first-phase { expression: attribute(" + field2 + ") }\n" +
                "    summary-features: attribute(" + field2 + ")" +
                "  }" +
                "  rank-profile inheritedsummaryfeatures inherits summaryfeatures {" +
                "  }" +
                "  rank-profile rankfeatures {" +
                "    first-phase { expression: attribute(" + field2 + ") }\n" +
                "    rank-features: attribute(" + field2 + ")" +
                "  }" +
                "}";
        return sd;
    }

    public static Search createSearch(String name, String field1, String field2) throws ParseException {
        SearchBuilder sb = new SearchBuilder();
        sb.importString(generateSearchDefinition(name, field1, field2));
        sb.build();
        return sb.getSearch();
    }

    public static SearchDefinition createSearchDefinition(String name, String field1, String field2) throws ParseException {
        com.yahoo.searchdefinition.Search type = ApplicationPackageUtils.createSearch(name, field1, field2);
        return new SearchDefinition(type.getName(), type);
    }

    public static List<String> generateSearchDefinition(String name) {
        return generateSearchDefinitions(name);
    }

    public static List<String> generateSearchDefinitions(String ... sdNames) {
        return generateSearchDefinitions(Arrays.asList(sdNames));
    }

    public static List<SearchDefinition> createSearchDefinition(String name) throws ParseException {
        return createSearchDefinitions(Arrays.asList(name));
    }

    public static List<SearchDefinition> createSearchDefinitions(List<String> sdNames) throws ParseException {
        List<SearchDefinition> sds = new ArrayList<>();
        int i = 0;
        for (String sdName : sdNames) {
            sds.add(createSearchDefinition(sdName, "f" + (i + 1), "f" + (i + 2)));
            i = i + 2;
        }
        return sds;
    }

    public static List<String> generateSearchDefinitions(List<String> sdNames) {
        List<String> sds = new ArrayList<>();
        int i = 0;
        for (String sdName : sdNames) {
            sds.add(generateSearchDefinition(sdName, "f" + (i + 1), "f" + (i + 2)));
            i = i + 2;
        }
        return sds;
    }

}
