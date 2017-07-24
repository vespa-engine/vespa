package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;

/**
 * Created by olaa on 07/07/2017.
 */
public class NodeGenerator {

    private static void addStandardSpecifications(HardwareInfo node){
        node.setIpv4Connectivity(true);
        node.setIpv6Connectivity(true);
        node.setInterfaceSpeedMbs(1000);
    }

    public static HardwareInfo convertJsonModel(NodeJsonModel nodeJsonModel){
        HardwareInfo node = nodeJsonModel.copyToHardwareInfo();
        addStandardSpecifications(node);
        return node;
    }


}
