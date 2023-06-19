// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.search.Query;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Field;
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
public class QueryValidatorPrefixTest {

    @Test
    void testPrefixRequiresAttribute() {
        var indexing = new Cluster.Builder("indexing").addSchema("test1").build();
        var streaming = new Cluster.Builder("streaming").addSchema("test1").addSchema("test2").setStreaming(true).build();
        var schemaInfo = new SchemaInfo(List.of(schema("test1"), schema("test2")), List.of(indexing, streaming));

        assertIndexingValidation("", schemaInfo);
        assertIndexingValidation("sources=indexing", schemaInfo);
        assertIndexingValidation("sources=indexing,streaming", schemaInfo);
        assertIndexingValidation("sources=indexing,streaming,ignored", schemaInfo);
        assertStreamingValidation("sources=streaming", schemaInfo);
        assertStreamingValidation("sources=streaming,ignored", schemaInfo);
        assertIndexingValidation("sources=test1", schemaInfo);
        assertIndexingValidation("sources=test1,streaming", schemaInfo);
        assertStreamingValidation("sources=test2,streaming", schemaInfo);
        assertIndexingValidation("sources=test1,test2", schemaInfo);
        assertStreamingValidation("sources=test2", schemaInfo);
    }

    private Schema schema(String name) {
        return new Schema.Builder(name)
                       .add(new Field.Builder("attributeOnly", "string").setAttribute(true).build())
                       .add(new Field.Builder("indexOnly", "string").setIndex(true).build())
                       .add(new Field.Builder("attributeAndIndex", "string").setAttribute(true).setIndex(true).build())
                       .build();
    }

    private void assertIndexingValidation(String sourcesParameter, SchemaInfo schemaInfo) {
        Execution execution = new Execution(Execution.Context.createContextStub(schemaInfo));
        assertSucceeds("?query=attributeOnly:foo*&" + sourcesParameter, execution);
        assertFails("'indexOnly' is not an attribute field: Prefix matching is not supported",
                    "?query=indexOnly:foo*&" + sourcesParameter, execution);
        assertFails("'attributeAndIndex' is an index field: Prefix matching is not supported even when it is also an attribute",
                    "?query=attributeAndIndex:foo*&" + sourcesParameter, execution);
    }

    private void assertStreamingValidation(String sourcesParameter, SchemaInfo schemaInfo) {
        Execution execution = new Execution(Execution.Context.createContextStub(schemaInfo));
        assertSucceeds("?query=attributeOnly:foo*&" + sourcesParameter, execution);
        assertSucceeds("?query=indexOnly:foo*&" + sourcesParameter, execution);
        assertSucceeds("?query=attributeAndIndex:foo*&" + sourcesParameter, execution);
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
