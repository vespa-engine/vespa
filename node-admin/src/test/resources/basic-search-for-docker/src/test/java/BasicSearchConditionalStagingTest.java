import com.yahoo.vespa.tenant.systemtest.base.StagingAutoTestConfigBuilder;
import com.yahoo.vespa.tenant.systemtest.base.StagingTest;
import org.junit.Test;

import java.io.IOException;

/**
 * This test is only run if there is a production instance to get queries and documents from, otherwise the tests
 * are marked as ignored automatically.
 */
public class BasicSearchConditionalStagingTest extends StagingTest {
    @Test
    public void testProdQueriesAndDocuments() throws IOException {
        testAutomatically(new StagingAutoTestConfigBuilder().withRoute("music-index").withDocumentType("music").build());
    }
}
