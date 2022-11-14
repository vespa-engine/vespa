// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;

import java.util.Objects;

/**
 * The target OS version for a {@link NodeType}.
 *
 * @author mpolden
 */
public record OsVersionTarget(NodeType nodeType, Version version) {

    public OsVersionTarget {
        Objects.requireNonNull(nodeType);
        Objects.requireNonNull(version);
    }

}
