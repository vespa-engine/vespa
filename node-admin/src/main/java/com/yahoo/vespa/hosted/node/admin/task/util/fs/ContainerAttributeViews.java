// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import java.io.IOException;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Set;

import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerGroupPrincipal;
import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerUserPrincipal;

/**
 * @author freva
 */
class ContainerAttributeViews {

    static class ContainerPosixFileAttributeView implements PosixFileAttributeView {
        private final PosixFileAttributeView posixFileAttributeView;
        private final ContainerPosixFileAttributes fileAttributes;

        ContainerPosixFileAttributeView(PosixFileAttributeView posixFileAttributeView,
                                        ContainerPosixFileAttributes fileAttributes) {
            this.posixFileAttributeView = posixFileAttributeView;
            this.fileAttributes = fileAttributes;
        }

        @Override public String name() { return "posix"; }
        @Override public UserPrincipal getOwner() { return fileAttributes.owner(); }
        @Override public PosixFileAttributes readAttributes() { return fileAttributes; }

        @Override
        public void setOwner(UserPrincipal owner) throws IOException {
            if (!(owner instanceof ContainerUserPrincipal)) throw new ProviderMismatchException();
            posixFileAttributeView.setOwner(((ContainerUserPrincipal) owner).baseFsPrincipal());
        }

        @Override
        public void setGroup(GroupPrincipal group) throws IOException {
            if (!(group instanceof ContainerGroupPrincipal)) throw new ProviderMismatchException();
            posixFileAttributeView.setGroup(((ContainerGroupPrincipal) group).baseFsPrincipal());
        }

        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
            posixFileAttributeView.setTimes(lastModifiedTime, lastAccessTime, createTime);
        }

        @Override
        public void setPermissions(Set<PosixFilePermission> perms) throws IOException {
            posixFileAttributeView.setPermissions(perms);
        }
    }

    static class ContainerPosixFileAttributes implements PosixFileAttributes {
        private final Map<String, Object> attributes;

        ContainerPosixFileAttributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        @SuppressWarnings("unchecked")
        @Override public Set<PosixFilePermission> permissions() { return (Set<PosixFilePermission>) attributes.get("permissions"); }
        @Override public ContainerUserPrincipal owner() { return (ContainerUserPrincipal) attributes.get("owner"); }
        @Override public ContainerGroupPrincipal group() { return (ContainerGroupPrincipal) attributes.get("group"); }
        @Override public FileTime lastModifiedTime() { return (FileTime) attributes.get("lastModifiedTime"); }
        @Override public FileTime lastAccessTime() { return (FileTime) attributes.get("lastAccessTime"); }
        @Override public FileTime creationTime() { return (FileTime) attributes.get("creationTime"); }
        @Override public boolean isRegularFile() { return (boolean) attributes.get("isRegularFile"); }
        @Override public boolean isDirectory() { return (boolean) attributes.get("isDirectory"); }
        @Override public boolean isSymbolicLink() { return (boolean) attributes.get("isSymbolicLink"); }
        @Override public boolean isOther() { return (boolean) attributes.get("isOther"); }
        @Override public long size() { return (long) attributes.get("size"); }
        @Override public Object fileKey() { return attributes.get("fileKey"); }
    }
}
