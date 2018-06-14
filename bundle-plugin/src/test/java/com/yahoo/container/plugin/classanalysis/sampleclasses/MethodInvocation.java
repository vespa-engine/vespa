// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

/**
 * @author tonytv
 */
public class MethodInvocation {
    void invokeMethod() {
        Interface1 interface1 = null;
        @SuppressWarnings({ "unused", "null" })
        Object o = interface1.methodSignatureTest(null, null);
    }
}
