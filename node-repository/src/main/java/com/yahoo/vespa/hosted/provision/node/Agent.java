package com.yahoo.vespa.hosted.provision.node;

/**
 * The enum of kinds of actions making changes to the system.
 * 
 * @author bratseth
 */
public enum Agent {
    system, application, operator, NodeRetirer
}
