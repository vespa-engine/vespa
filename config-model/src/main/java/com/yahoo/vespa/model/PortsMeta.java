// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Track meta information about the ports of a service.
 *
 * @author Vidar Larsen
 */
public class PortsMeta implements Serializable {

    /** A list of all ports. The list elements are lists of strings. */
    private final List<LinkedList<String>> ports;

    /** Remember the rpc admin port offset. */
    private Integer rpcAdminOffset   = null;
    /** Remember the rpc status port offset. */
    private Integer rpcStatusOffset  = null;
    /** Remember the http admin port offset. */
    private Integer httpAdminOffset  = null;
    /** Remember the http status port offset. */
    private Integer httpStatusOffset = null;

    /** Keep track of the offset to register for, when chaining */
    private Integer currentOffset;

    /** Create a new PortsMeta object. */
    public PortsMeta() {
        ports = new ArrayList<>();
    }

    /**
     * Set up the port to tag, for chained usage.
     *
     * @param offset the relative port to tag
     * @return this portsmeta, to allow .tag calls
     */
    public PortsMeta on(int offset) {
        this.currentOffset = offset;
        return this;
    }

    /**
     * Tag a previously setup port (using 'on') with the specified tag.
     *
     * @param meta the tag to apply to the current port
     * @return this portsmeta, to allow further .tag calls
     */
    public PortsMeta tag(String meta) {
        if (currentOffset == null) {
            throw new NullPointerException("No current port offset to tag, use 'on#1'");
        }
        return register(currentOffset, meta);
    }

    /**
     * Register a given metainfo string to the port at offset.
     *
     * @param offset 0-based index to identify the port
     * @param meta a String to be added to the given ports meta set
     * @return this for convenient chaining
     */
    private PortsMeta register(int offset, String meta) {
        // Allocate new LinkedLists on each element up-to-and-including offset
        for (int i = ports.size(); i <= offset; i++) {
            ports.add(i, new LinkedList<>());
        }
        ports.get(offset).addFirst(meta);

        return this;
    }

    /**
     * Check if the port at a specific offset contains a particular meta attribute.
     *
     * @param offset the relative port offset
     * @param meta the meta info we want to check for
     * @return boolean true if the specific port has registered the meta
     */
    public boolean contains(int offset, String meta) {
        return offset < ports.size() && ports.get(offset).contains(meta);
    }

    /**
     * Get the number of ports with registered meta.
     *
     * @return the number of ports that have been registered
     */
    public int getNumPorts() {
        return ports.size();
    }

    /**
     * Get an iterator of the Strings registered at the specific point.
     *
     * @param offset the relative offset to inquire about tags
     * @return List of tags.
     */
    public List<String> getTagsAt(int offset) {
        try {
            return ports.get(offset);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Trying to get ports meta with offset " + offset +
                                               ", which is outside the range 0 to " + ports.size(), e);
        }
    }

    /**
     * Get the offset to the rpc port used for admin.
     *
     * @return the offset, or null if none set
     */
    public Integer getRpcAdminOffset() {
        if (rpcAdminOffset == null) {
            for (int p = 0; p < getNumPorts(); p++) {
                if (contains(p, "rpc") && contains(p, "admin") && !contains(p, "nc")) {
                    rpcAdminOffset = p;
                    break;
                }
            }
        }
        return rpcAdminOffset;
    }

    /**
     * Get the offset to the rpc port used for status.
     * @return Integer the offset, or null if none set.
     */
    public Integer getRpcStatusOffset() {
        if (rpcStatusOffset == null) {
            for (int p = 0; p < getNumPorts(); p++) {
                if (contains(p, "rpc") && contains(p, "status") && !contains(p, "nc")) {
                    rpcStatusOffset = p;
                    break;
                }
            }
        }
        return rpcStatusOffset;
    }

    /**
     * Get the offset to the http port used for admin.
     * @return Integer the offset, or null if none set.
     */
    public Integer getHttpAdminOffset() {
        if (httpAdminOffset == null) {
            for (int p = 0; p < getNumPorts(); p++) {
                if (contains(p, "http") && contains(p, "admin")) {
                    httpAdminOffset = p;
                    break;
                }
            }
        }
        return httpAdminOffset;
    }

    /**
     * Get the offset to the http port used for status.
     * @return Integer the offset, or null if none set.
     */
    public Integer getHttpStatusOffset() {
        if (httpStatusOffset == null) {
            for (int p = 0; p < getNumPorts(); p++) {
                if (contains(p, "http") && contains(p, "status")) {
                    httpStatusOffset = p;
                    break;
                }
            }
        }
        return httpStatusOffset;
    }

}
