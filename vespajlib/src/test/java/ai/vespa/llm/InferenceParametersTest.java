package ai.vespa.llm;


import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author glebashnik
 */
public class InferenceParametersTest {
    @Test
    public void testWithDefaultOptions() {
        var params = new InferenceParameters("testapikey", "testhost", Map.of("a", "a1", "b", "b1")::get);
        var combinedParams = params.withDefaultOptions(Map.of("b", "b2", "c", "c2", "d", "d2")::get);
        
        assertEquals(combinedParams.getApiKey(), Optional.of("testapikey"));
        assertEquals(combinedParams.getEndpoint(), Optional.of("testhost"));
        assertEquals(combinedParams.get("a"), Optional.of("a1"));
        assertEquals(combinedParams.get("b"), Optional.of("b1"));
        assertEquals(combinedParams.get("c"), Optional.of("c2"));
        assertEquals(combinedParams.get("d"), Optional.of("d2"));
        assertTrue(combinedParams.get("e").isEmpty());
    }
}
