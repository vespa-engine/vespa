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
    void testValidation() {
        SearchDefinition sd = new SearchDefinition("test");
        sd.addCommand("mytensor1", "type tensor(x[100]");
        sd.addCommand("mytensor2", "type tensor<float>(x[100]");
        sd.addCommand("mystring", "type string");
        IndexModel model = new IndexModel(sd);

        IndexFacts indexFacts = new IndexFacts(model);
        Execution execution = new Execution(Execution.Context.createContextStub(indexFacts));
        new QueryValidator().search(new Query("?query=mystring:foo"), execution);

        try {
            new QueryValidator().search(new Query("?query=mytensor1:foo"), execution);
            fail("Expected validation error");
        }
        catch (IllegalArgumentException e) {
            // success
            assertEquals("Cannot search 'mytensor1': It is a tensor field", e.getMessage());
        }

        try {
            new QueryValidator().search(new Query("?query=mytensor2:foo"), execution);
            fail("Expected validation error");
        }
        catch (IllegalArgumentException e) {
            // success
            assertEquals("Cannot search 'mytensor2': It is a tensor field", e.getMessage());
        }
    }

}
