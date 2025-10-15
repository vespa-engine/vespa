// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for Export command.
 *
 * @author johsol
 */
class ExportTest {

    @TempDir
    Path tmp;

    private ExportClientParameters params(String outputFile, String indexDir, String field,
                                          String cluster, String schema, String node) {
        return ExportClientParameters.builder()
                .outputFile(outputFile)
                .indexDir(indexDir)
                .fieldName(field)
                .clusterName(cluster)
                .schemaName(schema)
                .nodeIndex(node)
                .build();
    }

    /** Minimal fake writer that captures terms written. */
    static class CapturingWriter implements TermDfWriter {
        final List<String> terms = new ArrayList<>();

        @Override
        public void write(VespaIndexInspectClient.TermDocumentFrequency row) throws IOException {
            terms.add(row.term());
        }

        @Override
        public void flush() throws IOException {
            TermDfWriter.super.flush();
        }

        @Override public void close() {}
    }

    @Test
    void happyPathWritesSortedTerms() throws Exception {
        var outPath = tmp.resolve("out.tsv").toString();

        // Params: force IndexLocator path (null indexDir), and provide field name
        var params = params(outPath, null, "myfield", "alpha", "doc", "0");

        // Create the index dir and the field dir on disk
        Path indexDir = tmp.resolve("idx");
        Files.createDirectories(indexDir.resolve("myfield"));

        // Locator returns that path
        IndexLocator locator = mock(IndexLocator.class);
        when(locator.locateIndexDir("alpha", "doc", "0")).thenReturn(indexDir);

        // Dump function emits unsorted rows
        Export.DumpFn dumpFn = (idx, field) -> Stream.of(
                new VespaIndexInspectClient.TermDocumentFrequency("alpha", 5),
                new VespaIndexInspectClient.TermDocumentFrequency("zulu", 2)
        );

        CapturingWriter writer = new CapturingWriter();
        Export.WriterFactory wf = (w, docCount, sorted, createdAt) -> writer;
        Export.DocumentCountProviderFactory dcpf = (dir) -> () -> 42L; // any value is fine for this test

        var baosOut = new ByteArrayOutputStream();
        var prevOut = System.out;
        System.setOut(new PrintStream(baosOut));
        try {
            int code = new Export(params, locator, wf, dumpFn, dcpf).run();
            assertEquals(0, code);
            assertEquals(List.of("alpha", "zulu"), writer.terms);
        } finally {
            System.setOut(prevOut);
        }
    }
}
