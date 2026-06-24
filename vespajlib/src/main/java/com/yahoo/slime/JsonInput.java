// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

interface JsonInput {
    byte getByte();
    boolean eof();
    void fail(String reason);
    boolean failed();
    byte[] getOffending();
    String getErrorMessage();
}
