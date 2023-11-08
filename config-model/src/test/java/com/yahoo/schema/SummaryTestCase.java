// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import com.yahoo.vespa.objects.FieldBase;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Disabled;
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
                "      summary foo1 { source: inmemory }",
                "      summary foo2 { source: ondisk }",
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
        assertEquals("In schema 'disksummary', document-summary 'foobar': " +
                     "Fields [foo2] references non-attribute fields: " +
                     "Using this summary will cause disk accesses. " +
                     "Set 'from-disk' on this document-summary to silence this warning.",
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
                "      summary foo1 { source: inmemory }",
                "      summary foo2 { source: ondisk }",
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
                "      summary elem_array_filtered {",
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
                "    summary title {",
                "      source: title",
                "    }",
                "  }",
                "  document-summary title_artist inherits title {",
                "    summary artist {",
                "      source: artist",
                "    }",
                "  }",
                "  document-summary everything inherits title_artist {",
                "    summary album {",
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
                    .toList();
            if (testValue.parent != null)
                assertEquals(testValue.parent, testValue.summary.inherited().get(0),
                             testValue.summary.getName() + " inherits " + testValue.parent.getName());
            else
                assertTrue(testValue.summary.inherited().isEmpty(),
                           testValue.summary.getName() + " does not inherit anything");

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
                "    summary title {",
                "      source: title",
                "    }",
                "  }",
                "  document-summary title2 inherits title {",
                "    summary title {",
                "      source: title_short",
                "    }",
                "  }",
                "}");
        var logger = new DeployLoggerStub();
        try {
            ApplicationBuilder.createFromString(sd, logger);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'music', document-summary 'title2', summary field 'title': Can not use " +
                    "source 'title_short' for this summary field, an equally named field in document-summary 'title' " +
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
            assertEquals("document-summary 'test_summary' inherits 'nonesuch' but this is not present in schema 'test'",
                         logger.entries.get(0).message);
            // fail("Expected failure");
        }
        catch (IllegalArgumentException e) {
            fail();
            // assertEquals("document-summary 'test_summary' inherits nonesuch but this is not present in schema 'test'",
            //             e.getMessage());
        }
    }

    @Test
    void testInheritingTwoSummariesWithConflictingFieldsFails() throws ParseException {
        try {
            String schema = """
                schema test {
                  document test {
                    field field1 type string {
                      indexing: summary | index | attribute
                    }
                    field field2 type int {
                      indexing: summary | attribute
                    }
                  }
                  document-summary parent1 {
                    summary s1 {
                        source: field1
                    }
                  }
                  document-summary parent2 {
                    summary field1 {
                        source: field2
                    }
                  }
                  document-summary child inherits parent1, parent2 {
                  }
                }
                """;
            DeployLoggerStub logger = new DeployLoggerStub();
            ApplicationBuilder.createFromStrings(logger, schema);
            fail("Expected failure");
        }
        catch (IllegalArgumentException e) {
            assertEquals("summary field1 type string in document-summary 'default' is inconsistent with " +
                         "summary field1 type int in document-summary 'parent2': " +
                         "All declarations of the same summary field must have the same type",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testInheritingTwoSummariesWithNonConflictingFieldsWorks() throws ParseException {
        String schema = """
                schema test {
                  document test {
                    field field1 type string {
                      indexing: summary | index | attribute
                    }
                    field field2 type int {
                      indexing: summary | attribute
                    }
                  }
                  document-summary parent1 {
                    summary s1 {
                        source: field1
                    }
                  }
                  document-summary parent2 {
                    summary field1 {
                        source: field1
                    }
                  }
                  document-summary child inherits parent1, parent2 {
                  }
                }
                """;
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromStrings(logger, schema);
        System.out.println("logger.entries = " + logger.entries);
        assertTrue(logger.entries.isEmpty());
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
                        "    summary pf1 {}" +
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
                        "    summary cf1 {}" +
                        "  }" +
                        "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromStrings(logger, parent, child);
        assertTrue(logger.entries.isEmpty());

    }
    private void testSummaryTypeInField(boolean explicit) throws ParseException {
        String sd = joinLines("schema test {",
                "  document test {",
                "    field foo type string {",
                "      indexing: summary",
                "      summary bar " + (explicit ? "type string ": "") + "{ }",
                "    }",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromStrings(logger, sd);
        if (explicit) {
            assertEquals(1, logger.entries.size());
            assertEquals(Level.FINE, logger.entries.get(0).level);
            assertEquals("For schema 'test', field 'foo', summary 'bar':" +
                    " Specifying the type is deprecated, ignored and will be an error in Vespa 9." +
                    " Remove the type specification to silence this warning.", logger.entries.get(0).message);
        } else {
            assertTrue(logger.entries.isEmpty());
        }
    }

    @Test
    void testSummaryInFieldWithoutTypeEmitsNoWarning() throws ParseException {
        testSummaryTypeInField(false);
    }

    @Test
    void testSummaryInFieldWithTypeEmitsWarning() throws ParseException {
        testSummaryTypeInField(true);
    }

    private void testSummaryField(boolean explicit) throws ParseException {
        String sd = joinLines("schema test {",
                "  document test {",
                "    field foo type string { indexing: summary }",
                "  }",
                "  document-summary bar {",
                "    summary foo " + (explicit ? "type string" : "") + "{ }",
                "    from-disk",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        ApplicationBuilder.createFromStrings(logger, sd);
        if (explicit) {
            assertEquals(1, logger.entries.size());
            assertEquals(Level.FINE, logger.entries.get(0).level);
            assertEquals("For schema 'test', document-summary 'bar', summary field 'foo':" +
                    " Specifying the type is deprecated, ignored and will be an error in Vespa 9." +
                    " Remove the type specification to silence this warning.", logger.entries.get(0).message);
        } else {
            assertTrue(logger.entries.isEmpty());
        }
    }

    @Test
    void testSummaryFieldWithoutTypeEmitsNoWarning() throws ParseException {
        testSummaryField(false);
    }

    @Test
    void testSummaryFieldWithTypeEmitsWarning() throws ParseException {
        testSummaryField(true);
    }

    @Test
    void testSummarySourceLoop() throws ParseException {
        String sd = joinLines("schema test {",
                "  document test {",
                "    field foo type string { indexing: summary }",
                "  }",
                "  document-summary bar {",
                "    summary foo { source: foo2 }",
                "    summary foo2 { source: foo3 }",
                "    summary foo3 { source: foo2 }",
                "    from-disk",
                "  }",
                "}");
        DeployLoggerStub logger = new DeployLoggerStub();
        try {
            ApplicationBuilder.createFromStrings(logger, sd);
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test' document-summary 'bar' summary field 'foo'" +
            ": Source loop detected for summary field 'foo2'", e.getMessage());
        }
    }

    private static class TestValue {

        private final DocumentSummary summary;
        private final DocumentSummary parent;
        private final List<String> fields;

        public TestValue(DocumentSummary summary, DocumentSummary parent, List<List<String>> fields) {
            this.summary = summary;
            this.parent = parent;
            this.fields = fields.stream().flatMap(Collection::stream).toList();;
        }

    }

}
