// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.nodeagent.UserScope;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author freva
 */
public class ContainerUserPrincipalLookupService extends UserPrincipalLookupService {

    private final UserPrincipalLookupService baseFsUserPrincipalLookupService;
    private final UserScope userScope;

    ContainerUserPrincipalLookupService(UserPrincipalLookupService baseFsUserPrincipalLookupService, UserScope userScope) {
        this.baseFsUserPrincipalLookupService = Objects.requireNonNull(baseFsUserPrincipalLookupService);
        this.userScope = Objects.requireNonNull(userScope);
    }

    public UserScope userScope() { return userScope; }

    public int userIdOnHost(int containerUid)  { return userScope.namespace().userIdOnHost(containerUid); }
    public int groupIdOnHost(int containerGid) { return userScope.namespace().groupIdOnHost(containerGid); }
    public int userIdInContainer(int hostUid)  { return userScope.namespace().userIdInContainer(hostUid); }
    public int groupIdInContainer(int hostGid) { return userScope.namespace().groupIdInContainer(hostGid); }

    @Override
    public ContainerUserPrincipal lookupPrincipalByName(String name) throws IOException {
        int containerUid = resolveName(name, UnixUser::uid, UnixUser::name);
        String user = resolveId(containerUid, UnixUser::uid, UnixUser::name);
        String hostUid = String.valueOf(userIdOnHost(containerUid));
        return new ContainerUserPrincipal(containerUid, user, baseFsUserPrincipalLookupService.lookupPrincipalByName(hostUid));
    }

    @Override
    public ContainerGroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
        int containerGid = resolveName(group, UnixUser::gid, UnixUser::group);
        String name = resolveId(containerGid, UnixUser::gid, UnixUser::group);
        String hostGid = String.valueOf(groupIdOnHost(containerGid));
        return new ContainerGroupPrincipal(containerGid, name, baseFsUserPrincipalLookupService.lookupPrincipalByGroupName(hostGid));
    }

    public ContainerUserPrincipal userPrincipal(int uid, UserPrincipal baseFsPrincipal) {
        String name = resolveId(uid, UnixUser::uid, UnixUser::name);
        return new ContainerUserPrincipal(uid, name, baseFsPrincipal);
    }
    
    public ContainerGroupPrincipal groupPrincipal(int gid, GroupPrincipal baseFsPrincipal) {
        String name = resolveId(gid, UnixUser::gid, UnixUser::group);
        return new ContainerGroupPrincipal(gid, name, baseFsPrincipal);
    }

    private String resolveId(int id, Function<UnixUser, Integer> idExtractor, Function<UnixUser, String> nameExtractor) {
        if (idExtractor.apply(userScope.root()) == id) return nameExtractor.apply(userScope.root());
        if (idExtractor.apply(userScope.vespa()) == id) return nameExtractor.apply(userScope.vespa());
        return String.valueOf(id);
    }

    private int resolveName(String name, Function<UnixUser, Integer> idExtractor, Function<UnixUser, String> nameExtractor) throws UserPrincipalNotFoundException {
        if (name.equals(nameExtractor.apply(userScope.root()))) return idExtractor.apply(userScope.root());
        if (name.equals(nameExtractor.apply(userScope.vespa()))) return idExtractor.apply(userScope.vespa());

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
