package ai.vespa.feed.client.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class CliClientTest {

    @Test
    void testDummyStream() throws IOException {
        AtomicInteger count = new AtomicInteger(3);
        InputStream in = CliClient.createDummyInputStream(4, new Random(0), () -> count.decrementAndGet() >= 0);
        byte[] buffer = new byte[1 << 20];
        int offset = 0, read;
        while ((read = in.read(buffer, offset, buffer.length - offset)) >= 0) offset += read;
        assertEquals("{ \"put\": \"id:test:test::ssxvnjhp\", \"fields\": { \"test\": \"dqdx\" } }\n" +
                     "{ \"put\": \"id:test:test::vcrastvy\", \"fields\": { \"test\": \"bcwv\" } }\n" +
                     "{ \"put\": \"id:test:test::mgnykrxv\", \"fields\": { \"test\": \"zxkg\" } }\n",
                     new String(buffer, 0, offset, StandardCharsets.UTF_8));
    }

}
