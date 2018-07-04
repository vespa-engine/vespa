// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
public class ChainCycleException extends RuntimeException {

    private final List<?> components;

    public ChainCycleException(List<?> components) {
        this.components = ImmutableList.copyOf(components);
    }

    public List<?> components() {
        return components;
    }

}
