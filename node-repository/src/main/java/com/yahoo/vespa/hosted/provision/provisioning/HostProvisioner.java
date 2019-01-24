// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;

/**
 * @author freva
 */
public interface HostProvisioner {

    List<Node> provisionHosts(int numHosts, Flavor nodeFlavor, int numNodesOnHost);

    void provisioning(Node node);

    void deprovision(Node node);
}
