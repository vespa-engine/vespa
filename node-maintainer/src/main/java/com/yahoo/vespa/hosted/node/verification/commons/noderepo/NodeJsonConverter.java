package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;

/**
 * Created by olaa on 07/07/2017.
 * Converts a NodeRepoJsonModel object to a HardwareInfo object.
 */
public class NodeJsonConverter {

    private static void addStandardSpecifications(HardwareInfo nodeRepoHardwareInfo) {
        nodeRepoHardwareInfo.setInterfaceSpeedMbs(1000);
    }

    protected static void setIpv6Interface(NodeRepoJsonModel nodeRepoJsonModel, HardwareInfo nodeRepoHardwareInfo) {
        if (nodeRepoJsonModel.getIpv6Address() != null) {
            nodeRepoHardwareInfo.setIpv6Interface(true);
        }
    }

    protected static void setIpv4Interface(NodeRepoJsonModel nodeRepoJsonModel, HardwareInfo nodeRepoHardwareInfo) {
        if (nodeRepoJsonModel.getIpv4Address() != null) {
            nodeRepoHardwareInfo.setIpv4Interface(true);
        }
    }

    public static HardwareInfo convertJsonModelToHardwareInfo(NodeRepoJsonModel nodeRepoJsonModel) {
        HardwareInfo nodeRepoHardwareInfo = nodeRepoJsonModel.copyToHardwareInfo();
        addStandardSpecifications(nodeRepoHardwareInfo);
        setIpv4Interface(nodeRepoJsonModel, nodeRepoHardwareInfo);
        setIpv6Interface(nodeRepoJsonModel, nodeRepoHardwareInfo);
        return nodeRepoHardwareInfo;
    }

}
