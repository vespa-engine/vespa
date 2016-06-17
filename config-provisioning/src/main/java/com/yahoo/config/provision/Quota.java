// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author hmusum
 */
public class Quota {

    private final int numberOfHosts;

    public Quota() {
        this(Integer.MAX_VALUE);
    }

    public Quota(int numberOfHosts) {
        this.numberOfHosts = numberOfHosts;
    }

    public int getNumberOfHosts() {
        return numberOfHosts;
    }

}
