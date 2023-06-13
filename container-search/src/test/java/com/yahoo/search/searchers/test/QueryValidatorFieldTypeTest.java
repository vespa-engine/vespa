package com.yahoo.search.searchers.test;

import com.yahoo.search.Query;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldSet;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.QueryValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class QueryValidatorFieldTypeTest {

    @Test
    void testTensorsCannotBeSearchedForTerms() {
        var test = new Schema.Builder("test")
                           .add(new Field.Builder("mytensor1", "tensor(x[100])").build())
                           .add(new Field.Builder("mytensor2", "tensor<float>(x[100])").build())
                           .add(new Field.Builder("mystring", "string").addAlias("fieldAlias").build())
                           .add(new FieldSet.Builder("myFieldSet").addField("mystring").build())
                           .build();
        var schemaInfo = new SchemaInfo(List.of(test), List.of());
        Execution execution = new Execution(Execution.Context.createContextStub(schemaInfo));

        assertSucceeds("?query=mystring:foo", execution);
        assertSucceeds("?query=fieldAlias:foo", execution);
        assertSucceeds("?query=myFieldSet:foo", execution);
        assertSucceeds("?query=none:foo", execution);
        assertFails("Cannot search for terms in 'mytensor1': It is a tensor field",
                    "?query=mytensor1:foo", execution);
        assertFails("Cannot search for terms in 'mytensor2': It is a tensor field",
                    "?query=mytensor2:foo", execution);
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
