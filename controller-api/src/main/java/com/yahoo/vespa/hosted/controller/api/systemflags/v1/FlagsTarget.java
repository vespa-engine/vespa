// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagDefinition;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.flags.FetchVector.Dimension.CLOUD;
import static com.yahoo.vespa.flags.FetchVector.Dimension.ENVIRONMENT;
import static com.yahoo.vespa.flags.FetchVector.Dimension.SYSTEM;
import static com.yahoo.vespa.flags.FetchVector.Dimension.ZONE_ID;

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

    FlagData partiallyResolveFlagData(FlagData data);

    static Set<FlagsTarget> getAllTargetsInSystem(ZoneRegistry registry, boolean reachableOnly) {
        Set<FlagsTarget> targets = new HashSet<>();
        ZoneList filteredZones = reachableOnly ? registry.zones().reachable() : registry.zones().all();
        for (ZoneApi zone : filteredZones.zones()) {
            targets.add(forConfigServer(registry, zone));
        }
        targets.add(forController(registry.systemZone()));
        return targets;
    }

    static FlagsTarget forController(ZoneApi controllerZone) {
        return new ControllerFlagsTarget(controllerZone.getSystemName(), controllerZone.getCloudName(), controllerZone.getVirtualId());
    }

    static FlagsTarget forConfigServer(ZoneRegistry registry, ZoneApi zone) {
        return new ConfigServerFlagsTarget(registry.system(),
                                           zone.getCloudName(),
                                           zone.getVirtualId(),
                                           registry.getConfigServerVipUri(zone.getVirtualId()),
                                           registry.getConfigServerHttpsIdentity(zone.getVirtualId()));
    }

    static String defaultFile() { return jsonFile("default"); }
    static String systemFile(SystemName system) { return jsonFile(system.value()); }
    static String environmentFile(SystemName system, Environment environment) { return jsonFile(system.value() + "." + environment); }
    static String zoneFile(SystemName system, ZoneId zone) { return jsonFile(system.value() + "." + zone.environment().value() + "." + zone.region().value()); }
    static String controllerFile(SystemName system) { return jsonFile(system.value() + ".controller"); }

    /** Return true if the filename applies to the system.  Throws on invalid filename format. */
    static boolean filenameForSystem(String filename, SystemName system) throws FlagValidationException {
        if (filename.equals(defaultFile())) return true;

        String[] parts = filename.split("\\.", -1);
        if (parts.length < 2) throw new FlagValidationException("Invalid flag filename: " + filename);

        if (!parts[parts.length - 1].equals("json")) throw new FlagValidationException("Invalid flag filename: " + filename);

        SystemName systemFromFile;
        try {
            systemFromFile = SystemName.from(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new FlagValidationException("First part of flag filename is neither 'default' nor a valid system: " + filename);
        }
        if (!SystemName.hostedVespa().contains(systemFromFile))
            throw new FlagValidationException("Unknown system in flag filename: " + filename);
        if (!systemFromFile.equals(system)) return false;

        if (parts.length == 2) return true;  // systemFile

        if (parts.length == 3) {
            if (parts[1].equals("controller")) return true;  // controllerFile
            try {
                Environment.from(parts[1]);
            } catch (IllegalArgumentException e) {
                throw new FlagValidationException("Invalid environment in flag filename: " + filename);
            }
            return true;  // environmentFile
        }

        if (parts.length == 4) {
            try {
                Environment.from(parts[1]);
            } catch (IllegalArgumentException e) {
                throw new FlagValidationException("Invalid environment in flag filename: " + filename);
            }
            try {
                RegionName.from(parts[2]);
            } catch (IllegalArgumentException e) {
                throw new FlagValidationException("Invalid region in flag filename: " + filename);
            }
            return true;  // zoneFile
        }

        throw new FlagValidationException("Invalid flag filename: " + filename);
    }

    /** Partially resolve inter-zone dimensions, except those dimensions defined by the flag for a controller zone. */
    static FlagData partialResolve(FlagData data, SystemName system, CloudName cloud, ZoneId virtualZoneId) {
        Set<FetchVector.Dimension> flagDimensions =
                virtualZoneId.equals(ZoneId.ofVirtualControllerZone()) ?
                Flags.getFlag(data.id())
                     .map(FlagDefinition::getDimensions)
                     .map(Set::copyOf)
                     // E.g. testing: Assume unknown flag should resolve any and all dimensions below
                     .orElse(EnumSet.noneOf(FetchVector.Dimension.class)) :
                EnumSet.noneOf(FetchVector.Dimension.class);

        var fetchVector = new FetchVector();
        if (!flagDimensions.contains(CLOUD)) fetchVector = fetchVector.with(CLOUD, cloud.value());
        if (!flagDimensions.contains(ENVIRONMENT)) fetchVector = fetchVector.with(ENVIRONMENT, virtualZoneId.environment().value());
        fetchVector = fetchVector.with(SYSTEM, system.value());
        if (!flagDimensions.contains(ZONE_ID)) fetchVector = fetchVector.with(ZONE_ID, virtualZoneId.value());
        return fetchVector.isEmpty() ? data : data.partialResolve(fetchVector);
    }

    private static String jsonFile(String nameWithoutExtension) { return nameWithoutExtension + ".json"; }
}


