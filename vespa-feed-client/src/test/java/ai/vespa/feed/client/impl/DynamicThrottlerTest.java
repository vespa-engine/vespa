package ai.vespa.feed.client.impl;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class DynamicThrottlerTest {

    @Test
    void testThrottler() {
        DynamicThrottler throttler = new DynamicThrottler(new FeedClientBuilderImpl(List.of(URI.create("http://localhost:8080"))));
        assertEquals(16, throttler.targetInflight());

        for (int i = 0; i < 65; i++) {
            throttler.sent(1, null);
            throttler.success();
        }
        assertEquals(18, throttler.targetInflight());

        throttler.throttled(34);
        assertEquals(17, throttler.targetInflight());
    }

}
