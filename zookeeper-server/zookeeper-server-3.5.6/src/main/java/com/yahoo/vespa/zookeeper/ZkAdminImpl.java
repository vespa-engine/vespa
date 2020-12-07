// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.admin.ZooKeeperAdmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZkAdminImpl implements ZkAdmin {

    private static final Logger log = java.util.logging.Logger.getLogger(ZkAdminImpl.class.getName());

    @Override
    public void reconfigure(String connectionSpec, String joiningServers, String leavingServers) throws ReconfigException {
        try {
            ZooKeeperAdmin zooKeeperAdmin = new ZooKeeperAdmin(connectionSpec,
                                                               (int) sessionTimeout().toMillis(),
                                                               new LoggingWatcher());
            long fromConfig = -1;
            // Using string parameters because the List variant of reconfigure fails to join empty lists (observed on 3.5.6, fixed in 3.7.0)
            byte[] appliedConfig = zooKeeperAdmin.reconfigure(joiningServers, leavingServers, null, fromConfig, null);
            log.log(Level.INFO, "Applied ZooKeeper config: " + new String(appliedConfig, StandardCharsets.UTF_8));
        } catch (KeeperException e) {
            if (retryOn(e))
                throw new ReconfigException(e);
            else
                throw new RuntimeException(e);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean retryOn(KeeperException e) {
        return e instanceof KeeperException.ReconfigInProgress ||
               e instanceof KeeperException.ConnectionLossException ||
               e instanceof KeeperException.NewConfigNoQuorum;
    }

}

