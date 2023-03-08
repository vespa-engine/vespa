// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author arnej
 */
public class TinyBertTest {

    @Test
    public void testTinyBert() {
        ModelTester tester = new ModelTester("src/test/resources/config/tinybert/");
        assertEquals(3, tester.models().size());
    }

}
