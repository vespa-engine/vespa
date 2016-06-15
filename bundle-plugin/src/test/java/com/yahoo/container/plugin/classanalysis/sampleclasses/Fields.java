// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

import java.util.List;

/**
 * Input for class analysis tests.
 * @author tonytv
 */
public class Fields {
    @DummyAnnotation
    public String field1;
    public static List<Object> field2;

    public int field3;
    public int[] field4;
}
