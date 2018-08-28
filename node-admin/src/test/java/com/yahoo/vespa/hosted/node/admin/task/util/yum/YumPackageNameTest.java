// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class YumPackageNameTest {

    @Test
    public void parsePackageName() {
        YumPackageName packageName = YumPackageName.fromString("docker-engine-selinux-1.12.6-1.el7");
        assertFalse(packageName.getEpoch().isPresent());
        assertEquals("docker-engine-selinux", packageName.getName());
        assertEquals("1.12.6", packageName.getVersion().get());
        assertEquals("1.el7", packageName.getRelease().get());
        assertFalse(packageName.getArchitecture().isPresent());
        assertEquals("0:docker-engine-selinux-1.12.6-1.el7.*", packageName.toFullName());
    }

    @Test
    public void parsePackageNameWithArchitecture() {
        YumPackageName packageName = YumPackageName.fromString("docker-engine-selinux-1.12.6-1.el7.x86_64");
        assertFalse(packageName.getEpoch().isPresent());
        assertEquals("docker-engine-selinux", packageName.getName());
        assertEquals("1.12.6", packageName.getVersion().get());
        assertEquals("1.el7", packageName.getRelease().get());
        assertEquals("x86_64", packageName.getArchitecture().get());
        assertEquals("0:docker-engine-selinux-1.12.6-1.el7.x86_64", packageName.toFullName());
        assertEquals("0:docker-engine-selinux-1.12.6-1.el7.*", packageName.toVersionLock());
    }

    @Test
    public void parsePackageNameWithEpochAndArchitecture() {
        YumPackageName packageName = YumPackageName.fromString("1:docker-engine-selinux-1.12.6-1.el7.x86_64");
        assertEquals("1", packageName.getEpoch().get());
        assertEquals("docker-engine-selinux", packageName.getName());
        assertEquals("1.12.6", packageName.getVersion().get());
        assertEquals("1.el7", packageName.getRelease().get());
        assertEquals("x86_64", packageName.getArchitecture().get());
        assertEquals("1:docker-engine-selinux-1.12.6-1.el7.x86_64", packageName.toFullName());
        assertEquals("1:docker-engine-selinux-1.12.6-1.el7.*", packageName.toVersionLock());
    }

    @Test(expected = IllegalArgumentException.class)
    public void failParsingOfPackageName() {
        YumPackageName.fromString("docker-engine-selinux");
    }

    @Test
    public void unrecognizedArchitectureGetsGobbledUp() {
        YumPackageName packageName = YumPackageName.fromString("docker-engine-selinux-1.12.6-1.el7.i486");
        // This is not a great feature - please use YumPackageName.Builder instead.
        assertEquals("1.el7.i486", packageName.getRelease().get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void failParsingOfPackageNameWithEpochAndArchitecture() {
        YumPackageName.fromString("epoch:docker-engine-selinux-1.12.6-1.el7.x86_64");
    }
}