package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoJsonModel;

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
        checkIPv6 = nodeRepoJsonModel.getIpv6Address() != null;
    }

    public boolean isCheckIPv6() {
        return checkIPv6;
    }

}
