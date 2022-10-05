// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import java.util.ArrayList;

/**
 *
 * Defines legal states for various uses. Split this into its own class such
 * that we can easily see what states are legal to use in what situations.
 * They double as disk states and node states nodes report they are in, and
 * wanted states set external sources.
 */
public enum State {

    // The order declares the ordinals, and defines what states are above/below others
    UNKNOWN     ("-", true, true, false, false, false, false, false), // This state is used by the fleetcontroller to indicate
                                                                      // that we have failed to contact the node. It should never be
                                                                      // sent out of the fleetcontroller
    MAINTENANCE ("m", false, false, true, true, false, true, true),
    DOWN        ("d", true, true, true, true, true, true, true), // Down is not valid reported state sent from the node itself.
    STOPPING    ("s", true, true, false, false, true, true, true),
    INITIALIZING("i", true, true, false, false, true, true, true),
    RETIRED     ("r", false, false, false, true, false, true, true),
    UP          ("u", true, true, true, true, true, true, true);

    private final boolean validClusterState;
    private final ArrayList<Boolean> validReportedNodeState = new ArrayList<>();
    private final ArrayList<Boolean> validWantedNodeState = new ArrayList<>();
    private final ArrayList<Boolean> validCurrentNodeState = new ArrayList<>();
    private final String serializedAs;

    State(String serialized, boolean validDistReported, boolean validStorReported, boolean validDistWanted,
          boolean validStorWanted, boolean validCluster, boolean validDistCurrent, boolean validStorCurrent) {
        validClusterState = validCluster;
        validReportedNodeState.add(validStorReported);
        validReportedNodeState.add(validDistReported);
        validWantedNodeState.add(validStorWanted);
        validWantedNodeState.add(validDistWanted);
        validCurrentNodeState.add(validStorCurrent);
        validCurrentNodeState.add(validDistCurrent);
        this.serializedAs = serialized;
    }

    public static State get(String serialized) {
        for (State s : values()) {
            if (s.serializedAs.equals(serialized)) { return s; }
        }
        throw new IllegalArgumentException("Invalid state '" + serialized + "'.");
    }

    public String serialize() { return serializedAs; }

    public boolean validClusterState() { return validClusterState; }
    public boolean validReportedNodeState(NodeType type) { return validReportedNodeState.get(type.ordinal()); }
    public boolean validWantedNodeState(NodeType type) { return validWantedNodeState.get(type.ordinal()); }
    public boolean validCurrentNodeState(NodeType type) { return validCurrentNodeState.get(type.ordinal()); }

    public boolean maySetWantedStateForThisNodeState(State s) { return (s.ordinal() <= ordinal()); }

    public boolean oneOf(String states) {
        for (char c : states.toCharArray()) {
            String s = "" + c;
            if (s.equals(serializedAs)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        String id = name();
        String lower = id.substring(1).toLowerCase();
        return id.charAt(0) + lower;
    }

}
