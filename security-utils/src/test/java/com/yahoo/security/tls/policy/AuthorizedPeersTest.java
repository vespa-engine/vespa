// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static com.yahoo.security.tls.policy.RequiredPeerCredential.Field.CN;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

/**
 * @author bjorncs
 */
public class AuthorizedPeersTest {

    @Test(expected = IllegalArgumentException.class)
    public void throws_exception_on_peer_policies_with_duplicate_names() {
        List<RequiredPeerCredential> requiredPeerCredential = singletonList(RequiredPeerCredential.of(CN, "mycfgserver"));
        PeerPolicy peerPolicy1 = new PeerPolicy("duplicate-name", singleton(new Role("role")), requiredPeerCredential);
        PeerPolicy peerPolicy2 = new PeerPolicy("duplicate-name", singleton(new Role("anotherrole")), requiredPeerCredential);
        new AuthorizedPeers(new HashSet<>(asList(peerPolicy1, peerPolicy2)));
    }

}
