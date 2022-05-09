// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * For testing purposes only.
 *
 * @author geirst
 */
public class ApplicationPackageUtils {

    public static String generateSchema(String name, String field1, String field2) {
        return "schema " + name + " {" +
               "  document " + name + " {" +
               "    field " + field1 + " type string {\n" +
               "      indexing: index | summary\n" +
               "      summary: dynamic\n" +
               "    }\n" +
               "    field " + field2 + " type int {\n" +
               "      indexing: attribute | summary\n" +
               "      attribute: fast-access\n" +
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
               "  rank-profile inputs {" +
               "    inputs {" +
               "      query(foo) tensor<float>(x[10])" +
               "      query(bar) tensor(key{},x[1000])" +
               "    }" +
               "  }" +
               "}";
    }

    public static List<String> generateSchemas(String ... sdNames) {
        return generateSchemas(Arrays.asList(sdNames));
    }

    public static List<String> generateSchemas(List<String> sdNames) {
        List<String> sds = new ArrayList<>();
        int i = 0;
        for (String sdName : sdNames) {
            sds.add(generateSchema(sdName, "f" + (i + 1), "f" + (i + 2)));
            i = i + 2;
        }
        return sds;
    }

}
