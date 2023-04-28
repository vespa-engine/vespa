// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import java.util.List;

/**
 * Utility methods for accessing the cgroup core interface files, i.e. all cgroup.* files.
 *
 * @author hakonhall
 */
public class CgroupCore {
    private final Cgroup cgroup;

    CgroupCore(Cgroup cgroup) { this.cgroup = cgroup; }

    public List<Integer> getPidsInCgroup() {
        return cgroup.readLines("cgroup.procs")
                     .stream()
                     .map(Integer::parseInt)
                     .toList();
    }

    /** Whether the given PID is a member of this cgroup. */
    public boolean isMember(int pid) {
        return getPidsInCgroup().contains(pid);
    }

    /** Move the given PID to this cgroup, but return false if it was already a member. */
    public boolean addMember(int pid) {
        if (isMember(pid)) return false;
        cgroup.unixPath().resolve("cgroup.procs").writeUtf8File(Integer.toString(pid));
        return true;
    }
}
