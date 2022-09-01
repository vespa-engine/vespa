package com.yahoo.vespa.clustercontroller.core;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

public class CleanupZookeeperLogsOnSuccess implements TestWatcher {

    public CleanupZookeeperLogsOnSuccess() {}

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        System.err.println("TEST FAILED - NOT cleaning up zookeeper directory");
        shutdownZooKeeper(context, false);
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        System.err.println("TEST SUCCEEDED - cleaning up zookeeper directory");
        shutdownZooKeeper(context, true);
    }

    private void shutdownZooKeeper(ExtensionContext ctx, boolean cleanupZooKeeperDir) {
        FleetControllerTest test = (FleetControllerTest) ctx.getTestInstance().orElseThrow();
        if (test.zooKeeperServer != null) {
            test.zooKeeperServer.shutdown(cleanupZooKeeperDir);
            test.zooKeeperServer = null;
        }
    }
}
