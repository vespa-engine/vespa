// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.google.common.collect.ImmutableBiMap;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Objects;
import java.util.Optional;

/**
 * @author valerijf
 */
class ContainerUserPrincipalLookupService extends UserPrincipalLookupService {

    /** Total number of UID/GID that are mapped for each container */
    private static final int ID_RANGE = 1 << 16;

    /**
     * IDs outside the ID range are translated to the overflow ID before being written to disk:
     * https://github.com/torvalds/linux/blob/5bfc75d92efd494db37f5c4c173d3639d4772966/Documentation/admin-guide/sysctl/fs.rst#overflowgid--overflowuid */
    static final int OVERFLOW_ID = 65_534;

    private static final ImmutableBiMap<String, Integer> CONTAINER_IDS_BY_NAME = ImmutableBiMap.<String, Integer>builder()
            .put("root", 0)
            .put("vespa", 1000)
            .build();

    private final UserPrincipalLookupService baseFsUserPrincipalLookupService;
    private final int uidOffset;
    private final int gidOffset;

    ContainerUserPrincipalLookupService(UserPrincipalLookupService baseFsUserPrincipalLookupService, int uidOffset, int gidOffset) {
        this.baseFsUserPrincipalLookupService = baseFsUserPrincipalLookupService;
        this.uidOffset = uidOffset;
        this.gidOffset = gidOffset;
    }

    public int containerUidToHostUid(int containerUid) { return containerIdToHostId(containerUid, uidOffset); }
    public int containerGidToHostGid(int containerGid) { return containerIdToHostId(containerGid, gidOffset); }
    public int hostUidToContainerUid(int hostUid) { return hostIdToContainerId(hostUid, uidOffset); }
    public int hostGidToContainerGid(int hostGid) { return hostIdToContainerId(hostGid, gidOffset); }

    @Override
    public ContainerUserPrincipal lookupPrincipalByName(String name) throws IOException {
        int containerUid = resolve(name);
        String hostUid = String.valueOf(containerUidToHostUid(containerUid));
        return new ContainerUserPrincipal(containerUid, baseFsUserPrincipalLookupService.lookupPrincipalByName(hostUid));
    }

    @Override
    public ContainerGroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
        int containerGid = resolve(group);
        String hostGid = String.valueOf(containerGidToHostGid(containerGid));
        return new ContainerGroupPrincipal(containerGid, baseFsUserPrincipalLookupService.lookupPrincipalByGroupName(hostGid));
    }

    private static int resolve(String name) throws UserPrincipalNotFoundException {
        Integer id = CONTAINER_IDS_BY_NAME.get(name);
        if (id != null) return id;

        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException ignored) {
            throw new UserPrincipalNotFoundException(name);
        }
    }

    private abstract static class NamedPrincipal implements UserPrincipal {
        private final int id;
        private final String name;
        private final UserPrincipal baseFsPrincipal;

        private NamedPrincipal(int id, UserPrincipal baseFsPrincipal) {
            this.id = id;
            this.name = Optional.ofNullable(CONTAINER_IDS_BY_NAME.inverse().get(id)).orElseGet(() -> Integer.toString(id));
            this.baseFsPrincipal = Objects.requireNonNull(baseFsPrincipal);
        }

        @Override
        public final String getName() {
            return name;
        }

        public int id() {
            return id;
        }

        public UserPrincipal baseFsPrincipal() {
            return baseFsPrincipal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NamedPrincipal that = (NamedPrincipal) o;
            return id == that.id && baseFsPrincipal.equals(that.baseFsPrincipal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, baseFsPrincipal);
        }

        @Override
        public String toString() {
            return "{id=" + id + ", baseFsPrincipal=" + baseFsPrincipal + '}';
        }
    }

    static final class ContainerUserPrincipal extends NamedPrincipal {
        ContainerUserPrincipal(int id, UserPrincipal baseFsPrincipal) { super(id, baseFsPrincipal); }
    }

    static final class ContainerGroupPrincipal extends NamedPrincipal implements GroupPrincipal {
        ContainerGroupPrincipal(int id, GroupPrincipal baseFsPrincipal) { super(id, baseFsPrincipal); }

        @Override public GroupPrincipal baseFsPrincipal() { return (GroupPrincipal) super.baseFsPrincipal(); }
    }

    private static int containerIdToHostId(int id, int idOffset) {
        if (id < 0 || id > ID_RANGE)
            throw new IllegalArgumentException("Invalid container id: " + id);
        return idOffset + id;
    }

    private static int hostIdToContainerId(int id, int idOffset) {
        id = id - idOffset;
        return id < 0 || id >= ID_RANGE ? OVERFLOW_ID : id;
    }
}
