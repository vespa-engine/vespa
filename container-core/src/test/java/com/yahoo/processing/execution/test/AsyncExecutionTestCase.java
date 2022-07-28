// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import org.junit.jupiter.api.Test;

import static com.yahoo.processing.test.ProcessorLibrary.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author  bratseth
 */
public class AsyncExecutionTestCase {

    /** Execute a processing chain which forks off into multiple threads */
    @Test
    void testAsyncExecution() {
        // Create a chain
        Chain<Processor> chain = new Chain<>(new CombineData(), new BlockingSplitter(2), new Get6DataItems(), new DataSource());

        // Execute it
        Request request = new Request();
        request.properties().set("appendage", 1);
        Response response = Execution.createRoot(chain, 0, Execution.Environment.createEmpty()).process(request);

        // Verify the result
        assertEquals(6 * 2 - 1, response.data().asList().size());
        assertEquals("first.2, third.2", response.data().get(0).toString());
        assertEquals("second.2", response.data().get(1).toString());
        assertEquals("first.3", response.data().get(2).toString());
        assertEquals("second.3", response.data().get(3).toString());
        assertEquals("third.3", response.data().get(4).toString());
        // from the parallel execution
        assertEquals("first.2", response.data().get(5).toString());
        assertEquals("second.2", response.data().get(6).toString());
        assertEquals("third.2", response.data().get(7).toString());
        assertEquals("first.3", response.data().get(8).toString());
        assertEquals("second.3", response.data().get(9).toString());
        assertEquals("third.3", response.data().get(10).toString());
    }

}
