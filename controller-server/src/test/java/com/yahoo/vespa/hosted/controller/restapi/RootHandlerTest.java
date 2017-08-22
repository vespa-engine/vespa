// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.container.handler.Request;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author bratseth
 */
public class RootHandlerTest extends ControllerContainerTest {
    
    @Test
    public void testRootRequest() throws IOException {
        ContainerTester tester = new ContainerTester(container, 
                                                     "src/test/java/com/yahoo/vespa/hosted/controller/restapi/");
        tester.assertResponse(new Request("http://localhost:8080/"), 
                              new File("root-response.json"), 200);
    }
    
}
