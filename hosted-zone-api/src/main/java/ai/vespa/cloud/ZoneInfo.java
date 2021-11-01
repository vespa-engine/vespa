// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.Objects;

/**
 * Provides information about the zone in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 * If you don't need any other information than the zone this should be preferred
 * to SystemInfo as it will never change at runtime and therefore does not
 * cause unnecessary reconstruction.
 *
 * @author bratseth
 */
public class ZoneInfo {

    private final Zone zone;

    public ZoneInfo(Zone zone) {
        Objects.requireNonNull(zone, "Zone cannot be null!");
        this.zone = zone;
    }

    /** Returns the zone this is running in */
    public Zone zone() { return zone; }

}
