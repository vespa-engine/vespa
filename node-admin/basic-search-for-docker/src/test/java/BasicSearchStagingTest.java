import com.yahoo.vespa.tenant.systemtest.base.StagingTest;
import com.yahoo.vespa.tenant.systemtest.blackbox.BlackBoxTester;
import com.yahoo.vespa.tenant.systemtest.blackbox.Report;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class BasicSearchStagingTest extends StagingTest {
    @Test
    public void testSearchAndFeeding() throws Exception {

        /**
         * Generates 100 documents and test recall while feeding.
         */
        Report report = new BlackBoxTester.BlackBoxTesterBuilder().documentFormat(
                "  {\n" +
                        "    \"put\": \"id:sampleapp:music::::$1\",\n" +
                        "    \"fields\": {\n" +
                        "      \"title\": \"$2\"\n" +
                        "    }\n" +
                        "  }")
                .numberOfDocumentsToFeed(100).build(getStagingTestInstance().getDefaultVespaEndpoint().getUri())
                .testFeedingAndRecall();
        assertThat(report.allSuccess(), is(true));
    }
}
