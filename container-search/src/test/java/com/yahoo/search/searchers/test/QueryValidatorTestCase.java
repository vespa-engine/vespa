// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.QueryValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class QueryValidatorTestCase {

    @Test
    void testTensorsCannotBeSearchedForTerms() {
        SearchDefinition sd = new SearchDefinition("test");
        sd.addCommand("mytensor1", "type tensor(x[100]");
        sd.addCommand("mytensor2", "type tensor<float>(x[100]");
        sd.addCommand("mystring", "type string");
        IndexModel model = new IndexModel(sd);

        IndexFacts indexFacts = new IndexFacts(model);
        Execution execution = new Execution(Execution.Context.createContextStub(indexFacts));
        assertSucceeds("?query=mystring:foo", execution);
        assertFails("Cannot search for terms in 'mytensor1': It is a tensor field",
                    "?query=mytensor1:foo", execution);
        assertFails("Cannot search for terms in 'mytensor2': It is a tensor field",
                    "?query=mytensor2:foo", execution);
    }

    @Test
    void testPrefixRequiresAttribute() {
        SearchDefinition sd = new SearchDefinition("test");
        sd.addCommand("attributeOnly", "type string")
          .addCommand("attribute");
        sd.addCommand("indexOnly", "type string")
          .addCommand("index");
        sd.addCommand("attributeAndIndex", "type string")
          .addCommand("attribute")
          .addCommand("index");
        IndexModel model = new IndexModel(sd);

        IndexFacts indexFacts = new IndexFacts(model);
        Execution execution = new Execution(Execution.Context.createContextStub(indexFacts));

        assertSucceeds("?query=attributeOnly:foo*", execution);
        assertFails("'indexOnly' is not an attribute field: Prefix matching is not supported",
                    "?query=indexOnly:foo*", execution);
        assertFails("'attributeAndIndex' is an index field: Prefix matching is not supported even when it is also an attribute",
                    "?query=attributeAndIndex:foo*", execution);
    }

    private void assertSucceeds(String query, Execution execution) {
        new QueryValidator().search(new Query(query), execution);
    }

    private void assertFails(String expectedError, String query, Execution execution) {
        try {
            new QueryValidator().search(new Query(query), execution);
            fail("Expected validation error from " + query);
        }
        catch (IllegalArgumentException e) {
            // success
            assertEquals(expectedError, e.getMessage());
        }
    }

}
