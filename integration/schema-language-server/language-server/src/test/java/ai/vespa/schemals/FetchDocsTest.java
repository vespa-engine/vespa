package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import ai.vespa.schemals.documentation.FetchDocumentation;

/**
 * FetchDocsTest
 */
public class FetchDocsTest {
    @Test
    public void testFetchDocs() {
        try {
            FetchDocumentation.fetchDocs(Paths.get("").resolve("tmp").resolve("generated-resources").resolve("hover"));
        } catch(IOException ioe) {
            assertEquals(0, 1, ioe.getMessage());
        }
    }
}
