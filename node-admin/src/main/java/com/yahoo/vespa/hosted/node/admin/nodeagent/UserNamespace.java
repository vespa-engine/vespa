// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

/**
 * @author valerijf
 */
public class UserNamespace {

    private final int uidOffset;
    private final int gidOffset;
    private final String vespaUser;
    private final String vespaGroup;

    public UserNamespace(int uidOffset, int gidOffset, String vespaUser, String vespaGroup, int vespaUserId, int vespaGroupId) {
        this.uidOffset = uidOffset;
        this.gidOffset = gidOffset;
        this.vespaUser = vespaUser;
        this.vespaGroup = vespaGroup;
    }

    public int rootUserIdOnHost() { return uidOffset; }
    public int rootGroupIdOnHost() { return gidOffset; }

    /** Returns name of the user that runs vespa inside the container */
    public String vespaUser() { return vespaUser; }
    /** Returns name of the group of the user that runs vespa inside the container */
    public String vespaGroup() { return vespaGroup; }
}
