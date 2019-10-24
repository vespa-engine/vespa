package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.Test;

import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests summary validation
 *
 * @author bratseth
 */
public class SummaryTestCase {

    @Test
    public void testMemorySummary() throws ParseException {
        String sd =
                "search memorysummary {\n" +
                "\n" +
                "  document memorysummary {\n" +
                "\n" +
                "      field inmemory type string {\n" +
                "          indexing: attribute | summary\n" +
                "      }\n" +
                "      field ondisk type string {\n" +
                "          indexing: index # no summary, so ignored\n" +
                "      }\n" +
                "\n" +
                "  }\n" +
                "\n" +
                "}";
        DeployLoggerStub logger = new DeployLoggerStub();
        SearchBuilder.createFromString(sd, logger);
        assertTrue(logger.entries.isEmpty());
    }

    @Test
    public void testDiskSummary() throws ParseException {
        String sd =
                "search disksummary {\n" +
                "\n" +
                "  document-summary foobar {\n" +
                "      summary foo1 type string { source: inmemory }\n" +
                "      summary foo2 type string { source: ondisk }\n" +
                "  }\n" +
                "  document disksummary {\n" +
                "\n" +
                "      field inmemory type string {\n" +
                "          indexing: attribute | summary\n" +
                "      }\n" +
                "      field ondisk type string {\n" +
                "          indexing: index | summary\n" +
                "      }\n" +
                "\n" +
                "  }\n" +
                "\n" +
                "}";
        DeployLoggerStub logger = new DeployLoggerStub();
        SearchBuilder.createFromString(sd, logger);
        assertEquals(1, logger.entries.size());
        assertEquals(Level.WARNING, logger.entries.get(0).level);
        assertEquals("summary field 'foo2' in document summary 'foobar' references source field 'ondisk', " +
                     "which is not an attribute: Using this summary will cause disk accesses. " +
                     "Set 'from-disk' on this summary class to silence this warning.",
                     logger.entries.get(0).message);
    }

    @Test
    public void testDiskSummaryExplicit() throws ParseException {
        String sd =
                "search disksummary {\n" +
                "\n" +
                "  document disksummary {\n" +
                "\n" +
                "      field inmemory type string {\n" +
                "          indexing: attribute | summary\n" +
                "      }\n" +
                "      field ondisk type string {\n" +
                "          indexing: index | summary\n" +
                "      }\n" +
                "\n" +
                "  }\n" +
                "\n" +
                "  document-summary foobar {\n" +
                "      summary foo1 type string { source: inmemory }\n" +
                "      summary foo2 type string { source: ondisk }\n" +
                "      from-disk\n" +
                "  }\n" +
                "\n" +
                "}";
        DeployLoggerStub logger = new DeployLoggerStub();
        SearchBuilder.createFromString(sd, logger);
        assertTrue(logger.entries.isEmpty());
    }

    @Test
    public void testStructMemorySummary() throws ParseException {
        String sd =
                "search structmemorysummary {\n" +
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
                        "  document-summary filtered {\n" +
                        "      summary elem_array_filtered type array<elem> {\n" +
                        "          source: elem_array\n" +
                        "          matched-elements-only\n" +
                        "      }\n" +
                        "  }\n" +
                        "\n" +
                        "}";
        DeployLoggerStub logger = new DeployLoggerStub();
        SearchBuilder.createFromString(sd, logger);
        assertTrue(logger.entries.isEmpty());
    }

}
