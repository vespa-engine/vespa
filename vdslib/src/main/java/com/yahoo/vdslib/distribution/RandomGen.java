// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

public class RandomGen extends java.util.Random {

    public RandomGen() {
        super();
    }

    public RandomGen(long seed) {
        super(seed);
    }

    public void setSeed(long seed){
        super.setSeed(seed);
        nextDouble();
    }
}
