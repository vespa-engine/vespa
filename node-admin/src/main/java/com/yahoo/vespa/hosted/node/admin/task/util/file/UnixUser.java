// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.util.Objects;

/**
 * A regular UNIX-style user and its primary group.
 *
 * @author mpolden
 */
public class UnixUser {

    public static final UnixUser ROOT = new UnixUser("root", 0, "root", 0);

    private final String name;
    private final int uid;
    private final String group;
    private final int gid;

    public UnixUser(String name, int uid, String group, int gid) {
        this.name = name;
        this.uid = uid;
        this.group = group;
        this.gid = gid;
    }

    /** Username of this */
    public String name() { return name; }

    /** User ID of this */
    public int uid() { return uid; }

    /** Primary group of this */
    public String group() { return group; }

    /** Primary group ID of this */
    public int gid() { return gid; }

    @Override
    public String toString() {
        return "user " + name + ":" + group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnixUser unixUser = (UnixUser) o;
        return uid == unixUser.uid && name.equals(unixUser.name) &&
                gid == unixUser.gid && group.equals(unixUser.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, name, gid, group);
    }
}
