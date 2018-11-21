// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class PeerPolicy {

    private final String peerName;
    private final List<RequiredPeerCredential> requiredCredentials;

    public PeerPolicy(String peerName, List<RequiredPeerCredential> requiredCredentials) {
        this.peerName = peerName;
        this.requiredCredentials = Collections.unmodifiableList(requiredCredentials);
    }

    public String peerName() {
        return peerName;
    }

    public List<RequiredPeerCredential> requiredCredentials() {
        return requiredCredentials;
    }

    @Override
    public String toString() {
        return "PeerPolicy{" +
                "peerName='" + peerName + '\'' +
                ", requiredCredentials=" + requiredCredentials +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerPolicy that = (PeerPolicy) o;
        return Objects.equals(peerName, that.peerName) &&
                Objects.equals(requiredCredentials, that.requiredCredentials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerName, requiredCredentials);
    }
}
