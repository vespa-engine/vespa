// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test;

import static org.junit.Assert.*;

import org.junit.Test;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.test.documentation.AsyncDataProcessingInitiator;
import com.yahoo.processing.test.documentation.AsyncDataProducer;
import com.yahoo.processing.test.documentation.ExampleProcessor;
import com.yahoo.processing.test.documentation.Federator;

/**
 * See to it we can actually run the examples in the doc.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class DocumentationTestCase {

    @SuppressWarnings("unchecked")
    @Test
    public final void test() {
        Processor p = new ExampleProcessor();
        Chain<Processor> basic = new Chain<>(p);
        Processor initiator = new AsyncDataProcessingInitiator(basic);
        Chain<Processor> postProcessing = new Chain<>(initiator);
        Execution e = Execution.createRoot(postProcessing, 0, Execution.Environment.createEmpty());
        Response r = e.process(new Request());
        // just adds a listener to the result returned from basic
        assertEquals(0, r.data().asList().size());
        Processor producer = new AsyncDataProducer();
        Chain<Processor> asyncChain = new Chain<>(producer);
        Processor federator = new Federator(basic, asyncChain);
        e = Execution.createRoot(federator, 0, Execution.Environment.createEmpty());
        r = e.process(new Request());
        assertEquals(2, r.data().asList().size());
    }

}
