package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoJsonModel;

/**
 * Created by sgrostad on 07/08/2017.
 * Contains information on what spec should be verified or not.
 */

public class VerifierSettings {

    private final boolean ipv6;

    public VerifierSettings(){
        this.ipv6 = true;
    }

    public VerifierSettings(NodeRepoJsonModel nodeRepoJsonModel){
        if (nodeRepoJsonModel.getIpv6Address() != null){
            ipv6 = true;
        }
        else {
            ipv6 = false;
        }
    }

    public boolean isIpv6() {
        return ipv6;
    }

}
