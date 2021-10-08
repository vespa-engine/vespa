/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.vespa.test.file;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.AttributeProvider;
import com.google.common.jimfs.File;
import com.google.common.jimfs.FileLookup;

import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Same as {@code com.google.common.jimfs.UnixAttributeProvider} except that getUniqueId() will return user
 * if user is a numerical string.
 */
public class UnixUidGidAttributeProvider extends AttributeProvider {

    private static final ImmutableSet<String> ATTRIBUTES = ImmutableSet.of("uid", "ino", "dev", "nlink", "rdev", "ctime", "mode", "gid");
    private static final ImmutableSet<String> INHERITED_VIEWS = ImmutableSet.of("basic", "owner", "posix");

    private final AtomicInteger uidGenerator = new AtomicInteger();
    private final ConcurrentMap<UserPrincipal, Integer> idCache = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "unix";
    }

    @Override
    public ImmutableSet<String> inherits() {
        return INHERITED_VIEWS;
    }

    @Override
    public ImmutableSet<String> fixedAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public Class<UnixFileAttributeView> viewType() {
        return UnixFileAttributeView.class;
    }

    @Override
    public UnixFileAttributeView view(FileLookup lookup, ImmutableMap<String, FileAttributeView> inheritedViews) {
        throw new UnsupportedOperationException();
    }

    private int getUniqueId(UserPrincipal user) {
        return idCache.computeIfAbsent(user, id -> maybeNumber(id.getName()).orElseGet(uidGenerator::incrementAndGet));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object get(File file, String attribute) {
        switch (attribute) {
            case "uid":
                UserPrincipal user = (UserPrincipal) file.getAttribute("owner", "owner");
                return getUniqueId(user);
            case "gid":
                GroupPrincipal group = (GroupPrincipal) file.getAttribute("posix", "group");
                return getUniqueId(group);
            case "mode":
                Set<PosixFilePermission> permissions =
                        (Set<PosixFilePermission>) file.getAttribute("posix", "permissions");
                return toMode(permissions);
            case "ctime":
                return FileTime.fromMillis(file.getCreationTime());
            case "rdev":
                return 0L;
            case "dev":
                return 1L;
            case "ino":
                return file.id();
            case "nlink":
                return file.links();
            default:
                return null;
        }
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        throw unsettable(view, attribute);
    }

    @SuppressWarnings("OctalInteger")
    private static int toMode(Set<PosixFilePermission> permissions) {
        int result = 0;
        for (PosixFilePermission permission : permissions) {
            checkNotNull(permission);
            switch (permission) {
                case OWNER_READ:
                    result |= 0400;
                    break;
                case OWNER_WRITE:
                    result |= 0200;
                    break;
                case OWNER_EXECUTE:
                    result |= 0100;
                    break;
                case GROUP_READ:
                    result |= 0040;
                    break;
                case GROUP_WRITE:
                    result |= 0020;
                    break;
                case GROUP_EXECUTE:
                    result |= 0010;
                    break;
                case OTHERS_READ:
                    result |= 0004;
                    break;
                case OTHERS_WRITE:
                    result |= 0002;
                    break;
                case OTHERS_EXECUTE:
                    result |= 0001;
                    break;
                default:
                    throw new AssertionError(); // no other possible values
            }
        }
        return result;
    }

    interface UnixFileAttributeView extends FileAttributeView {}

    private static Optional<Integer> maybeNumber(String str) {
        try {
            return Optional.of(Integer.parseInt(str));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
