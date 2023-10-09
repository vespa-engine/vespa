// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.List;

/**
 * An implementation of {@link OsUpgrader} that delegates calls to multiple implementations.
 *
 * @author mpolden
 */
public class CompositeOsUpgrader extends OsUpgrader {

    private final List<OsUpgrader> upgraders;

    public CompositeOsUpgrader(NodeRepository nodeRepository, List<OsUpgrader> upgraders) {
        super(nodeRepository);
        this.upgraders = List.copyOf(upgraders);
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        upgraders.forEach(upgrader -> upgrader.upgradeTo(target));
    }

    @Override
    public void disableUpgrade(NodeType type) {
        upgraders.forEach(upgrader -> upgrader.disableUpgrade(type));
    }

}
