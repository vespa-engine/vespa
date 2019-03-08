// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;

import java.net.Inet4Address;
import java.util.Optional;

/**
 * @author freva
 */
public class RetireIPv4OnlyNodes implements RetirementPolicy {
    private final Zone zone;

    public RetireIPv4OnlyNodes(Zone zone) {
        this.zone = zone;
    }

    @Override
    public boolean isActive() {
        if(zone.system() == SystemName.cd) {
            return zone.environment() == Environment.dev || zone.environment() == Environment.prod;
        }

        if (zone.system() == SystemName.main) {
            if (zone.region().equals(RegionName.from("us-east-3"))) {
                return zone.environment() == Environment.perf || zone.environment() == Environment.prod;
            } else if (zone.region().equals(RegionName.from("us-west-1"))) {
                return zone.environment() == Environment.prod;
            } else if (zone.region().equals(RegionName.from("us-central-1"))) {
                return zone.environment() == Environment.prod;
            } else if (zone.region().equals(RegionName.from("ap-southeast-1"))) {
                return zone.environment() == Environment.prod;
            } else if (zone.region().equals(RegionName.from("ap-northeast-1"))) {
                return zone.environment() == Environment.prod;
            } else if (zone.region().equals(RegionName.from("ap-northeast-2"))) {
                return zone.environment() == Environment.prod;
            } else if (zone.region().equals(RegionName.from("eu-west-1"))) {
                return zone.environment() == Environment.prod;
            }
        }

        return false;
    }

    @Override
    public Optional<String> shouldRetire(Node node) {
        if (node.flavor().environment() == Flavor.Environment.VIRTUAL_MACHINE) return Optional.empty();
        boolean shouldRetire = node.ipAddresses().stream()
                .map(InetAddresses::forString)
                .allMatch(address -> address instanceof Inet4Address);

        return shouldRetire ? Optional.of("Node is IPv4-only") : Optional.empty();
    }
}
