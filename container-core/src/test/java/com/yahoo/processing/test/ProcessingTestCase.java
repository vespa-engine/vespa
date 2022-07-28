// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import org.junit.jupiter.api.Test;

import static com.yahoo.processing.test.ProcessorLibrary.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the basic of the processing framework
 */
public class ProcessingTestCase {

    /** Execute three simple processors doing some phony processing */
    @Test
    void testChainedProcessing1() {
        // Create a chain
        Chain<Processor> chain = new Chain<>(new CombineData(), new Get6DataItems(), new DataSource());

        // Execute it
        Request request = new Request();
        request.properties().set("appendage", 1);
        Response response = Execution.createRoot(chain, 0, Execution.Environment.createEmpty()).process(request);

        // Verify the result
        assertEquals(6 - 1, response.data().asList().size());
        assertEquals("first.2, third.2", response.data().get(0).toString());
        assertEquals("second.2", response.data().get(1).toString());
        assertEquals("first.3", response.data().get(2).toString());
        assertEquals("second.3", response.data().get(3).toString());
        assertEquals("third.3", response.data().get(4).toString());
    }

    /** Execute the same processors in a different order */
    @Test
    void testChainedProcessing2() {
        // Create a chain
        Chain<Processor> chain = new Chain<>(new Get6DataItems(), new CombineData(), new DataSource());

        // Execute it
        Request request = new Request();
        request.properties().set("appendage", 1);
        Response response = Execution.createRoot(chain, 0, Execution.Environment.createEmpty()).process(request);

        // Check the result
        assertEquals(6, response.data().asList().size());
        assertEquals("first.2, third.2", response.data().get(0).toString());
        assertEquals("second.2", response.data().get(1).toString());
        assertEquals("first.4, third.4", response.data().get(2).toString());
        assertEquals("second.4", response.data().get(3).toString());
        assertEquals("first.6, third.6", response.data().get(4).toString());
        assertEquals("second.6", response.data().get(5).toString());
    }

}
