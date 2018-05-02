// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

import java.util.*;
import java.text.ParseException;

/**
 * Represent a group in the tree structure of groups in hierarchical setup of VDS nodes.
 */
public class Group implements Comparable<Group> {

    private String name;
    private Group parent = null;
    private int index;
    private int distributionHash;
    private Distribution distribution = null;
    private double capacity;
    private Map<Integer, Group> subgroups;
    private List<ConfiguredNode> nodes;

    public Group(int index, String name) {
        this.name = name;
        this.index = index;
        this.distributionHash = 0;
        this.distribution = null;
        this.capacity = 1;
        this.nodes = new ArrayList<>();
        this.subgroups = null;
    }

    public Group(int index, String name, Distribution d) {
        this.name = name;
        this.index = index;
        this.distributionHash = 0;
        this.distribution = d;
        this.capacity = 1;
        this.nodes = null;
        this.subgroups = new TreeMap<>();
    }

    private String getPathWithSeparator(String separator) {
        if (parent != null) {
            final String prefix = parent.getPathWithSeparator(separator);
            return prefix.isEmpty() ? name : prefix + separator + name;
        } else {
            return "";
        }
    }

    public String getPath() {
        return getPathWithSeparator(".");
    }

    public String getUnixStylePath() {
        return "/" + getPathWithSeparator("/");
    }

    @Override
    public int compareTo(Group o) {
        return new Integer(index).compareTo(o.getIndex());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Group)) { return false; }
        Group other = (Group) o;
        if ( ! name.equals(other.name)
            || index != other.index
            || (distribution == null ^ other.distribution == null)
            || (distribution != null &&  ! distribution.equals(other.distribution))
            || Math.abs(capacity - other.capacity) > 0.0000001
            || (subgroups == null ^ other.subgroups == null)
            || (subgroups != null && !subgroups.equals(other.subgroups))
            || (nodes == null ^ other.nodes == null)
            || (nodes != null && !nodes.equals(other.nodes)))
        {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode() +
                17 * index +
                23 * distribution.hashCode() +
                43 * subgroups.hashCode() +
                47 * nodes.hashCode();

    }

    @Override
    public String toString() {
        return toString("");
    }

    public String toString(String indent) {
        StringBuffer sb = new StringBuffer();
        sb.append("Group(name: ").append(name).append(", index: ").append(index);
        if (distribution != null) sb.append(", distribution: ").append(distribution);
        if (Math.abs(capacity - 1.0) > 0.0000001) sb.append(", capacity: ").append(capacity);
        if (nodes != null) {
            sb.append(", nodes( ");
            for (ConfiguredNode node : nodes) {
                sb.append(node.index()).append(' ');
            }
            sb.append(")");
        }
        if (subgroups != null) {
            sb.append(", subgroups: ").append(subgroups.size());
        }
        sb.append(") {");
        if (subgroups != null && subgroups.size() > 0) {
            for (Group g : subgroups.values()) {
                sb.append("\n").append(indent).append("  ");
                sb.append(g.toString(indent + "  "));
            }
        }
        sb.append("\n").append(indent).append("}");
        return sb.toString();
    }

    public void addSubGroup(Group g) {
        if (distribution == null) {
            throw new IllegalStateException("Cannot add sub groups to a node without distribution set.");
        }
        if (subgroups.containsKey(g.getIndex())) {
            throw new IllegalStateException("A subgroup with index " + g.getIndex() + " already exist.");
        }
        if (nodes != null) {
            throw new IllegalStateException("Cannot add subgroup to leaf group with nodes");
        }
        g.parent = this;
        subgroups.put(g.getIndex(), g);
    }

    public void setCapacity(double c) { capacity = c; }

    public void setNodes(List<ConfiguredNode> nodes) {
        if (distribution != null) {
            throw new IllegalStateException("Cannot add nodes to non-leaf group with distribution set");
        }
        if (subgroups != null) {
            throw new IllegalStateException("Cannot add nodes to group with children");
        }
        this.nodes = new ArrayList<>(nodes);
        Collections.sort(this.nodes);
    }

    public String getName() { return name; }
    public int getIndex() { return index; }
    public List<ConfiguredNode> getNodes() { return Collections.unmodifiableList(nodes); }
    public Map<Integer, Group> getSubgroups() { return Collections.unmodifiableMap(subgroups); }
    public double getCapacity() { return capacity; }
    public int getDistributionHash() { return distributionHash; }
    public boolean isLeafGroup() { return (distribution == null); }
    public Distribution getDistribution() { return distribution; }

    /**
     * The distribution hashes is hopefully unique numbers for each group that is used to adjust the seed generated
     * for groups. This is called by Distribution during configuration on the root node. It recursively generates all hashes.
     */
    void calculateDistributionHashValues() {
        calculateDistributionHashValues(0x8badf00d);
    }

    private void calculateDistributionHashValues(int parentHash) {
        distributionHash = parentHash ^ (1664525 * index + 1013904223);
        if (subgroups == null) return;
        for (Map.Entry<Integer, Group> entry : subgroups.entrySet()) {
            entry.getValue().calculateDistributionHashValues(distributionHash);
        }
    }

    public Group getGroupForNode(int index) {
        if (nodes != null) {
            for (ConfiguredNode node : nodes) {
                if (node.index() == index) {
                    return this;
                }
            }
        }

        if (subgroups != null) {
            for (Group group : subgroups.values()) {
                Group retVal = group.getGroupForNode(index);
                if (retVal != null) {
                    return retVal;
                }
            }
        }

        return null;
    }

    /**
     * The distribution class keeps precalculated arrays for distributions for all legal redundancies. The class is
     * immutable, such that it can be returned safely out from the group object.
     */
    public static class Distribution {

        private final int[] distributionSpec;
        private final int[][] preCalculatedResults;

        public Distribution(String serialized, int maxRedundancy) throws ParseException {
            StringTokenizer st = new StringTokenizer(serialized, "|");
            // Create the distribution spec
            int[] distributionSpec = new int[st.countTokens()];
            for (int i=0; i<distributionSpec.length; ++i) {
                String token = st.nextToken();
                try{
                    distributionSpec[i] = (token.equals("*") ? 0 : Integer.valueOf(token));
                } catch (NumberFormatException e) {
                    throw new ParseException("Illegal distribution spec \"" + serialized + "\". Copy counts must be integer values in the range 1-255.", i);
                }
                if (!token.equals("*") && distributionSpec[i] == 0) {
                    throw new ParseException("Illegal distribution spec \"" + serialized + "\". Copy counts must be in the range 1-255.", i);
                }
            }
            // Verify sanity of the distribution spec
            int firstAsterix = distributionSpec.length;
            for (int i=0; i<distributionSpec.length; ++i) {
                if (i > firstAsterix) {
                    if (distributionSpec[i] != 0) {
                        throw new ParseException("Illegal distribution spec \"" + serialized + "\". Asterix specification must be tailing the specification.", i);
                    }
                    continue;
                }
                if (i < firstAsterix && distributionSpec[i] == 0) {
                    firstAsterix = i;
                    continue;
                }
                if (distributionSpec[i] <= 0 || distributionSpec[i] >= 256) {
                    throw new ParseException("Illegal distribution spec \"" + serialized + "\". Copy counts must be in the range 1-255.", i);
                }
            }
            this.distributionSpec = distributionSpec;
            // Create the pre calculated results
            if (maxRedundancy <= 0 || maxRedundancy > 255) throw new IllegalArgumentException("The max redundancy must be a positive number in the range 1-255.");
            int asterixCount = distributionSpec.length - firstAsterix;
            int[][] preCalculations = new int[maxRedundancy + 1][];
            for (int i=1; i<=maxRedundancy; ++i) {
                List<Integer> spec = new ArrayList<Integer>();
                for (int j=0; j<distributionSpec.length; ++j) {
                    spec.add(distributionSpec[j]);
                }
                int remainingRedundancy = i;
                for (int j=0; j<firstAsterix; ++j) {
                    spec.set(j, Math.min(remainingRedundancy, spec.get(j)));
                    remainingRedundancy -= spec.get(j);
                }
                int divided = remainingRedundancy / asterixCount;
                remainingRedundancy = remainingRedundancy % asterixCount;
                for (int j=firstAsterix; j<spec.size(); ++j) {
                    spec.set(j, divided + (j - firstAsterix < remainingRedundancy ? 1 : 0));
                }
                while (spec.get(spec.size() - 1) == 0) {
                    spec.remove(spec.size() - 1);
                }
                preCalculations[i] = new int[spec.size()];
                Collections.sort(spec);
                for (int j=0; j<spec.size(); ++j) preCalculations[i][j] = spec.get(spec.size() - 1 - j);
            }
            this.preCalculatedResults = preCalculations;
        }

        public int[] getRedundancyArray(int redundancy) {
            if (redundancy == 0 || redundancy >= preCalculatedResults.length) {
                throw new IllegalArgumentException("Can only retrieve redundancy arrays in the inclusive range 1-" + (preCalculatedResults.length - 1) + ".");
            }
            return preCalculatedResults[redundancy];
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Distribution)) return false;
            Distribution other = (Distribution) o;
            return (distributionSpec == other.distributionSpec && preCalculatedResults.length == other.preCalculatedResults.length);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(distributionSpec) + 13 * preCalculatedResults.length;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            for (int i=0; i<distributionSpec.length; ++i) {
                if (i != 0) sb.append('|');
                if (distributionSpec[i] == 0) sb.append('*');
                else sb.append(distributionSpec[i]);
            }
            return sb.toString();
        }
    }

}
