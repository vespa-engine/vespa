// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;

/**
 * Converts a NodeSpec object to a HardwareInfo object.
 * 
 * @author olaaun
 * @author sgrostad
 */
public class NodeJsonConverter {

    private static void addStandardSpecifications(HardwareInfo nodeRepoHardwareInfo) {
        nodeRepoHardwareInfo.setInterfaceSpeedMbs(1000);
    }

    private static void setIpv6Interface(NodeSpec nodeSpec, HardwareInfo nodeRepoHardwareInfo) {
        if (nodeSpec.getIpv6Address() != null) {
            nodeRepoHardwareInfo.setIpv6Interface(true);
        }
    }

    private static void setIpv4Interface(NodeSpec nodeSpec, HardwareInfo nodeRepoHardwareInfo) {
        if (nodeSpec.getIpv4Address() != null) {
            nodeRepoHardwareInfo.setIpv4Interface(true);
        }
    }

    public static HardwareInfo convertJsonModelToHardwareInfo(NodeSpec nodeSpec) {
        HardwareInfo nodeRepoHardwareInfo = nodeSpec.copyToHardwareInfo();
        addStandardSpecifications(nodeRepoHardwareInfo);
        setIpv4Interface(nodeSpec, nodeRepoHardwareInfo);
        setIpv6Interface(nodeSpec, nodeRepoHardwareInfo);
        return nodeRepoHardwareInfo;
    }

}
