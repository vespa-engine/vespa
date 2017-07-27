package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;

/**
 * Created by olaa on 07/07/2017.
 * Converts a NodeRepoJsonModel object to a HardwareInfo object.
 */
public class NodeJsonConverter {

    private static void addStandardSpecifications(HardwareInfo nodeRepoHardwareInfo) {
        nodeRepoHardwareInfo.setIpv4Connectivity(true);
        nodeRepoHardwareInfo.setInterfaceSpeedMbs(1000);
    }

    protected static void setIpv6AddressConnectivity(NodeRepoJsonModel nodeRepoJsonModel, HardwareInfo nodeRepoHardwareInfo){
        if (nodeRepoJsonModel.getIpv6Address() != null){
            nodeRepoHardwareInfo.setIpv6Connectivity(true);
        }
    }

    public static HardwareInfo convertJsonModelToHardwareInfo(NodeRepoJsonModel nodeRepoJsonModel) {
        HardwareInfo nodeRepoHardwareInfo = nodeRepoJsonModel.copyToHardwareInfo();
        addStandardSpecifications(nodeRepoHardwareInfo);
        setIpv6AddressConnectivity(nodeRepoJsonModel, nodeRepoHardwareInfo);
        return nodeRepoHardwareInfo;
    }

}
