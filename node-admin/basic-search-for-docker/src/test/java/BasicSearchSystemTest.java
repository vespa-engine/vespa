import com.yahoo.vespa.tenant.systemtest.Endpoint;
import com.yahoo.vespa.tenant.systemtest.VespaEndpoints;
import com.yahoo.vespa.tenant.systemtest.base.SystemTest;
import com.yahoo.vespa.tenant.systemtest.blackbox.BlackBoxTester;
import com.yahoo.vespa.tenant.systemtest.blackbox.Report;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests running in CI-pipeline. In a regular vespa system this will be triggered whenever a commit is made and
 * run before code is pushed to production.
 *
 * @author dybdahl
 */
public class BasicSearchSystemTest extends SystemTest {
    @Override
    /**
     * In order to develop and test the system tests, you can create a dev instance and push this
     * (e.g. mvn deploy:vespa) to your personal vespa dev instance. You will need to change this
     * function to point to this instance. This is only for testing and debugging the test.
     */
    protected VespaEndpoints createVespaSystemTestInstanceEndpointsWhenNotOnScrewdriver() {
        return new VespaEndpoints.Builder().fromPom().inDevCluster()
                .withRegion("corp-us-east-1").withTenant("ENTER TENANT USER FOR USED FOR LOCAL DEVELOPMENT HERE").build();
    }


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
                .numberOfDocumentsToFeed(100).build(getSystemTestsInstance().getDefaultVespaEndpoint().getUri())
                .testFeedingAndRecall();
        assertThat(report.allSuccess(), is(true));
    }
}
