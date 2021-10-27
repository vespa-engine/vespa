// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.NodeResources;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeSkewTest {

    private static final double d = 0.0001;

    @Test
    public void testNodeSkewComputation() {
        // No skew
        assertEquals(0, Node.skew(r(6, 4, 2), r(6, 4, 2)), d);
        assertEquals(0, Node.skew(r(6, 4, 2), r(0, 0, 0)), d);
        assertEquals(0, Node.skew(r(6, 4, 2), r(3, 2, 1)), d);

        // Extremely skewed
        assertEquals(0.2222, Node.skew(r(6, 4, 2), r(0, 4, 0)), d);
        // A little less
        assertEquals(0.1666, Node.skew(r(6, 4, 2), r(3, 4, 0)), d);
        // A little less
        assertEquals(0.0555, Node.skew(r(6, 4, 2), r(3, 4, 1)), d);
        // The same, since being at half and full is equally skewed here
        assertEquals(0.0555, Node.skew(r(6, 4, 2), r(3, 4, 2)), d);
        // Almost not skewed
        assertEquals(0.0062, Node.skew(r(6, 4, 2), r(5, 4, 2)), d);

        // Skew is scale free
        assertEquals(0.0201, Node.skew(r( 6, 4, 2), r(1, 1, 1)), d);
        // - all dimensions twice as large
        assertEquals(0.0201, Node.skew(r(12, 8, 4), r(2, 2, 2)), d);
        // - just one dimension twice as large
        assertEquals(0.0201, Node.skew(r(12, 4, 2), r(2, 1, 1)), d);
    }

    private NodeResources r(double vcpu, double memGb, double diskGb) {
        return new NodeResources(vcpu, memGb, diskGb, 1);
    }

}
