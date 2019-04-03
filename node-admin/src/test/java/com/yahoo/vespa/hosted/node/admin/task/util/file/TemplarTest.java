// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 */
public class TemplarTest {
    @Test
    public void test() {
        Templar templar = new Templar("x y <%= foo %>, some other <%=bar%> text");
        templar.set("foo", "fidelity")
                .set("bar", "halimov")
                .set("not", "used");

        assertEquals("x y fidelity, some other halimov text", templar.resolve());
    }
}