// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.QueryValidator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class QueryValidatorTestCase {

    @Test
    public void testValidation() {
        SearchDefinition sd = new SearchDefinition("test");
        sd.addCommand("mytensor", "type tensor(x[100]");
        sd.addCommand("mystring", "type string");
        IndexModel model = new IndexModel(sd);

        IndexFacts indexFacts = new IndexFacts(model);
        Execution execution = new Execution(Execution.Context.createContextStub(indexFacts));
        new QueryValidator().search(new Query("?query=mystring:foo"), execution);

        try {
            new QueryValidator().search(new Query("?query=sddocname%3Aproduct%20lfmModel25KeysV0%3A9%2A%20mytensor%3A%3E0"), execution);
            fail("Excpected validation error");
        }
        catch (IllegalArgumentException e) {
            // success
            assertEquals("Cannot search 'mytensor': It is a tensor field", e.getMessage());
        }
    }

}
