// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ${package};

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;


public class ExampleProcessorTest {
    @Test
    public void requireThatResultContainsHelloWorld() {
        ExampleProcessor processor = new ExampleProcessor();
        Chain<Processor> chain = new Chain<Processor>(processor);

        Execution execution = Execution.createRoot(chain, 0, Execution.Environment.createEmpty());
        Response response = execution.process(new Request());
        assertThat(response.data().get(0).toString(), containsString("Hello, world!"));
    }
}
