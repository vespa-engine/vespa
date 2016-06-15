// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

/**
 * @author lulf
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
                .addEqualityGroup(ClusterSpec.Group.from("id1"), ClusterSpec.Group.from("id1"))
                .addEqualityGroup(ClusterSpec.Group.from("id2"))
                .addEqualityGroup(ClusterSpec.Group.from("id3"))
                .testEquals();
    }

}
