// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.component.Version;

/**
 * Red Hat versions and their associated Yum major version.
 *
 * @author mpolden
 */
public enum YumVersion {

    rhel7(3),
    rhel8(4);

    private final Version version;

    YumVersion(int yumMajor) {
        this.version = new Version(yumMajor, 0, 0);
    }

    public Version asVersion() {
        return version;
    }

}
