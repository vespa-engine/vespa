// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import java.util.Objects;

/**
 * Describes Vespa user inside the container user namespace.
 * 
 * @author freva
 */
public class VespaUser {

    private final String name;
    private final String group;
    private final int uid;
    private final int gid;

    public VespaUser(String name, String group, int uid, int gid) {
        this.name = Objects.requireNonNull(name);
        this.group = Objects.requireNonNull(group);
        this.uid = uid;
        this.gid = gid;
    }

    public String name() { return name; }
    public String group() { return group; }
    public int uid() { return uid; }
    public int gid() { return gid; }
}
