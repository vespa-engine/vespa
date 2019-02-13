// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

/**
 * The enum of kinds of actions making changes to the system.
 * 
 * @author bratseth
 */
public enum Agent {
    system, application, operator, NodeRetirer, NodeFailer
}
