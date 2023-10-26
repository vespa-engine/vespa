// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class SmallConstantImportingTest {

    @Test
    public void testImportingSmallConstant() {
        ModelTester tester = new ModelTester("src/test/resources/config/smallconstant/");

        assertEquals(1, tester.models().size());

        Model model = tester.models().get("my_profile");
        tester.assertFunction("firstphase", "reduce(constant(my_tensor), sum)", model);
        assertEquals(3.0, model.evaluatorOf().evaluate().asDouble(), 0.00000000001);

    }

}
