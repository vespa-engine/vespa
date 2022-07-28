// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.IncomingData;
import com.yahoo.processing.test.ProcessorLibrary;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests listening on every available new piece of data in a response
 *
 * @author  bratseth
 */
public class StreamingTestCase {

    /** Tests adding a chain which is called every time new data is added to a data list */
    @SuppressWarnings({"unchecked"})
    @Test
    void testStreamingData() throws InterruptedException, ExecutionException, TimeoutException {
        // Set up
        StreamProcessor streamProcessor = new StreamProcessor();
        Chain<Processor> streamProcessing = new Chain<Processor>(streamProcessor);
        ProcessorLibrary.FutureDataSource futureDataSource = new ProcessorLibrary.FutureDataSource();
        Chain<Processor> main = new Chain<>(new ProcessorLibrary.DataCounter(),
                new ProcessorLibrary.StreamProcessingInitiator(streamProcessing),
                futureDataSource);

        // Execute
        Request request = new Request();
        Response response = Execution.createRoot(main, 0, Execution.Environment.createEmpty()).process(request);
        IncomingData incomingData = futureDataSource.incomingData.get(0);

        // State prior to receiving any additional data
        assertEquals(1, response.data().asList().size());
        assertEquals("Data count: 0", response.data().get(0).toString());
        assertEquals(1, streamProcessor.invocationCount, "Add data listener invoked also for DataCounter");
        assertEquals(1, response.data().asList().size(), "Initial data count");

        // add first data - we have no listener so the data is held in the incoming buffer
        incomingData.add(new ProcessorLibrary.StringData(request, "d1"));
        assertEquals(1, streamProcessor.invocationCount, "Data add listener not invoked as we are not listening on new data yet");
        assertEquals(1, response.data().asList().size(), "New data is not consumed");

        // start listening on incoming data - this is what a renderer will do
        incomingData.addNewDataListener(new MockNewDataListener(incomingData), MoreExecutors.directExecutor());
        assertEquals(2, streamProcessor.invocationCount, "We got a data add event for the data which was already added");
        assertEquals(2, response.data().asList().size(), "New data is consumed");

        incomingData.add(new ProcessorLibrary.StringData(request, "d2"));
        assertEquals(3, streamProcessor.invocationCount, "We are now getting data add events each time");
        assertEquals(3, response.data().asList().size(), "New data is consumed");

        incomingData.addLast(new ProcessorLibrary.StringData(request, "d3"));
        assertEquals(4, streamProcessor.invocationCount, "We are getting data add events also the last time");
        assertEquals(4, response.data().asList().size(), "New data is consumed");

        response.data().completeFuture().get(1000, TimeUnit.MILLISECONDS); // no-op here
        assertEquals("d1", response.data().get(1).toString().toString());
        assertEquals("d2", response.data().get(2).toString().toString());
        assertEquals("d3", response.data().get(3).toString().toString());
    }

    private static class MockNewDataListener implements Runnable {

        private final IncomingData<Data> incomingData;

        public MockNewDataListener(IncomingData<Data> incomingData) {
            this.incomingData = incomingData;
        }

        @Override
        public void run() {
            // consume new data
            for (Data newData : incomingData.drain()) {
                incomingData.getOwner().add(newData);
            }
            // actual rendering would go here (at this point data add listeners will have executed)
        }

    }

    private static class StreamProcessor extends Processor {

        int invocationCount;

        @Override
        public Response process(Request request, Execution execution) {
            invocationCount++;
            return execution.process(request);
        }

    }

}
