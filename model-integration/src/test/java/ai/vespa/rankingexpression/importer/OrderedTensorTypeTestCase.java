// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class OrderedTensorTypeTestCase {

    @Test
    public void testToFromSpec() {
        String spec = "tensor(b[],c{},a[3])";
        String orderedSpec = "tensor(a[3],b[],c{})";
        OrderedTensorType type = OrderedTensorType.fromSpec(spec);
        assertEquals(orderedSpec, type.toString());
        assertEquals(orderedSpec, type.type().toString());
    }

}
