// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import com.yahoo.text.StringUtilities;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * The state of a single node in the cluster state
 * TODO: The config aspects of this should move to ConfiguredNode
 * TODO: The type should be removed, as it is part of the owner.
 * TODO: Monitoring aspects should move to NodeInfo
 */
public class NodeState implements Cloneable {

    private final NodeType type;
    private State state = State.UP;
    private String description = "";
    private double capacity = 1.0;
    private int reliability =  1;
    private double initProgress = 1.0;
    private int minUsedBits = 16;
    private List<DiskState> diskStates = new ArrayList<>();
    /** When generating ideal states, we want to cheaply check if any disks are down in the nodestate. */
    private boolean anyDiskDown = false;
    private long startTimestamp = 0;

    public static double getListingBucketsInitProgressLimit() { return 0.01; }

    public NodeState(NodeType type, State state) {
        this.type = type;
        this.state = state;
        updateAnyDiskDownFlag();
    }

    private void updateAnyDiskDownFlag() {
        boolean anyDown = false;
        for (DiskState ds : diskStates) {
            if (!ds.getState().equals(State.UP)) {
                anyDown = true;
                break;
            }
        }
        anyDiskDown = anyDown;
    }

    public NodeState clone() {
        try{
            NodeState ns = (NodeState) super.clone();
            ns.diskStates = new ArrayList<>();
            for (DiskState s : diskStates) {
                ns.diskStates.add(s.clone());
            }
            return ns;
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
            || Math.abs(reliability - ns.reliability)   > 0.0000000001
            || Math.abs(initProgress - ns.initProgress) > 0.0000000001
            || startTimestamp != ns.startTimestamp
            || minUsedBits != ns.minUsedBits)
        {
            return false;
        }
        if (diskStates.size() == 0 && ns.diskStates.size() == 0) {
            // Everything is fine
        } else if (diskStates.size() == 0 || ns.diskStates.size() == 0) {
            NodeState nonEmptyState = (diskStates.size() == 0 ? ns : this);
            for (int i=0; i<nonEmptyState.diskStates.size(); ++i) {
                if (!nonEmptyState.diskStates.get(i).equals(new DiskState(State.UP))) {
                    return false;
                }
            }
        } else if (diskStates.size() != ns.diskStates.size()) {
            return false;
        } else {
            for (int i=0; i<diskStates.size(); ++i) {
                if (!diskStates.get(i).equals(ns.diskStates.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }
    public int hashCode() {
        return state.hashCode() ^ diskStates.hashCode() ^ new Double(capacity).hashCode() ^ new Double(reliability).hashCode();
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
        if (Math.abs(reliability - other.reliability) > 0.0000000001) return false;
        if (startTimestamp != other.startTimestamp) return false;

        // Init progress on different sides of the init progress limit boundary is not similar.
        if (considerInitProgress
            && type.equals(NodeType.STORAGE)
            && (initProgress < getListingBucketsInitProgressLimit()
                ^ other.initProgress < getListingBucketsInitProgressLimit()))
        {
            return false;
        }

        if (diskStates.size() == 0 && other.diskStates.size() == 0) {
            // Everything is fine
        } else if (diskStates.size() == 0 || other.diskStates.size() == 0) {
            NodeState nonEmptyState = (diskStates.size() == 0 ? other : this);
            for (int i=0; i<nonEmptyState.diskStates.size(); ++i) {
                if (!nonEmptyState.diskStates.get(i).equals(new DiskState(State.UP))) {
                    return false;
                }
            }
        } else if (diskStates.size() != other.diskStates.size()) {
            return false;
        } else {
            for (int i=0; i<diskStates.size(); ++i) {
                if (!diskStates.get(i).equals(other.diskStates.get(i))) {
                    return false;
                }
            }
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
        if (Math.abs(reliability - other.reliability) > 0.000000001) {
            diff.add(new Diff.Entry("reliability", reliability, other.reliability));
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
        if (diskStates.size() != other.diskStates.size()) {
            diff.add(new Diff.Entry("disks", diskStates.size(), other.diskStates.size()));
        } else {
            Diff diskDiff = new Diff();
            for (int i=0; i<diskStates.size(); ++i) {
                if (!diskStates.get(i).equals(other.diskStates.get(i))) {
                    diskDiff.add(new Diff.Entry(i, diskStates.get(i), other.diskStates.get(i)));
                }
            }
            if (diskDiff.differs()) {
                diff.add(new Diff.Entry("disks", diskDiff));
            }
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
    public NodeState setCapacity(double c) { this.capacity = c; return this; }

    public NodeState setReliability(int r) { this.reliability = r; return this; }
    public NodeState setInitProgress(double p) { this.initProgress = p; return this; }
    public NodeState setDescription(String desc) { this.description = desc; return this; }
    public NodeState setMinUsedBits(int u) { this.minUsedBits = u; return this; }
    public NodeState setState(State state) { this.state = state; return this; }
    public NodeState setStartTimestamp(long ts) { this.startTimestamp = ts; return this; }

    public double getCapacity() { return this.capacity; }
    public int getReliability() { return this.reliability; }
    public double getInitProgress() { return this.initProgress; }
    public boolean hasDescription() { return (description.length() > 0); }
    public String getDescription() { return description; }
    public State getState() { return this.state; }
    public int getMinUsedBits() { return minUsedBits; }
    public long getStartTimestamp() { return startTimestamp; }

    public boolean isAnyDiskDown() { return anyDiskDown; }
    public int getDiskCount() { return diskStates.size(); }
    public List<DiskState> getDiskStates() { return Collections.unmodifiableList(diskStates); }

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
        if (Math.abs(reliability - 1.0) > 0.000000001) {
            sb.append(compact ? ", r " : ", reliability ").append(reliability);
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

        if (diskStates.size() > 0) {
            if (compact) {
                boolean anyNonDefault = false;
                for (DiskState diskState : diskStates) {
                    anyNonDefault |= (!diskState.equals(new DiskState(State.UP)));
                }
                if (anyNonDefault) {
                    sb.append(",");
                    DiskState defaultDiskState = new DiskState(State.UP);
                    for (int i=0; i<diskStates.size(); ++i) {
                        if (!diskStates.get(i).equals(defaultDiskState)) {
                            sb.append(" d").append(i).append("(").append(diskStates.get(i).serialize("", false)).append(")");
                        }
                    }
                }
            } else {
                sb.append(", disk states:");
                for (int i=0; i<diskStates.size(); ++i) {
                    sb.append(" disk ").append(i).append(": ").append(diskStates.get(i).toString());
                }
            }
        }
        if (description.length() > 0) {
            sb.append(": ").append(description);
        }
        return sb.toString();
    }

    public NodeState setDiskCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must be positive. Was "+count+".");
        }
        diskStates.clear();
        for(int i=0;i<count;i++) {
            diskStates.add(new DiskState(State.UP, "", 1.0));
        }
        return this;
    }

    public NodeState setDiskState(int disk, DiskState state) throws IndexOutOfBoundsException {
        diskStates.set(disk, state);
        updateAnyDiskDownFlag();
        return this;
    }

    public DiskState getDiskState(int disk) throws IndexOutOfBoundsException {
        if (diskStates.isEmpty()) { // Zero disks, means unknown amount of disks, but all are up,
            return new DiskState();        // in which case we don't need to know amount of disks.
        }
        return diskStates.get(disk);
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
        if (Math.abs(reliability - 1.0) > 0.000000001) {
            if (empty) { empty = false; } else { sb.append(' '); }
            sb.append(prefix).append("r:").append(reliability);
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

        if (diskStates.size() > 0) {
            StringBuilder diskInfo = new StringBuilder();
            for(int i = 0; i < diskStates.size(); ++i) {
                String diskPrefix = prefix + "d." + i + ".";
                String disk = diskStates.get(i).serialize(diskPrefix, verbose);
                if (disk.length() > 0) {
                    diskInfo.append(' ').append(disk);
                }
            }
            String diskInfoStr = diskInfo.toString();
            if (verbose || diskInfoStr.length() > 0) {
                if (empty) { empty = false; } else { sb.append(' '); }
                sb.append(prefix).append("d:").append(diskStates.size());
                sb.append(diskInfoStr);
            } else if (nodeIdx == -1) {
                if (empty) { empty = false; } else { sb.append(' '); }
                sb.append(prefix).append("d:").append(diskStates.size());
            }
        }
        if ((verbose || nodeIdx == -1) && description.length() > 0) {
            if (!empty) { sb.append(' '); }
            sb.append(prefix).append("m:").append(StringUtilities.escape(description, ' '));
        }
        return sb.toString();
    }

    private static class DiskData {

        boolean empty = true;
        int diskIndex = 0;
        StringBuilder sb = new StringBuilder();

        public void addDisk(NodeState ns) throws ParseException {
            if (!empty) {
                while (diskIndex >= ns.diskStates.size()) {
                    ns.diskStates.add(new DiskState());
                }
                ns.diskStates.set(diskIndex, new DiskState(sb.toString()));
                empty = true;
                sb = new StringBuilder();
            }
        }
    }

    /** Creates an instance from the serialized form produced by serialize */
    public static NodeState deserialize(NodeType type, String serialized) throws ParseException {
        NodeState newState = new NodeState(type, State.UP);
        StringTokenizer st = new StringTokenizer(serialized, " \t\r\f\n", false);
        DiskData diskData = new DiskData();
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
                    newState.setCapacity(Double.valueOf(value));
                } catch (Exception e) {
                    throw new ParseException("Illegal capacity '" + value + "'. Capacity must be a positive floating point number", 0);
                }
                continue;
            case 'r':
                if (key.length() > 1) break;
                if (type != null && !type.equals(NodeType.STORAGE)) break;
                try{
                    newState.setReliability(Integer.valueOf(value));
                } catch (Exception e) {
                    throw new ParseException("Illegal reliability '" + value + "'. Reliability must be a positive integer number", 0);
                }
                continue;
            case 'i':
                if (key.length() > 1) break;
                try{
                    newState.setInitProgress(Double.valueOf(value));
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
                if (key.length() == 1) {
                    int size;
                    try{
                        size = Integer.valueOf(value);
                    } catch (Exception e) {
                        throw new ParseException("Invalid disk count '" + value + "'. Need a positive integer value", 0);
                    }
                    while (newState.diskStates.size() < size) {
                        newState.diskStates.add(new DiskState());
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
                if (diskIndex >= newState.diskStates.size()) {
                    throw new ParseException("Cannot index disk " + diskIndex + " of " + newState.diskStates.size(), 0);
                }
                if (diskData.diskIndex != diskIndex) {
                    diskData.addDisk(newState);
                }
                if (endp < 0) {
                    diskData.sb.append(" s:").append(value);
                } else {
                    diskData.sb.append(" ").append(key.substring(endp + 1)).append(':').append(value);
                }
                diskData.diskIndex = diskIndex;
                diskData.empty = false;
                continue;
            default:
                break;
            }
            // Ignore unknown tokens
        }
        diskData.addDisk(newState);
        newState.updateAnyDiskDownFlag();
        return newState;
    }

    public void verifyValidInSystemState(NodeType type) {
        if (!state.validCurrentNodeState(type)) {
            throw new IllegalArgumentException("State " + state + " cannot fit in system state for node of type: " + type);
        }
        if (type.equals(NodeType.DISTRIBUTOR) && Math.abs(capacity - 1.0) > 0.000000001) {
            throw new IllegalArgumentException("Capacity should not be set for a distributor node");
        }
        if (type.equals(NodeType.DISTRIBUTOR) && Math.abs(reliability - 1.0) > 0.000000001) {
            throw new IllegalArgumentException("Reliability should not be set for a distributor node");
        }
        if (type.equals(NodeType.DISTRIBUTOR) && !diskStates.isEmpty()) {
            throw new IllegalArgumentException("Disk states should not be set for a distributor node");
        }
    }

}
