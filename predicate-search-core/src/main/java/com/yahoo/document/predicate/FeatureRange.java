// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class FeatureRange extends PredicateValue {

    private String key;
    private Long from;
    private Long to;
    private List<RangePartition> partitions;
    private List<RangeEdgePartition> edgePartitions;

    public FeatureRange(String key) {
        this(key, null, null);
    }

    public FeatureRange(String key, Long fromInclusive, Long toInclusive) {
        setKey(key);
        setFromInclusive(fromInclusive);
        setToInclusive(toInclusive);
        partitions = new ArrayList<>();
        edgePartitions = new ArrayList<>();
    }

    public FeatureRange setKey(String key) {
        Objects.requireNonNull(key, "key");
        this.key = key;
        return this;
    }

    public String getKey() {
        return key;
    }

    public FeatureRange setFromInclusive(Long from) {
        if (from != null && to != null && from > to) {
            throw new IllegalArgumentException("Expected 'from' less than or equal to " + to + ", got " + from + ".");
        }
        this.from = from;
        return this;
    }

    public Long getFromInclusive() {
        return from;
    }

    public FeatureRange setToInclusive(Long to) {
        if (from != null && to != null && from > to) {
            throw new IllegalArgumentException("Expected 'to' greater than or equal to " + from + ", got " + to + ".");
        }
        this.to = to;
        return this;
    }

    public Long getToInclusive() {
        return to;
    }

    public void addPartition(RangePartition p) {
        if (p instanceof RangeEdgePartition) {
            edgePartitions.add((RangeEdgePartition)p);
        } else {
            partitions.add(p);
        }
    }

    public List<RangeEdgePartition> getEdgePartitions() {
        return edgePartitions;
    }

    public List<RangePartition> getPartitions() {
        return partitions;
    }

    public void clearPartitions() {
        partitions.clear();
        edgePartitions.clear();
    }

    @Override
    public FeatureRange clone() throws CloneNotSupportedException {
        return (FeatureRange)super.clone();
    }

    @Override
    public int hashCode() {
        return ((key.hashCode() + (from != null ? from.hashCode() : 0)) * 31 + (to != null ? to.hashCode() : 0)) * 31;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof FeatureRange)) {
            return false;
        }
        FeatureRange rhs = (FeatureRange)obj;
        if (!key.equals(rhs.key)) {
            return false;
        }
        if (!Objects.equals(from, rhs.from)) {
            return false;
        }
        if (!Objects.equals(to, rhs.to)) {
            return false;
        }
        return partitions.equals(rhs.partitions) && edgePartitions.equals(rhs.edgePartitions);
    }

    @Override
    protected void appendTo(StringBuilder out) {
        appendQuotedTo(key, out);
        out.append(" in [");
        if (from != null) {
            out.append(from);
        }
        out.append("..");
        if (to != null) {
            out.append(to);
        }
        if (!partitions.isEmpty() || !edgePartitions.isEmpty()) {
            out.append(" (");
            for (RangeEdgePartition p : edgePartitions) {
                p.appendTo(out);
                out.append(',');
            }
            for (RangePartition p : partitions) {
                p.appendTo(out);
                out.append(',');
            }
            out.deleteCharAt(out.length() - 1);  // Remove extra ','
            out.append(")");
        }
        out.append("]");
    }

    public static FeatureRange buildFromMixedIn(String key, List<String> partitions, int arity) {
        Long fromInclusive = null;
        Long toInclusive = null;
        long from = 0;
        long to = 0;
        for (String p : partitions) {
            String[] parts = p.split(",");
            if (parts.length == 1) {
                String[] subparts = parts[0].split("=|-");
                int offset = subparts.length == 3? 0 : 1;
                if (subparts.length < 3 || subparts.length > 4) {
                    throw new IllegalArgumentException("MIXED_IN range partition must be on the form label=val-val");
                }
                from = Long.parseLong(subparts[offset + 1]);
                to = Long.parseLong(subparts[offset + 2]);
                if (parts[0].contains("=-")) {
                    long tmp = from;
                    from = -to;
                    to = -tmp;
                }
            } else {
                if (parts.length != 3) {
                    throw new IllegalArgumentException("MIXED_IN range edge partition must be on the form label=val,val,payload");
                }
                long value = Long.parseLong(parts[1]);
                long payload = Long.parseLong(parts[2]);
                if ((payload & 0xc0000000) == 0x80000000L) {
                    from = value + (payload & 0xffff);
                    to = value + arity - 1;
                } else if ((payload & 0xc0000000) == 0x40000000L) {
                    from = value;
                    to = value + (payload & 0xffff);
                } else {
                    from = value + (payload >> 16);
                    to = value + (payload & 0xffff);
                }
            }
            if (fromInclusive == null || fromInclusive > from) {
                fromInclusive = from;
            }
            if (toInclusive == null || toInclusive > to) {
                toInclusive = to;
            }
        }
        return new FeatureRange(key, fromInclusive, toInclusive);
    }
}
