// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

/**
 * Provides information about the system in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 *
 * @author bratseth
 */
public class SystemInfo {

    private final Zone zone;
    private final Node node;

    public SystemInfo(Zone zone, Node node) {
        this.zone = zone;
        this.node = node;
    }

    /** Returns the zone this is running in */
    public Zone zone() { return zone; }


    /** Returns the node this is running on */
    public Node node() { return node; }

}
