// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import java.util.Objects;

/**
 * @author valerijf
 */
public class UserNamespace {

    /** Total number of UID/GID that are mapped for each container */
    private static final int ID_RANGE = 65_536; // 2^16

    /**
     * IDs outside the ID range are translated to the overflow ID before being written to disk:
     * https://github.com/torvalds/linux/blob/5bfc75d92efd494db37f5c4c173d3639d4772966/Documentation/admin-guide/sysctl/fs.rst#overflowgid--overflowuid */
    private static final int OVERFLOW_ID = 65_534;

    private final int uidOffset;
    private final int gidOffset;
    private final String vespaUser;
    private final String vespaGroup;
    private final int vespaUserId;
    private final int vespaGroupId;

    public UserNamespace(int uidOffset, int gidOffset, String vespaUser, String vespaGroup, int vespaUserId, int vespaGroupId) {
        this.uidOffset = uidOffset;
        this.gidOffset = gidOffset;
        this.vespaUser = Objects.requireNonNull(vespaUser);
        this.vespaGroup = Objects.requireNonNull(vespaGroup);
        this.vespaUserId = vespaUserId;
        this.vespaGroupId = vespaGroupId;
    }

    public int userIdOnHost(int containerUid) { return toHostId(containerUid, uidOffset); }
    public int groupIdOnHost(int containerGid) { return toHostId(containerGid, gidOffset); }
    public int userIdInContainer(int hostUid) { return toContainerId(hostUid, uidOffset); }
    public int groupIdInContainer(int hostGid) { return toContainerId(hostGid, gidOffset); }

    public String vespaUser() { return vespaUser; }
    public String vespaGroup() { return vespaGroup; }
    public int vespaUserId() { return vespaUserId; }
    public int vespaGroupId() { return vespaGroupId; }

    public int idRange() { return ID_RANGE; }
    public int overflowId() { return OVERFLOW_ID; }

    private static int toHostId(int containerId, int idOffset) {
        if (containerId < 0 || containerId > ID_RANGE)
            throw new IllegalArgumentException("Invalid container id: " + containerId);
        return idOffset + containerId;
    }

    private static int toContainerId(int hostId, int idOffset) {
        hostId = hostId - idOffset;
        return hostId < 0 || hostId >= ID_RANGE ? OVERFLOW_ID : hostId;
    }
}
