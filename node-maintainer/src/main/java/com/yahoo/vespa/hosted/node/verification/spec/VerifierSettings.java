package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoJsonModel;

/**
 * Created by sgrostad on 07/08/2017.
 * Contains information on what spec should be verified or not.
 */

public class VerifierSettings {

    private final boolean checkIPv6;

    public VerifierSettings(){
        this.checkIPv6 = true;
    }

    public VerifierSettings(NodeRepoJsonModel nodeRepoJsonModel){
        if (nodeRepoJsonModel.getIpv6Address() != null){
            checkIPv6 = true;
        }
        else {
            checkIPv6 = false;
        }
    }

    public boolean isCheckIPv6() {
        return checkIPv6;
    }

}
