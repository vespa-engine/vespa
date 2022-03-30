// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import com.google.common.base.Preconditions;
import org.junit.jupiter.api.Test;

import static ai.vespa.validation.Validation.require;
import static ai.vespa.validation.Validation.requireAtLeast;
import static ai.vespa.validation.Validation.requireAtMost;
import static ai.vespa.validation.Validation.requireInRange;
import static ai.vespa.validation.Validation.requireNonBlank;
import static ai.vespa.validation.Validation.validate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
class ValidationTest {

    @Test
    void testNumberComparisons() {
        assertEquals(3.14, validate("3.14", Double::parseDouble, "pi",
                                    requireInRange(3.14, 3.14),
                                    requireInRange(0.0, Double.POSITIVE_INFINITY),
                                    requireInRange(Double.NEGATIVE_INFINITY, 3.14),
                                    requireAtLeast(3.14),
                                    requireAtMost(3.14)));

        assertEquals("lower bound cannot be greater than upper bound, but got '1.0' > '0.1'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> validate(3.14, "pi", requireInRange(1.0, 0.1)))
                             .getMessage());

        assertEquals("pi must be at least '0.0' and at most '0.0', but got: '3.14'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> validate(3.14, "pi", requireInRange(0.0, 0.0)))
                             .getMessage());

        assertEquals("pi must be at least '4.0' and at most '4.0', but got: '3.14'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> validate(3.14, "pi", requireInRange(4.0, 4.0)))
                             .getMessage());

        assertEquals("pi must be at least '4.0', but got: '3.14'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> validate(3.14, "pi", requireAtLeast(4.0)))
                             .getMessage());

        assertEquals("pi must be at most '3.0', but got: '3.14'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> validate(3.14, "pi", requireAtMost(3.0)))
                             .getMessage());
    }

    @Test
    void testStringComparisons() {
        assertEquals("hei", validate("hei", "word",
                                     requireNonBlank(),
                                     require(__ -> true, "nothing"),
                                     requireInRange("hai", "hoi")));

        assertEquals("word cannot be blank, but got: ''",
                     assertThrows(IllegalArgumentException.class,
                                  () -> validate("", "word", requireNonBlank()))
                             .getMessage());

        assertEquals("lower bound cannot be greater than upper bound, but got 'hoi' > 'hai'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> validate("hei", "word", requireInRange("hoi", "hai")))
                             .getMessage());
    }

}
