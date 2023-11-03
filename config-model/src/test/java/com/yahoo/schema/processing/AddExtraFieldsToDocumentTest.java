// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.document.DataType;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.Schema;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Test AddExtraFieldsToDocument processor.
 */
public class AddExtraFieldsToDocumentTest {

    @Test
    public void testCopyTransformForExtraSummaryFields() throws ParseException {
        String sd = joinLines("schema test {",
                "  document test {",
                "    field a type string { indexing: index }",
                "    field b type int { }",
                "    field c type long { indexing: attribute }",
                "  }",
                "  document-summary foo {",
                "    summary my_a { source: a }",
                "    summary my_b { source: b }",
                "    summary my_c { source: c }",
                "    from-disk",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        var builder = ApplicationBuilder.createFromStrings(logger, sd);
        var schema = builder.getSchema();
        assertTrue(logger.entries.isEmpty());
        // Don't use extra fields when generating summary.
        assertSummary(schema, "foo", "my_a", SummaryTransform.COPY, "a");
        assertSummary(schema, "foo", "my_b", SummaryTransform.COPY, "b");
        assertSummary(schema, "foo", "my_c", SummaryTransform.ATTRIBUTE, "c");
        // Extra fields should still be created
        assertField(schema, "my_a", DataType.STRING);
        assertField(schema,"my_b", DataType.INT);
        assertNull(schema.getDocument().getField("my_c"));
    }

    @Test
    public void testExtraFieldIsAddedWhenBeingASummarySource() throws ParseException {
        var sd = """
                search renamed {
                  document renamed {
                    field foo type string { }
                  }
                  field bar type string {
                    indexing: input foo | summary
                    summary baz { }
                  }
                  field bar2 type string {
                    indexing: input foo
                    summary baz2 { }
                  }
                }
                """;
        var builder = ApplicationBuilder.createFromString(sd);
        var schema = builder.getSchema();
        assertNotNull(schema.getDocument().getDocumentType().getField("bar"));
        assertNull(schema.getDocument().getDocumentType().getField("bar2"));
    }

    private void assertSummary(Schema schema, String dsName, String name, SummaryTransform transform, String source) {
        var docsum = schema.getSummary(dsName);
        var field = docsum.getSummaryField(name);
        assertEquals(transform, field.getTransform());
        assertEquals(source, field.getSingleSource());
    }

    private void assertField(Schema schema, String name, DataType type) {
        var field = schema.getDocument().getField(name);
        assertNotNull(field);
        assertEquals(field.getDataType(), type);
    }

}
