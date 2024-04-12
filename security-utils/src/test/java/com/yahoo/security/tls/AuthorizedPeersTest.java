// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.yahoo.security.tls.RequiredPeerCredential.Field.CN;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
public class AuthorizedPeersTest {

    @Test
    void throws_exception_on_peer_policies_with_duplicate_names() {
        assertThrows(IllegalArgumentException.class, () -> {
            PeerPolicy peerPolicy1 = new PeerPolicy("duplicate-name", List.of(RequiredPeerCredential.of(CN, "mycfgserver")));
            PeerPolicy peerPolicy2 = new PeerPolicy("duplicate-name", List.of(RequiredPeerCredential.of(CN, "myclient")));
            new AuthorizedPeers(Set.of(peerPolicy1, peerPolicy2));
        });
    }

}
