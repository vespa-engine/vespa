// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.mock;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class MockCuratorFrameworkTest {

    @Test
    public void stat_has_zxids_which_are_never_reused() throws Exception {
        Curator curator = new MockCurator();
        Path path = Path.fromString("/foo");

        curator.framework().create().forPath(path.getAbsolute(), new byte[1]);
        Stat created = curator.getStat(path).get();
        assertTrue(created.getCzxid() > 0);
        assertEquals(created.getCzxid(), created.getMzxid());

        curator.set(path, new byte[2]);
        Stat modified = curator.getStat(path).get();
        assertEquals(created.getCzxid(), modified.getCzxid());
        assertTrue(modified.getMzxid() > created.getMzxid());

        // A recreated node gets the same version as before, but new zxids
        curator.delete(path);
        curator.framework().create().forPath(path.getAbsolute(), new byte[1]);
        Stat recreated = curator.getStat(path).get();
        assertEquals(created.getVersion(), recreated.getVersion());
        assertNotEquals(created.getCzxid(), recreated.getCzxid());
        assertTrue(recreated.getMzxid() > modified.getMzxid());
    }

}
