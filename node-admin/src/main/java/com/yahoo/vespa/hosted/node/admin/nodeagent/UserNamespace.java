// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import java.util.Objects;

/**
 * @author freva
 */
public class UserNamespace {

    /**
     * IDs outside the ID range are translated to the overflow ID before being written to disk:
     * https://github.com/torvalds/linux/blob/5bfc75d92efd494db37f5c4c173d3639d4772966/Documentation/admin-guide/sysctl/fs.rst#overflowgid--overflowuid
     * Real value in /proc/sys/fs/overflowuid or overflowgid, hardcode default value*/
    private static final int OVERFLOW_ID = 65_534;

    private final int uidOffset;
    private final int gidOffset;
    private final int idRangeSize;

    public UserNamespace(int uidOffset, int gidOffset, int idRangeSize) {
        this.uidOffset = uidOffset;
        this.gidOffset = gidOffset;
        this.idRangeSize = idRangeSize;
    }

    public int userIdOnHost(int containerUid) { return toHostId(containerUid, uidOffset, idRangeSize); }
    public int groupIdOnHost(int containerGid) { return toHostId(containerGid, gidOffset, idRangeSize); }
    public int userIdInContainer(int hostUid) { return toContainerId(hostUid, uidOffset, idRangeSize); }
    public int groupIdInContainer(int hostGid) { return toContainerId(hostGid, gidOffset, idRangeSize); }

    public int idRangeSize() { return idRangeSize; }
    public int overflowId() { return OVERFLOW_ID; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserNamespace that = (UserNamespace) o;
        return uidOffset == that.uidOffset && gidOffset == that.gidOffset && idRangeSize == that.idRangeSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uidOffset, gidOffset, idRangeSize);
    }

    @Override
    public String toString() {
        return "UserNamespace{" +
                "uidOffset=" + uidOffset +
                ", gidOffset=" + gidOffset +
                ", idRangeSize=" + idRangeSize +
                '}';
    }

    private static int toHostId(int containerId, int idOffset, int idRangeSize) {
        if (containerId < 0 || containerId > idRangeSize)
            throw new IllegalArgumentException("Invalid container id: " + containerId);
        return idOffset + containerId;
    }

    private static int toContainerId(int hostId, int idOffset, int idRangeSize) {
        hostId = hostId - idOffset;
        return hostId < 0 || hostId >= idRangeSize ? OVERFLOW_ID : hostId;
    }
}
