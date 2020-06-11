// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.Objects;

/**
 * The zone in which a cloud deployment may be running.
 * A zone is a combination of an environment and a region.
 *
 * @author bratseth
 */
public class Zone {

    private final Environment environment;

    private final String region;

    public Zone(Environment environment, String region) {
        this.environment = environment;
        this.region = region;
    }

    public Environment environment() { return environment; }
    public String region() { return region; }

    /** Returns the string environment.region */
    @Override
    public String toString() { return environment + "." + region; }

    @Override
    public int hashCode() { return Objects.hash(environment, region); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof Zone)) return false;
        Zone other = (Zone)o;
        return this.environment.equals(other.environment) && this.region.equals(other.region);
    }

    /**
     * Creates a zone from a string on the form environment.region
     *
     * @throws IllegalArgumentException if the given string is not a valid zone
     */
    public static Zone from(String zoneString) {
        String[] parts = zoneString.split("\\.");
        if (parts.length != 2)
            throw new IllegalArgumentException("A zone string must be on the form [environment].[region], but was '" + zoneString + "'");

        Environment environment;
        try {
            environment = Environment.valueOf(parts[0]);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid zone '" + zoneString + "': No environment named '" + parts[0] + "'");
        }
        return new Zone(environment, parts[1]);
    }

}
