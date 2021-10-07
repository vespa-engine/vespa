// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * Model a single cluster of services in the Vespa model.
 *
 * @author Steinar Knutsen
 */
public final class Cluster implements Comparable<Cluster> {

    public final String name;
    public final String type;
    /**
     * An ordered list of the service instances in this cluster.
     */
    public final ImmutableList<Service> services;

    public Cluster(String name, String type, List<Service> services) {
        this.name = name;
        this.type = type;
        ImmutableList.Builder<Service> builder = ImmutableList.builder();
        Service[] sortingBuffer = services.toArray(new Service[0]);
        Arrays.sort(sortingBuffer);
        builder.add(sortingBuffer);
        this.services = builder.build();
    }

    @Override
    public int compareTo(Cluster other) {
        int nameOrder = name.compareTo(other.name);
        if (nameOrder != 0) {
            return nameOrder;
        }
        return type.compareTo(other.type);
    }

    @Override
    public int hashCode() {
        final int prime = 761;
        int result = 1;
        result = prime * result + name.hashCode();
        result = prime * result + type.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Cluster other = (Cluster) obj;
        if (!name.equals(other.name)) {
            return false;
        }
        return type.equals(other.type);
    }

    @Override
    public String toString() {
        final int maxLen = 3;
        StringBuilder builder = new StringBuilder();
        builder.append("Cluster [name=").append(name).append(", type=").append(type).append(", services=")
                .append(services.subList(0, Math.min(services.size(), maxLen))).append("]");
        return builder.toString();
    }

}
