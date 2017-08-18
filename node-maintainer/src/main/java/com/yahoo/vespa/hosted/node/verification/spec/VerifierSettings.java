package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoJsonModel;

/**
 * Contains information on what spec should be verified or not.
 * 
 * @author sgrostad
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
