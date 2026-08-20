// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author frodelu
 */
class InconclusiveTestExceptionTest {

    @Test
    void defaultConstructorsLeaveDurationsEmpty() {
        InconclusiveTestException noArgs = new InconclusiveTestException();
        assertNull(noArgs.getMessage());
        assertEquals(Optional.empty(), noArgs.retryAfter());
        assertEquals(Optional.empty(), noArgs.giveUpAfter());

        InconclusiveTestException withMessage = new InconclusiveTestException("not yet");
        assertEquals("not yet", withMessage.getMessage());
        assertEquals(Optional.empty(), withMessage.retryAfter());
        assertEquals(Optional.empty(), withMessage.giveUpAfter());
    }

    @Test
    void durationsRoundTripThroughFullConstructor() {
        InconclusiveTestException exception =
                new InconclusiveTestException("not yet", Duration.ofMinutes(5), Duration.ofHours(2));
        assertEquals("not yet", exception.getMessage());
        assertEquals(Optional.of(Duration.ofMinutes(5)), exception.retryAfter());
        assertEquals(Optional.of(Duration.ofHours(2)), exception.giveUpAfter());

        InconclusiveTestException partial = new InconclusiveTestException(null, Duration.ZERO, null);
        assertNull(partial.getMessage());
        assertEquals(Optional.of(Duration.ZERO), partial.retryAfter());
        assertEquals(Optional.empty(), partial.giveUpAfter());
    }

    @Test
    void negativeDurationsAreRejected() {
        assertEquals("retryAfter must be non-negative",
                     assertThrows(IllegalArgumentException.class,
                                  () -> new InconclusiveTestException("", Duration.ofSeconds(-1), null))
                             .getMessage());
        assertEquals("giveUpAfter must be non-negative",
                     assertThrows(IllegalArgumentException.class,
                                  () -> new InconclusiveTestException("", null, Duration.ofSeconds(-1)))
                             .getMessage());
    }

}
