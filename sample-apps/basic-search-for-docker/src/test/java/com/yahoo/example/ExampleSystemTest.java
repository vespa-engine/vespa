package com.yahoo.example;

import com.yahoo.vespa.tenant.systemtest.Feed;
import com.yahoo.vespa.tenant.systemtest.FeedResult;
import com.yahoo.vespa.tenant.systemtest.Query;
import com.yahoo.vespa.tenant.systemtest.QueryResult;
import com.yahoo.vespa.tenant.systemtest.VespaEndpoints;
import com.yahoo.vespa.tenant.systemtest.base.MutableVespaEndpoint;
import com.yahoo.vespa.tenant.systemtest.base.SystemTest;
import com.yahoo.vespa.tenant.systemtest.hitchecker.HitChecker;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ExampleSystemTest extends SystemTest {
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
    public void testWithOneDocument() throws Exception {
        MutableVespaEndpoint endpoint = getSystemTestsInstance().getDefaultVespaEndpoint();
        FeedResult feedResult = endpoint.feed(Feed.createFromResource("/minifeed.json"));
        assertThat(feedResult.numOk(), is(1l));
        QueryResult result = endpoint.search(new Query("bad"));
        assertThat(result.totalHitCount(), is(1l));
        result.expectHit(1, new HitChecker()
                .fieldRegex("title", ".*Bad")
                .relevance(0.254, 0.2)
                .fieldNull("nosuchfield"));
    }
}
