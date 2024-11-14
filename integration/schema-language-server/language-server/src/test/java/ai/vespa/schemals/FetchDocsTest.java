package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ai.vespa.schemals.documentation.FetchDocumentation;

/**
 * FetchDocsTest
 */
public class FetchDocsTest {
    @Test
    public void testFetchDocs() {
        try {
            FetchDocumentation.fetchSchemaDocs(Paths.get("").resolve("tmp").resolve("generated-resources").resolve("hover"));
            FetchDocumentation.fetchServicesDocs(Paths.get("").resolve("tmp").resolve("generated-resources").resolve("hover"));
        } catch(IOException ioe) {
            assertEquals(0, 1, ioe.getMessage());
        }

        if (Paths.get("").resolve("tmp").toFile().exists()) {
            deleteDirectory(Paths.get("").resolve("tmp").toFile());
        }
    }

    private boolean deleteDirectory(File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        return directory.delete();
    }
}
