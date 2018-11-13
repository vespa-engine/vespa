package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.Test;

import java.io.IOException;
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
        assertEquals("summary field 'ondisk' in document summary 'default' references source field 'ondisk', " +
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
                "  document-summary default {\n" +
                "      from-disk\n" +
                "  }\n" +
                "\n" +
                "}";
        DeployLoggerStub logger = new DeployLoggerStub();
        SearchBuilder.createFromString(sd, logger);
        assertTrue(logger.entries.isEmpty());
    }

}
