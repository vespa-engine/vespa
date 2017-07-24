package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.hardware.HardwareInfo;

/**
 * Created by olaa on 07/07/2017.
 */
public class NodeGenerator {

    private final NodeJsonModel jsonModel;

    public NodeGenerator(NodeJsonModel jsonModel) {
        this.jsonModel = jsonModel;
    }

    private void addStandardSpecifications(HardwareInfo node){
        node.setIpv4Connectivity(true);
        node.setIpv6Connectivity(true);
        node.setInterfaceSpeedMbs(1000);
    }

    public HardwareInfo convertJsonModel(){
        HardwareInfo node = jsonModel.copyToHardwareInfo();
        addStandardSpecifications(node);
        return node;
    }


}
