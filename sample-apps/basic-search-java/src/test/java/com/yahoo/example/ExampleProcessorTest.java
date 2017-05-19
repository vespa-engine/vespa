// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

public class ExampleProcessorTest {

    @Test
    public void requireThatResultContainsHelloWorld() {
        ExampleProcessorConfig.Builder config = new ExampleProcessorConfig.Builder().message("Hello, processor!");
        Processor processor = new ExampleProcessor(new ExampleProcessorConfig(config));

        Response response = newExecution(processor).process(new Request());
        assertThat(response.data().get(0).toString(), containsString("Hello, processor!"));
    }

    private static Execution newExecution(Processor... processors) {
        return Execution.createRoot(new Chain<>(processors), 0, Execution.Environment.createEmpty());
    }
}
