// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.component.Version;
import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author hakonhall
 */
public class YumPackageNameTest {

    @Test
    public void testBuilder() {
        YumPackageName yumPackage = new YumPackageName.Builder("docker")
                .setEpoch("2")
                .setVersion("1.12.6")
                .setRelease("71.git3e8e77d.el7.centos.1")
                .setArchitecture("x86_64")
                .build();
        assertEquals("2:docker-1.12.6-71.git3e8e77d.el7.centos.1.x86_64", yumPackage.toName(Version.fromString("3")));
        assertEquals("docker-2:1.12.6-71.git3e8e77d.el7.centos.1.x86_64", yumPackage.toName(Version.fromString("4")));
    }

    @Test
    public void testAllValidFormats() {
        // name
        verifyPackageName(
                "docker-engine-selinux",
                null,
                "docker-engine-selinux",
                null,
                null,
                null,
                "docker-engine-selinux",
                null);

        // name with parenthesis
        verifyPackageName(
                "dnf-command(versionlock)",
                null,
                "dnf-command(versionlock)",
                null,
                null,
                null,
                "dnf-command(versionlock)",
                null);

        // name.arch
        verifyPackageName(
                "docker-engine-selinux.x86_64",
                null,
                "docker-engine-selinux",
                null,
                null,
                "x86_64",
                "docker-engine-selinux.x86_64",
                null);

        // name-ver-rel
        verifyPackageName("docker-engine-selinux-1.12.6-1.el7",
                null,
                "docker-engine-selinux",
                "1.12.6",
                "1.el7",
                null,
                "docker-engine-selinux-1.12.6-1.el7",
                "0:docker-engine-selinux-1.12.6-1.el7.*");

        // name-ver-rel.arch
        verifyPackageName("docker-engine-selinux-1.12.6-1.el7.x86_64",
                null,
                "docker-engine-selinux",
                "1.12.6",
                "1.el7",
                "x86_64",
                "docker-engine-selinux-1.12.6-1.el7.x86_64",
                "0:docker-engine-selinux-1.12.6-1.el7.*");

        // name-epoch:ver-rel.arch
        verifyPackageName(
                "docker-2:1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "2",
                "docker",
                "1.12.6",
                "71.git3e8e77d.el7.centos.1",
                "x86_64",
                "2:docker-1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "2:docker-1.12.6-71.git3e8e77d.el7.centos.1.*");

        // epoch:name-ver-rel.arch
        verifyPackageName(
                "2:docker-1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "2",
                "docker",
                "1.12.6",
                "71.git3e8e77d.el7.centos.1",
                "x86_64",
                "2:docker-1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "2:docker-1.12.6-71.git3e8e77d.el7.centos.1.*");

        // name-ver-rel.arch (RHEL 8)
        verifyPackageName("podman-1.9.3-2.module+el8.2.1+6867+366c07d6.x86_64",
                          null,
                          "podman",
                          "1.9.3",
                          "2.module+el8.2.1+6867+366c07d6",
                          "x86_64",
                          "podman-0:1.9.3-2.module+el8.2.1+6867+366c07d6.x86_64",
                          "podman-0:1.9.3-2.module+el8.2.1+6867+366c07d6.*",
                          YumVersion.rhel8);
    }

    private void verifyPackageName(String packageName,
                                   String epoch,
                                   String name,
                                   String version,
                                   String release,
                                   String architecture,
                                   String toName,
                                   String toVersionName) {
        verifyPackageName(packageName, epoch, name, version, release, architecture, toName, toVersionName, YumVersion.rhel7);
    }

    private void verifyPackageName(String packageName,
                                   String epoch,
                                   String name,
                                   String version,
                                   String release,
                                   String architecture,
                                   String toName,
                                   String toVersionName,
                                   YumVersion yumVersion) {
        YumPackageName yumPackageName = YumPackageName.fromString(packageName);
        verifyValue(epoch, yumPackageName.getEpoch());
        verifyValue(name, Optional.of(yumPackageName.getName()));
        verifyValue(version, yumPackageName.getVersion());
        verifyValue(release, yumPackageName.getRelease());
        verifyValue(architecture, yumPackageName.getArchitecture());
        verifyValue(toName, Optional.of(yumPackageName.toName(yumVersion.asVersion())));

        if (toVersionName == null) {
            try {
                yumPackageName.toVersionLockName(yumVersion.asVersion());
                fail();
            } catch (IllegalStateException e) {
                assertThat(e.getMessage(), containsStringIgnoringCase("Version is missing "));
            }
        } else {
            assertEquals(toVersionName, yumPackageName.toVersionLockName(yumVersion.asVersion()));
        }
    }

    private void verifyValue(String value, Optional<String> actual) {
        if (value == null) {
            assertFalse(actual.isPresent());
        } else {
            assertEquals(value, actual.get());
        }
    }

    @Test
    public void testArchitectures() {
        assertEquals("x86_64", YumPackageName.fromString("docker.x86_64").getArchitecture().get());
        assertEquals("i686", YumPackageName.fromString("docker.i686").getArchitecture().get());
        assertEquals("noarch", YumPackageName.fromString("docker.noarch").getArchitecture().get());
    }

    @Test
    public void unrecognizedArchitectureGetsGobbledUp() {
        YumPackageName packageName = YumPackageName.fromString("docker-engine-selinux-1.12.6-1.el7.i486");
        // This is not a great feature - please use YumPackageName.Builder instead.
        assertEquals("1.el7.i486", packageName.getRelease().get());
    }

    @Test
    public void failParsingOfPackageNameWithEpochAndArchitecture() {
        try {
            YumPackageName.fromString("epoch:docker-engine-selinux-1.12.6-1.el7.x86_64");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsStringIgnoringCase("epoch"));
        }
    }

    @Test
    public void testSubset() {
        YumPackageName yumPackage = new YumPackageName.Builder("docker")
                .setVersion("1.12.6")
                .build();

        assertTrue(yumPackage.isSubsetOf(yumPackage));
        assertTrue(yumPackage.isSubsetOf(new YumPackageName.Builder("docker")
                .setVersion("1.12.6")
                .setEpoch("2")
                .setRelease("71.git3e8e77d.el7.centos.1")
                .setArchitecture("x86_64")
                .build()));
        assertFalse(yumPackage.isSubsetOf(new YumPackageName.Builder("docker")
                .setVersion("1.13.1")
                .build()));
    }

}
