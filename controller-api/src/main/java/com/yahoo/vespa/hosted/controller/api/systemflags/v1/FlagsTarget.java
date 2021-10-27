// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents either configservers in a zone or controllers in a system.
 *
 * Defines the location and precedence of the flags data files for the given target.
 *
 * Naming rules for flags data files:
 * <ul>
 *  <li>zone specific: {@code <system>.<environment>.<region>.json}</li>
 *  <li>controller specific: {@code <system>.controller.json}</li>
 *  <li>environment specific: {@code <system>.<environment>.json}</li>
 *  <li>system specific: {@code <system>.json}</li>
 *  <li>global default: {@code default.json}</li>
 * </ul>
 *
 * @author bjorncs
 */
public interface FlagsTarget {

    List<String> flagDataFilesPrioritized();
    URI endpoint();
    Optional<AthenzIdentity> athenzHttpsIdentity();
    String asString();

    static Set<FlagsTarget> getAllTargetsInSystem(ZoneRegistry registry, boolean reachableOnly) {
        SystemName system = registry.system();
        Set<FlagsTarget> targets = new HashSet<>();
        ZoneList filteredZones = reachableOnly ? registry.zones().reachable() : registry.zones().all();
        for (ZoneApi zone : filteredZones.zones()) {
            targets.add(forConfigServer(registry, zone.getId()));
        }
        targets.add(forController(system));
        return targets;
    }

    static FlagsTarget forController(SystemName systemName) {
        return new ControllerFlagsTarget(systemName);
    }

    static FlagsTarget forConfigServer(ZoneRegistry registry, ZoneId zoneId) {
        return new ConfigServerFlagsTarget(
                registry.system(), zoneId, registry.getConfigServerVipUri(zoneId), registry.getConfigServerHttpsIdentity(zoneId));
    }

    static String defaultFile() { return jsonFile("default"); }
    static String systemFile(SystemName system) { return jsonFile(system.value()); }
    static String environmentFile(SystemName system, Environment environment) { return jsonFile(system.value() + "." + environment); }
    static String zoneFile(SystemName system, ZoneId zone) { return jsonFile(system.value() + "." + zone.environment().value() + "." + zone.region().value()); }
    static String controllerFile(SystemName system) { return jsonFile(system.value() + ".controller"); }

    private static String jsonFile(String nameWithoutExtension) { return nameWithoutExtension + ".json"; }
}


