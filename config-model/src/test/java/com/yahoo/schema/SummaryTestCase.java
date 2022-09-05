// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import com.yahoo.vespa.objects.FieldBase;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests summary validation
 *
 * @author bratseth
 */
public class SummaryTestCase {

    @Test
    void testMemorySummary() throws ParseException {
        String sd = joinLines(
                "schema memorysummary {",
                "  document memorysummary {",
                "      field inmemory type string {",
                "          indexing: attribute | summary",
                "      }",
                "      field ondisk type string {",
                "          indexing: index # no summary, so ignored",
                "      }",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromString(sd, logger);
        assertTrue(logger.entries.isEmpty());
    }

    @Test
    void testDiskSummary() throws ParseException {
        String sd = joinLines(
                "schema disksummary {",
                "  document-summary foobar {",
                "      summary foo1 type string { source: inmemory }",
                "      summary foo2 type string { source: ondisk }",
                "  }",
                "  document disksummary {",
                "      field inmemory type string {",
                "          indexing: attribute | summary",
                "      }",
                "      field ondisk type string {",
                "          indexing: index | summary",
                "      }",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromString(sd, logger);
        assertEquals(1, logger.entries.size());
        assertEquals(Level.WARNING, logger.entries.get(0).level);
        assertEquals("In schema 'disksummary', document summary 'foobar': " +
                     "Fields [foo2] references non-attribute fields: " +
                     "Using this summary will cause disk accesses. " +
                     "Set 'from-disk' on this summary class to silence this warning.",
                logger.entries.get(0).message);
    }

    @Test
    void testDiskSummaryExplicit() throws ParseException {
        String sd = joinLines(
                "schema disksummary {",
                "  document disksummary {",
                "      field inmemory type string {",
                "          indexing: attribute | summary",
                "      }",
                "      field ondisk type string {",
                "          indexing: index | summary",
                "      }",
                "  }",
                "  document-summary foobar {",
                "      summary foo1 type string { source: inmemory }",
                "      summary foo2 type string { source: ondisk }",
                "      from-disk",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromString(sd, logger);
        assertTrue(logger.entries.isEmpty());
    }

    @Test
    void testStructMemorySummary() throws ParseException {
        String sd = joinLines(
                "schema structmemorysummary {",
                "  document structmemorysummary {",
                "      struct elem {",
                "        field name type string {}",
                "        field weight type int {}",
                "      }",
                "      field elem_array type array<elem> {",
                "          indexing: summary",
                "          struct-field name {",
                "              indexing: attribute",
                "          }",
                "          struct-field weight {",
                "              indexing: attribute",
                "          }",
                "      }",
                "  }",
                "  document-summary filtered {",
                "      summary elem_array_filtered type array<elem> {",
                "          source: elem_array",
                "          matched-elements-only",
                "      }",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromString(sd, logger);
        assertTrue(logger.entries.isEmpty());
    }

    @Test
    void testInheritance() throws Exception {
        String sd = joinLines(
                "schema music {",
                "  document music {",
                "    field title type string {",
                "      indexing: summary | attribute | index",
                "    }",
                "    field artist type string {",
                "      indexing: summary | attribute | index",
                "    }",
                "    field album type string {",
                "      indexing: summary | attribute | index",
                "    }",
                "  }",
                "  document-summary title {",
                "    summary title type string {",
                "      source: title",
                "    }",
                "  }",
                "  document-summary title_artist inherits title {",
                "    summary artist type string {",
                "      source: artist",
                "    }",
                "  }",
                "  document-summary everything inherits title_artist {",
                "    summary album type string {",
                "      source: album",
                "    }",
                "  }",
                "}");
        var logger = new DeployLoggerStub();
        var search = ApplicationBuilder.createFromString(sd, logger).getSchema();
        assertEquals(List.of(), logger.entries);

        var titleField = "title";
        var artistField = "artist";
        var albumField = "album";
        var titleSummary = search.getSummary(titleField);
        var titleArtistSummary = search.getSummary(titleField + "_" + artistField);
        var everythingSummary = search.getSummary("everything");

        var implicitFields = List.of("rankfeatures", "summaryfeatures");
        var tests = List.of(
                new TestValue(titleSummary, null, List.of(List.of(titleField), implicitFields)),
                new TestValue(titleArtistSummary, titleSummary, List.of(List.of(titleField), implicitFields, List.of(artistField))),
                new TestValue(everythingSummary, titleArtistSummary, List.of(List.of(titleField), implicitFields, List.of(artistField, albumField)))
        );
        tests.forEach(testValue -> {
            var actualFields = testValue.summary.getSummaryFields().values().stream()
                    .map(FieldBase::getName)
                    .collect(Collectors.toList());
            assertEquals(Optional.ofNullable(testValue.parent),
                    testValue.summary.inherited(),
                    testValue.summary.getName() + (testValue.parent == null ? " does not inherit anything" : " inherits " + testValue.parent.getName()));
            assertEquals(testValue.fields, actualFields, "Summary " + testValue.summary.getName() + " has expected fields");
        });
    }

    @Test
    void testRedeclaringInheritedFieldFails() throws Exception {
        String sd = joinLines(
                "schema music {",
                "  document music {",
                "    field title type string {",
                "      indexing: summary | attribute | index",
                "    }",
                "    field title_short type string {",
                "      indexing: summary | attribute | index",
                "    }",
                "  }",
                "  document-summary title {",
                "    summary title type string {",
                "      source: title",
                "    }",
                "  }",
                "  document-summary title2 inherits title {",
                "    summary title type string {",
                "      source: title_short",
                "    }",
                "  }",
                "}");
        var logger = new DeployLoggerStub();
        try {
            ApplicationBuilder.createFromString(sd, logger);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'music', summary class 'title2', summary field 'title': Can not use " +
                    "source 'title_short' for this summary field, an equally named field in summary class 'title' " +
                    "uses a different source: 'title'.", e.getMessage());
        }
    }

    @Test
    void testValidationOfInheritedSummary() throws ParseException {
        try {
            String schema = joinLines(
                    "schema test {" +
                            "  document test {" +
                            "  }" +
                            "  document-summary test_summary inherits nonesuch {" +
                            "  }" +
                            "}");
            DeployLoggerStub logger = new DeployLoggerStub();
            ApplicationBuilder.createFromStrings(logger, schema);
            assertEquals("document summary 'test_summary' inherits nonesuch but this is not present in schema 'test'",
                    logger.entries.get(0).message);
            // fail("Expected failure");
        }
        catch (IllegalArgumentException e) {
            // assertEquals("document summary 'test_summary' inherits nonesuch but this is not present in schema 'test'",
            //             e.getMessage());
        }
    }

    @Test
    void testInheritingParentSummary() throws ParseException {
        String parent = joinLines(
                "schema parent {" +
                        "  document parent {" +
                        "    field pf1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "  document-summary parent_summary {" +
                        "    summary pf1 type string {}" +
                        "  }" +
                        "}");
        String child = joinLines(
                "schema child inherits parent {" +
                        "  document child inherits parent {" +
                        "    field cf1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "  document-summary child_summary inherits parent_summary {" +
                        "    summary cf1 type string {}" +
                        "  }" +
                        "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromStrings(logger, parent, child);
        logger.entries.forEach(e -> System.out.println(e));
        //assertTrue(logger.entries.isEmpty());
    }

    private static class TestValue {

        private final DocumentSummary summary;
        private final DocumentSummary parent;
        private final List<String> fields;

        public TestValue(DocumentSummary summary, DocumentSummary parent, List<List<String>> fields) {
            this.summary = summary;
            this.parent = parent;
            this.fields = fields.stream().flatMap(Collection::stream).collect(Collectors.toList());;
        }

    }

}
