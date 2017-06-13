// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey.classvisitor;

import javax.ws.rs.Path;

/**
 * @author tonytv
 */
public class NestedClass {
    @Path("ignored")
    public static class Nested {}
}
