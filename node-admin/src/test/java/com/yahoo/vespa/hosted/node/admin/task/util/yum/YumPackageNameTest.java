// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hakonhall
 */
public class YumPackageNameTest {

    @Test
    void testBuilder() {
        YumPackageName yumPackage = new YumPackageName.Builder("docker")
                .setEpoch("2")
                .setVersion("1.12.6")
                .setRelease("71.git3e8e77d.el7.centos.1")
                .setArchitecture("x86_64")
                .build();
        assertEquals("docker-2:1.12.6-71.git3e8e77d.el7.centos.1.x86_64", yumPackage.toName());
    }

    @Test
    void testAllValidFormats() {
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

        // name-ver
        verifyPackageName("docker-engine-selinux-1.12.6",
                null,
                "docker-engine-selinux",
                "1.12.6",
                null,
                null,
                "docker-engine-selinux-0:1.12.6",
                null);

        // name-ver-rel
        verifyPackageName("docker-engine-selinux-1.12.6-1.el7",
                null,
                "docker-engine-selinux",
                "1.12.6",
                "1.el7",
                null,
                "docker-engine-selinux-0:1.12.6-1.el7",
                "docker-engine-selinux-0:1.12.6-1.el7.*");

        // name-ver-rel.arch
        verifyPackageName("docker-engine-selinux-1.12.6-1.el7.x86_64",
                null,
                "docker-engine-selinux",
                "1.12.6",
                "1.el7",
                "x86_64",
                "docker-engine-selinux-0:1.12.6-1.el7.x86_64",
                "docker-engine-selinux-0:1.12.6-1.el7.*");

        // name-epoch:ver-rel.arch
        verifyPackageName(
                "docker-2:1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "2",
                "docker",
                "1.12.6",
                "71.git3e8e77d.el7.centos.1",
                "x86_64",
                "docker-2:1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "docker-2:1.12.6-71.git3e8e77d.el7.centos.1.*");

        // epoch:name-ver-rel.arch
        verifyPackageName(
                "2:docker-1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "2",
                "docker",
                "1.12.6",
                "71.git3e8e77d.el7.centos.1",
                "x86_64",
                "docker-2:1.12.6-71.git3e8e77d.el7.centos.1.x86_64",
                "docker-2:1.12.6-71.git3e8e77d.el7.centos.1.*");
    }

    private void verifyPackageName(String input,
                                   String epoch,
                                   String name,
                                   String version,
                                   String release,
                                   String architecture,
                                   String toName,
                                   String toVersionName) {
        YumPackageName yumPackageName = YumPackageName.fromString(input);
        assertPackageField("epoch", epoch, yumPackageName.getEpoch());
        assertPackageField("name", name, Optional.of(yumPackageName.getName()));
        assertPackageField("version", version, yumPackageName.getVersion());
        assertPackageField("release", release, yumPackageName.getRelease());
        assertPackageField("architecture", architecture, yumPackageName.getArchitecture());
        assertPackageField("toName()", toName, Optional.of(yumPackageName.toName()));

        if (toVersionName == null) {
            try {
                yumPackageName.toVersionLockName();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Version is missing ") ||
                           e.getMessage().contains("Release is missing "),
                           "Exception message contains expected substring: " + e.getMessage());
            }
        } else {
            assertEquals(toVersionName, yumPackageName.toVersionLockName());
        }
    }

    private void assertPackageField(String field, String expected, Optional<String> actual) {
        if (expected == null) {
            assertFalse(actual.isPresent(), field + " is not present");
        } else {
            assertEquals(expected, actual.get(), field + " has expected value");
        }
    }

    @Test
    void testArchitectures() {
        assertEquals("x86_64", YumPackageName.fromString("docker.x86_64").getArchitecture().get());
        assertEquals("i686", YumPackageName.fromString("docker.i686").getArchitecture().get());
        assertEquals("noarch", YumPackageName.fromString("docker.noarch").getArchitecture().get());
    }

    @Test
    void unrecognizedArchitectureGetsGobbledUp() {
        YumPackageName packageName = YumPackageName.fromString("docker-engine-selinux-1.12.6-1.el7.i486");
        // This is not a great feature - please use YumPackageName.Builder instead.
        assertEquals("1.el7.i486", packageName.getRelease().get());
    }

    @Test
    void failParsingOfPackageNameWithEpochAndArchitecture() {
        try {
            YumPackageName.fromString("epoch:docker-engine-selinux-1.12.6-1.el7.x86_64");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("epoch"));
        }
    }

    @Test
    void testSubset() {
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
