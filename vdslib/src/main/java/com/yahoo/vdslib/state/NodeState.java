// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import com.yahoo.text.StringUtilities;

import java.text.ParseException;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * The state of a single node in the cluster state
 * TODO: The config aspects of this should move to ConfiguredNode
 * TODO: The type should be removed, as it is part of the owner.
 * TODO: Monitoring aspects should move to NodeInfo
 */
public class NodeState implements Cloneable {

    public static final String ORCHESTRATOR_RESERVED_DESCRIPTION = "Orchestrator";

    private final NodeType type;
    private State state;
    private String description = "";
    private float capacity = 1.0f;
    private float initProgress = 1.0f;
    private int minUsedBits = 16;
    private long startTimestamp = 0;

    public static float getListingBucketsInitProgressLimit() { return 0.01f; }

    public NodeState(NodeType type, State state) {
        this.type = type;
        this.state = state;
    }

    public NodeState clone() {
        try{
            return (NodeState) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Does not happen");
        }
    }

    /**
     * A state can not be forced to be in a state above it's reported state.
     * For instance, a down being down, cannot be forced up, but a node being down can be forced in maintenance.
     */
    public boolean above(NodeState other) {
        return (state.ordinal() > other.state.ordinal());
    }

    public boolean equals(Object o) {
        if (!(o instanceof NodeState)) { return false; }
        NodeState ns = (NodeState) o;
        if (state != ns.state
            || Math.abs(capacity - ns.capacity)         > 0.0000000001
            || Math.abs(initProgress - ns.initProgress) > 0.0000000001
            || startTimestamp != ns.startTimestamp
            || minUsedBits != ns.minUsedBits)
        {
            return false;
        }
        return true;
    }
    public int hashCode() {
        return state.hashCode() ^ Double.valueOf(capacity).hashCode();
    }

    /**
     * States are similar if the cluster state doesn't need to be updated due to a change.
     * Note that min dist bits may need to alter cluster state, but as we don't know at this point, we ignore it.
     * Cluster state will check for that.
     */
    public boolean similarTo(Object o) {
        if (!(o instanceof NodeState)) {
            return false;
        }
        return similarToImpl((NodeState)o, true);
    }

    public boolean similarToIgnoringInitProgress(final NodeState other) {
        return similarToImpl(other, false);
    }

    private boolean similarToImpl(final NodeState other, boolean considerInitProgress) {
        if (state != other.state) return false;
        if (Math.abs(capacity - other.capacity) > 0.0000000001) return false;
        if (startTimestamp != other.startTimestamp) return false;

        // Init progress on different sides of the init progress limit boundary is not similar.
        if (considerInitProgress
            && type.equals(NodeType.STORAGE)
            && (initProgress < getListingBucketsInitProgressLimit()
                ^ other.initProgress < getListingBucketsInitProgressLimit()))
        {
            return false;
        }
        return true;
    }

    public Diff getDiff(NodeState other) {
        Diff diff = new Diff();
        if (!state.equals(other.state)) {
            diff.add(new Diff.Entry("", state, other.state).bold());
        }
        if (Math.abs(capacity - other.capacity) > 0.000000001) {
            diff.add(new Diff.Entry("capacity", capacity, other.capacity));
        }
        if (minUsedBits != other.minUsedBits) {
            diff.add(new Diff.Entry("minUsedBits", minUsedBits, other.minUsedBits));
        }
        if (Math.abs(initProgress - other.initProgress) > 0.000000001 && state.equals(State.INITIALIZING) && other.state.equals(State.INITIALIZING)) {
            diff.add(new Diff.Entry("initProgress", initProgress, other.initProgress));
        }
        if (startTimestamp != other.startTimestamp) {
            diff.add(new Diff.Entry("startTimestamp", startTimestamp, other.startTimestamp));
        }
        if (!description.equals(other.description)) {
            diff.add(new Diff.Entry("description", description, other.description));
        }
        return diff;
    }

    public String getTextualDifference(NodeState other) {
        return getDiff(other).toString();
    }

    /** Capacity is set by deserializing a node state. This seems odd, as it is config */
    public NodeState setCapacity(float c) { this.capacity = c; return this; }

    public NodeState setInitProgress(float p) { this.initProgress = p; return this; }
    public NodeState setDescription(String desc) { this.description = desc; return this; }
    public NodeState setMinUsedBits(int u) { this.minUsedBits = u; return this; }
    public NodeState setState(State state) { this.state = state; return this; }
    public NodeState setStartTimestamp(long ts) { this.startTimestamp = ts; return this; }

    public double getCapacity() { return this.capacity; }
    public double getInitProgress() { return this.initProgress; }
    public boolean hasDescription() { return (description.length() > 0); }
    public String getDescription() { return description; }
    public State getState() { return this.state; }
    public int getMinUsedBits() { return minUsedBits; }
    public long getStartTimestamp() { return startTimestamp; }

    public String toString() { return toString(false); }

    public String toString(boolean compact) {
        StringBuilder sb = new StringBuilder();
        if (compact) {
            sb.append(state.serialize().toUpperCase());
        } else {
            sb.append(state);
        }
        if (Math.abs(capacity - 1.0) > 0.000000001) {
            sb.append(compact ? ", c " : ", capacity ").append(compact ? String.format(Locale.ENGLISH, "%.3g", capacity) : capacity);
        }
        if (state.equals(State.INITIALIZING)) {
            sb.append(compact ? ", i " : ", init progress ").append(compact ? String.format(Locale.ENGLISH, "%.3g", initProgress) : initProgress);
            if (type.equals(NodeType.STORAGE)) {
                if (initProgress < getListingBucketsInitProgressLimit()) {
                    sb.append(compact ? " (ls)" : " (listing files)");
                } else {
                    sb.append(compact ? " (read)" : " (reading file headers)");
                }
            }
        }
        if (startTimestamp > 0) {
            sb.append(compact ? ", t " : ", start timestamp ").append(startTimestamp);
        }
        if (minUsedBits != 16) {
            sb.append(compact ? ", b " : ", minimum used bits ").append(minUsedBits);
        }
        if (description.length() > 0) {
            sb.append(": ").append(description);
        }
        return sb.toString();
    }

    public String serialize() { return serialize(-1, false); }
    public String serialize(boolean verbose) { return serialize(-1, verbose); }
    public String serialize(int nodeIdx, boolean verbose) {
        boolean empty = true;
        StringBuilder sb = new StringBuilder();
        String prefix = (nodeIdx == -1 ? "" : "." + nodeIdx + ".");
        if (state != State.UP){
            empty = false;
            sb.append(prefix).append("s:").append(state.serialize());
        }
        if (Math.abs(capacity - 1.0) > 0.000000001) {
            if (empty) { empty = false; } else { sb.append(' '); }
            sb.append(prefix).append("c:").append(capacity);
        }
        if (state == State.INITIALIZING) {
            sb.append(' ');
            sb.append(prefix).append("i:").append(initProgress);
        }
        if (startTimestamp != 0) {
            if (empty) { empty = false; } else { sb.append(' '); }
            sb.append(prefix).append("t:").append(startTimestamp);
        }
        if (nodeIdx == -1 && minUsedBits != 16) {
            if (empty) { empty = false; } else { sb.append(' '); }
            sb.append(prefix).append("b:").append(minUsedBits);
        }

        if ((verbose || nodeIdx == -1) && description.length() > 0) {
            if (!empty) { sb.append(' '); }
            sb.append(prefix).append("m:").append(StringUtilities.escape(description, ' '));
        }
        return sb.toString();
    }

    /** Creates an instance from the serialized form produced by serialize */
    public static NodeState deserialize(NodeType type, String serialized) throws ParseException {
        NodeState newState = new NodeState(type, State.UP);
        StringTokenizer st = new StringTokenizer(serialized, " \t\r\f\n", false);
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            int index = token.indexOf(':');
            if (index < 0) {
                throw new ParseException("Token " + token + " does not contain ':': " + serialized, 0);
            }
            String key = token.substring(0, index);
            String value = token.substring(index + 1);
            if (key.length() > 0) switch (key.charAt(0)) {
            case 's':
                if (key.length() > 1) break;
                newState.setState(State.get(value));
                continue;
            case 'b':
                if (key.length() > 1) break;
                newState.setMinUsedBits(Integer.parseInt(value));
                continue;
            case 'c':
                if (key.length() > 1) break;
                if (type != null && !type.equals(NodeType.STORAGE)) break;
                try{
                    newState.setCapacity(Float.valueOf(value));
                } catch (Exception e) {
                    throw new ParseException("Illegal capacity '" + value + "'. Capacity must be a positive floating point number", 0);
                }
                continue;
            case 'i':
                if (key.length() > 1) break;
                try{
                    newState.setInitProgress(Float.valueOf(value));
                } catch (Exception e) {
                    throw new ParseException("Illegal init progress '" + value + "'. Init progress must be a floating point number from 0.0 to 1.0", 0);
                }
                continue;
            case 't':
                if (key.length() > 1) break;
                try{
                    newState.setStartTimestamp(Long.valueOf(value));
                    if (newState.getStartTimestamp() < 0) throw new Exception();
                } catch (Exception e) {
                    throw new ParseException("Illegal start timestamp " + value + ". Start timestamp must be 0 or a positive long.", 0);
                }
                continue;
            case 'm':
                if (key.length() > 1) break;
                newState.setDescription(StringUtilities.unescape(value));
                continue;
            case 'd':
                if (type != null && !type.equals(NodeType.STORAGE)) break;
                int size = 0;
                if (key.length() == 1) {
                    try{
                        size = Integer.valueOf(value);
                    } catch (Exception e) {
                        throw new ParseException("Invalid disk count '" + value + "'. Need a positive integer value", 0);
                    }
                    continue;
                }
                if (key.charAt(1) != '.') break;
                int diskIndex;
                int endp = key.indexOf('.', 2);
                String indexStr = (endp < 0 ? key.substring(2) : key.substring(2, endp));
                try{
                    diskIndex = Integer.valueOf(indexStr);
                } catch (Exception e) {
                    throw new ParseException("Invalid disk index '" + indexStr + "'. need a positive integer value", 0);
                }
                if (diskIndex >= size) {
                    throw new ParseException("Cannot index disk " + diskIndex + " of " + size, 0);
                }
                continue;
            default:
                break;
            }
            // Ignore unknown tokens
        }
        return newState;
    }

    public void verifyValidInSystemState(NodeType type) {
        if (!state.validCurrentNodeState(type)) {
            throw new IllegalArgumentException("State " + state + " cannot fit in system state for node of type: " + type);
        }
        if (type.equals(NodeType.DISTRIBUTOR) && Math.abs(capacity - 1.0) > 0.000000001) {
            throw new IllegalArgumentException("Capacity should not be set for a distributor node");
        }
    }

}
