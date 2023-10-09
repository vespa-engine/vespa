// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

public class OtherMasterException extends NotMasterException {

    private final String masterHost;
    private final int masterPort;

    public OtherMasterException(String masterHost, int masterPort) {
        super("Cluster controller not master. Use master at " + masterHost + ":" + masterPort + ".");
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    public String getHost() { return masterHost; }
    public int getPort() { return masterPort; }

}
