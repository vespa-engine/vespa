// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

/**
 * @author Ulf Lilleengen
 */
public class ClusterSpecTest {

    @Test
    public void testIdEquals() {
        new EqualsTester()
                .addEqualityGroup(ClusterSpec.Id.from("id1"), ClusterSpec.Id.from("id1"))
                .addEqualityGroup(ClusterSpec.Id.from("id2"))
                .addEqualityGroup(ClusterSpec.Id.from("id3"))
                .testEquals();
    }

    @Test
    public void testGroupEquals() {
        new EqualsTester()
                .addEqualityGroup(ClusterSpec.Group.from(1), ClusterSpec.Group.from(1))
                .addEqualityGroup(ClusterSpec.Group.from(2))
                .addEqualityGroup(ClusterSpec.Group.from(3))
                .testEquals();
    }

}
