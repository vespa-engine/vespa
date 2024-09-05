package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import ai.vespa.schemals.documentation.DocumentationFetcher;

/**
 * FetchDocsTest
 */
public class FetchDocsTest {
    @Test
    public void testFetchDocs() {
        try {
            String result = DocumentationFetcher.fetchDocs();
            assertEquals(0, 1, result);
        } catch(IOException ioe) {
            assertEquals(0, 1, ioe.getMessage());
        }
    }
}
