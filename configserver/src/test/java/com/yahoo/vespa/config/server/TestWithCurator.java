// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Before;

/**
 * For tests that require a Curator instance
 *
 * @author lulf
 * @since 5.16
 */
public class TestWithCurator {

    protected ConfigCurator configCurator;
    protected CuratorFramework curatorFramework;
    protected Curator curator;

    @Before
    public void setupZKProvider() throws Exception {
        curator = new MockCurator();
        configCurator = ConfigCurator.create(curator);
        curatorFramework = curator.framework();
    }

}
