// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Schema tests that don't depend on files.
 *
 * @author bratseth
 */
public class SchemaTestCase {

    @Test
    public void testValidationOfInheritedSchema() throws ParseException {
        try {
            String schema = joinLines(
                    "schema test inherits nonesuch {" +
                    "  document test inherits nonesuch {" +
                    "  }" +
                    "}");
            DeployLoggerStub logger = new DeployLoggerStub();
            SearchBuilder.createFromStrings(logger, schema);
            assertEquals("schema 'test' inherits 'nonesuch', but this schema does not exist",
                         logger.entries.get(0).message);
            fail("Expected failure");
        }
        catch (IllegalArgumentException e) {
            assertEquals("schema 'test' inherits 'nonesuch', but this schema does not exist", e.getMessage());
        }
    }

    @Test
    public void testValidationOfSchemaAndDocumentInheritanceConsistency() throws ParseException {
        try {
            String parent = joinLines(
                    "schema parent {" +
                    "  document parent {" +
                    "    field pf1 type string {" +
                    "      indexing: summary" +
                    "    }" +
                    "  }" +
                    "}");
            String child = joinLines(
                    "schema child inherits parent {" +
                    "  document child {" +
                    "    field cf1 type string {" +
                    "      indexing: summary" +
                    "    }" +
                    "  }" +
                    "}");
            SearchBuilder.createFromStrings(new DeployLoggerStub(), parent, child);
        }
        catch (IllegalArgumentException e) {
            assertEquals("schema 'child' inherits 'parent', " +
                         "but its document type does not inherit the parent's document type"
                         , e.getMessage());
        }
    }

    @Test
    public void testSchemaInheritance() throws ParseException {
        String parentLines = joinLines(
                "schema parent {" +
                "  document parent {" +
                "    field pf1 type string {" +
                "      indexing: summary" +
                "    }" +
                "  }" +
                "  fieldset parent_set {" +
                "    fields: pf1" +
                "  }" +
                "  stemming: none" +
                "  index parent_index {" +
                "    stemming: best" +
                "  }" +
                "  field parent_field type string {" +
                "      indexing: input pf1 | lowercase | index | attribute | summary" +
                "  }" +
                "}");
        String childLines = joinLines(
                "schema child inherits parent {" +
                "  document child inherits parent {" +
                "    field cf1 type string {" +
                "      indexing: summary" +
                "    }" +
                "  }" +
                "}");
        var application = SearchBuilder.createFromStrings(new DeployLoggerStub(), parentLines, childLines).application();
        var child = application.schemas().get("child");
        assertEquals("pf1", child.fieldSets().userFieldSets().get("parent_set").getFieldNames().stream().findFirst().get());
        assertEquals(Stemming.NONE, child.getStemming());
        assertEquals(Stemming.BEST, child.getIndex("parent_index").getStemming());
        assertNotNull(child.getField("parent_field"));
        assertNotNull(child.getExtraField("parent_field"));
    }

}
