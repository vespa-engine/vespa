// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.test.jars.jersey.resources;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class TestResourceBase {
    @GET
    @Produces({MediaType.TEXT_PLAIN})
    public String get() {
        return content(getClass());
    }

    public static String content(Class<? extends TestResourceBase> clazz) {
        return "Response from " + clazz.getName();
    }
}
