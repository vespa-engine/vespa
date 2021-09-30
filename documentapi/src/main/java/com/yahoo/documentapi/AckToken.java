// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Token to use to acknowledge data for visiting.
 *
 * @author Thomas Gundersen
 */
public class AckToken {

    public Object ackObject;

    /**
     * Creates ack token from the supplied parameter.
     *
     * @param ackObject the object to use to ack data
     */
    public AckToken(Object ackObject) {
        this.ackObject = ackObject;
    }
}
