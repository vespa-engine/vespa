// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

/**
* @author thomasg
*/
class Timer {
    int secs() { return (int)(milliSecs() / 1000); }
    long milliSecs() { return System.currentTimeMillis(); }
}
