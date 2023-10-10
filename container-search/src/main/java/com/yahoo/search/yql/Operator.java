// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

interface Operator {

    String name();

    void checkArguments(Object... args);

}
