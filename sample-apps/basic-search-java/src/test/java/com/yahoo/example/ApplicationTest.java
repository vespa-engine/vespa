// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.application.container.Processing;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import org.junit.Test;

import java.nio.file.FileSystems;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class ApplicationTest {

    @Test
    public void requireThatResultContainsHelloWorld() throws Exception {
        try (Application app = Application.fromApplicationPackage(
                FileSystems.getDefault().getPath("src/main/application"),
                Networking.disable)) {
            Processing processing = app.getJDisc("jdisc").processing();
            Response response = processing.process(ComponentSpecification.fromString("default"), new Request());
            assertThat(response.data().get(0).toString(), containsString("Hello, services!"));
        }
    }
}
