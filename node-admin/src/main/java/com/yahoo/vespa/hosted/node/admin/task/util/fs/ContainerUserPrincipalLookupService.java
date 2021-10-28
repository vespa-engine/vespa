// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.nodeagent.UserNamespace;
import com.yahoo.vespa.hosted.node.admin.nodeagent.VespaUser;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Objects;

/**
 * @author freva
 */
public class ContainerUserPrincipalLookupService extends UserPrincipalLookupService {

    private final UserPrincipalLookupService baseFsUserPrincipalLookupService;
    private final UserNamespace userNamespace;
    private final VespaUser vespaUser;

    ContainerUserPrincipalLookupService(
            UserPrincipalLookupService baseFsUserPrincipalLookupService, UserNamespace userNamespace, VespaUser vespaUser) {
        this.baseFsUserPrincipalLookupService = Objects.requireNonNull(baseFsUserPrincipalLookupService);
        this.userNamespace = Objects.requireNonNull(userNamespace);
        this.vespaUser = Objects.requireNonNull(vespaUser);
    }

    public UserNamespace userNamespace() { return userNamespace; }
    public VespaUser vespaUser() { return vespaUser; }

    public int userIdOnHost(int containerUid)  { return userNamespace.userIdOnHost(containerUid); }
    public int groupIdOnHost(int containerGid) { return userNamespace.groupIdOnHost(containerGid); }
    public int userIdInContainer(int hostUid)  { return userNamespace.userIdInContainer(hostUid); }
    public int groupIdInContainer(int hostGid) { return userNamespace.groupIdInContainer(hostGid); }

    @Override
    public ContainerUserPrincipal lookupPrincipalByName(String name) throws IOException {
        int containerUid = resolveName(name, vespaUser.name(), vespaUser.uid());
        String user = resolveId(containerUid, vespaUser.name(), vespaUser.uid());
        String hostUid = String.valueOf(userIdOnHost(containerUid));
        return new ContainerUserPrincipal(containerUid, user, baseFsUserPrincipalLookupService.lookupPrincipalByName(hostUid));
    }

    @Override
    public ContainerGroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
        int containerGid = resolveName(group, vespaUser.group(), vespaUser.gid());
        String name = resolveId(containerGid, vespaUser.group(), vespaUser.gid());
        String hostGid = String.valueOf(groupIdOnHost(containerGid));
        return new ContainerGroupPrincipal(containerGid, name, baseFsUserPrincipalLookupService.lookupPrincipalByGroupName(hostGid));
    }

    public ContainerUserPrincipal userPrincipal(int uid, UserPrincipal baseFsPrincipal) {
        String name = resolveId(uid, vespaUser.name(), vespaUser.uid());
        return new ContainerUserPrincipal(uid, name, baseFsPrincipal);
    }
    
    public ContainerGroupPrincipal groupPrincipal(int gid, GroupPrincipal baseFsPrincipal) {
        String name = resolveId(gid, vespaUser.group(), vespaUser.gid());
        return new ContainerGroupPrincipal(gid, name, baseFsPrincipal);
    }

    private String resolveId(int id, String vespaName, int vespaId) {
        if (id == 0) return "root";
        if (id == vespaId) return vespaName;
        return String.valueOf(id);
    }

    private int resolveName(String name, String vespaName, int vespaId) throws UserPrincipalNotFoundException {
        if (name.equals("root")) return 0;
        if (name.equals(vespaName)) return vespaId;

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

        private NamedPrincipal(int id, String name, UserPrincipal baseFsPrincipal) {
            this.id = id;
            this.name = Objects.requireNonNull(name);
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
        private ContainerUserPrincipal(int id, String name, UserPrincipal baseFsPrincipal) { super(id, name, baseFsPrincipal); }
    }

    static final class ContainerGroupPrincipal extends NamedPrincipal implements GroupPrincipal {
        private ContainerGroupPrincipal(int id, String name, GroupPrincipal baseFsPrincipal) { super(id, name, baseFsPrincipal); }

        @Override public GroupPrincipal baseFsPrincipal() { return (GroupPrincipal) super.baseFsPrincipal(); }
    }
}
