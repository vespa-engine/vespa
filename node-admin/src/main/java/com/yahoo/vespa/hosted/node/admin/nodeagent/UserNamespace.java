package com.yahoo.vespa.hosted.node.admin.nodeagent;

/**
 * @author valerijf
 */
public class UserNamespace {

    /** Total number of UID/GID that are mapped for each container */
    private static final int ID_RANGE = 1 << 16;

    private final int uidOffset;
    private final int gidOffset;
    private final String vespaUser;
    private final String vespaGroup;
    private final int vespaUserId;
    private final int vespaGroupId;

    public UserNamespace(int uidOffset, int gidOffset, String vespaUser, String vespaGroup, int vespaUserId, int vespaGroupId) {
        this.uidOffset = uidOffset;
        this.gidOffset = gidOffset;
        this.vespaUser = vespaUser;
        this.vespaGroup = vespaGroup;
        this.vespaUserId = vespaUserId;
        this.vespaGroupId = vespaGroupId;
    }

    public int userIdOnHost(int userIdInContainer) {
        assertValidId("User", userIdInContainer);
        return uidOffset + userIdInContainer;
    }

    public int groupIdOnHost(int groupIdInContainer) {
        assertValidId("Group", groupIdInContainer);
        return gidOffset + groupIdInContainer;
    }

    int rootUserId() { return 0; }
    int rootGroupId() { return 0; }
    int rootUserIdOnHost() { return userIdOnHost(rootUserId()); }
    int rootGroupIdOnHost() { return groupIdOnHost(rootGroupId()); }

    public String vespaUser() { return vespaUser; }
    public String vespaGroup() { return vespaGroup; }
    public int vespaUserId() { return vespaUserId; }
    public int vespaGroupId() { return vespaGroupId; }
    public int vespaUserIdOnHost() { return userIdOnHost(vespaUserId()); }
    public int vespaGroupIdOnHost() { return groupIdOnHost(vespaGroupId()); }

    private static void assertValidId(String type, int id) {
        if (id < 0) throw new IllegalArgumentException(type + " ID cannot be negative, was " + id);
        if (id >= ID_RANGE) throw new IllegalArgumentException(type + " ID must be less than " + ID_RANGE + ", was " + id);
    }
}
