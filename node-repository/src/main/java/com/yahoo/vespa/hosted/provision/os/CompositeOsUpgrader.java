package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;

import java.util.List;

/**
 * An implementation of {@link OsUpgrader} that delegates calls to multiple implementations.
 *
 * @author mpolden
 */
public record CompositeOsUpgrader(List<OsUpgrader> upgraders) implements OsUpgrader {

    public CompositeOsUpgrader(List<OsUpgrader> upgraders) {
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
