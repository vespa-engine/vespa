package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class OrderedTensorTypeTestCase {

    @Test
    public void testToFromSpec() {
        String spec = "tensor(b[],c{},a[3])";
        OrderedTensorType type = OrderedTensorType.fromSpec(spec);
        assertEquals(spec, type.toString());
        assertEquals("tensor(a[3],b[],c{})", type.type().toString());
    }

}
