// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

/**
 * There is one logd running on each Vespa host, and one instance of
 * this class is therefore created by each instance of class {@link
 * Host}.
 *
 * @author gjoranv
 */
public class Logd extends AbstractService {

    /**
     * Creates a new Logd instance.
     */
    public Logd(Host host) {
        super(host, "logd");
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
    }

    /**
     * Logd does not need any ports.
     *
     * @return The number of ports reserved by the logd
     */
    public int getPortCount() { return 0; }

     /**
     * @return The command used to start logd
     */
    public String getStartupCommand() { return "exec sbin/logd"; }

}
