// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import com.yahoo.vespa.objects.FieldBase;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    @Test
    public void testInheritance() throws Exception {
        String sd =
                "search music {\n" +
                "\n" +
                "  document music {\n" +
                "    field title type string {\n" +
                "      indexing: summary | attribute | index\n" +
                "    }\n" +
                "    \n" +
                "    field artist type string {\n" +
                "      indexing: summary | attribute | index\n" +
                "    }\n" +
                "    \n" +
                "    field album type string {\n" +
                "      indexing: summary | attribute | index\n" +
                "    }\n" +
                "  }\n" +
                "  \n" +
                "  document-summary title {\n" +
                "    summary title type string {\n" +
                "      source: title\n" +
                "    }\n" +
                "  }\n" +
                "  document-summary title_artist inherits title {\n" +
                "    summary artist type string {\n" +
                "      source: artist\n" +
                "    }\n" +
                "  }\n" +
                "  document-summary everything inherits title_artist {\n" +
                "    summary album type string {\n" +
                "      source: album\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "}";
        var logger = new DeployLoggerStub();
        var search = SearchBuilder.createFromString(sd, logger).getSearch();
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
            var actualFields = testValue.summary.getSummaryFields().stream()
                                                .map(FieldBase::getName)
                                                .collect(Collectors.toList());
            assertEquals(testValue.summary.getName() + (testValue.parent == null ? " does not inherit anything" : " inherits " + testValue.parent.getName()),
                         testValue.parent,
                         testValue.summary.getInherited());
            assertEquals("Summary " + testValue.summary.getName() + " has expected fields", testValue.fields, actualFields);
        });
    }

    @Test
    public void testRedeclaringInheritedFieldFails() throws Exception {
        String sd =
                "search music {\n" +
                "\n" +
                "  document music {\n" +
                "    field title type string {\n" +
                "      indexing: summary | attribute | index\n" +
                "    }\n" +
                "    field title_short type string {\n" +
                "      indexing: summary | attribute | index\n" +
                "    }\n" +
                "  }\n" +
                "  \n" +
                "  document-summary title {\n" +
                "    summary title type string {\n" +
                "      source: title\n" +
                "    }\n" +
                "  }\n" +
                "  document-summary title2 inherits title {\n" +
                "    summary title type string {\n" +
                "      source: title_short\n" +
                "    }\n" +
                "  }\n" +
                "  \n" +
                "}";
        var logger = new DeployLoggerStub();
        try {
            SearchBuilder.createFromString(sd, logger);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'music', summary class 'title2', summary field 'title': Can not use " +
                         "source 'title_short' for this summary field, an equally named field in summary class 'title' " +
                         "uses a different source: 'title'.", e.getMessage());
        }
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
