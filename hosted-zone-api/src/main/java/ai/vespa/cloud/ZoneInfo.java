// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.Objects;

/**
 * Provides information about the zone context in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 * If you don't need any other information than what's present here this should be preferred
 * to SystemInfo as it will never change at runtime and therefore does not
 * cause unnecessary reconstruction.
 *
 * @author bratseth
 */
public class ZoneInfo {

    private static final ZoneInfo defaultInfo = new ZoneInfo(new ApplicationId("default", "default", "default"),
                                                             new Zone(Environment.prod, "default"));

    private final ApplicationId application;
    private final Zone zone;

    public ZoneInfo(ApplicationId application, Zone zone) {
        this.application = Objects.requireNonNull(application, "Application cannot be null!");
        this.zone = Objects.requireNonNull(zone, "Zone cannot be null!");
    }

    /** Returns the application this is running as part of */
    public ApplicationId application() { return application; }

    /** Returns the zone this is running in */
    public Zone zone() { return zone; }

    /** Returns the info instance used when no zone info is available because we're not running in a cloud context */
    public static ZoneInfo defaultInfo() { return defaultInfo; }

}
