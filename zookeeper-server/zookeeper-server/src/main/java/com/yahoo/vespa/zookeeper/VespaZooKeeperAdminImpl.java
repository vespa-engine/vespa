// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.admin.ZooKeeperAdmin;
import org.apache.zookeeper.data.ACL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hmusum
 */
@SuppressWarnings("unused") // Created by injection
public class VespaZooKeeperAdminImpl implements VespaZooKeeperAdmin {

    private static final Logger log = java.util.logging.Logger.getLogger(VespaZooKeeperAdminImpl.class.getName());

    @Override
    public void reconfigure(String connectionSpec, String servers) throws ReconfigException {
        try (ZooKeeperAdmin zooKeeperAdmin = createAdmin(connectionSpec)) {
            long fromConfig = -1;
            // Using string parameters because the List variant of reconfigure fails to join empty lists (observed on 3.5.6, fixed in 3.7.0).
            log.log(Level.INFO, "Applying ZooKeeper config: " + servers);
            byte[] appliedConfig = zooKeeperAdmin.reconfigure(null, null, servers, fromConfig, null);
            log.log(Level.INFO, "Applied ZooKeeper config: " + new String(appliedConfig, StandardCharsets.UTF_8));

            // Verify by issuing a write operation; this is only accepted once new quorum is obtained.
            List<ACL> acl = ZooDefs.Ids.OPEN_ACL_UNSAFE;
            String node = zooKeeperAdmin.create("/reconfigure-dummy-node", new byte[0], acl, CreateMode.EPHEMERAL_SEQUENTIAL);
            zooKeeperAdmin.delete(node, -1);

            log.log(Level.INFO, "Verified ZooKeeper config: " + new String(appliedConfig, StandardCharsets.UTF_8));
        }
        catch (   KeeperException.ReconfigInProgress
                | KeeperException.ConnectionLossException
                | KeeperException.NewConfigNoQuorum  e) {
            throw new ReconfigException(e);
        }
        catch (KeeperException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private ZooKeeperAdmin createAdmin(String connectionSpec) {
        return uncheck(() -> new ZooKeeperAdmin(connectionSpec, (int) sessionTimeout().toMillis(),
                                                (event) -> log.log(Level.INFO, event.toString()), new ZkClientConfigBuilder().toConfig()));
    }

    /** Creates a node in zookeeper, with hostname as part of node name, this ensures that server is up and working before returning */
    void createDummyNode(ZookeeperServerConfig zookeeperServerConfig) {
        int sleepTime = 2_000;
        try (ZooKeeperAdmin zooKeeperAdmin = createAdmin(localConnectionSpec(zookeeperServerConfig))) {
            Instant end = Instant.now().plus(Duration.ofMinutes(5));
            Exception exception = null;
            do {
                try {
                    zooKeeperAdmin.create("/dummy-node-" + HostName.getLocalhost(), new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    return;
                } catch (KeeperException e) {
                    if (e instanceof KeeperException.NodeExistsException) {
                        try {
                            zooKeeperAdmin.setData("/dummy-node-" + HostName.getLocalhost(), new byte[0], -1);
                            return;
                        } catch (KeeperException ex) {
                            log.log(Level.INFO, e.getMessage());
                            Thread.sleep(sleepTime);
                            continue;
                        }
                    }
                    log.log(Level.INFO, e.getMessage());
                    exception = e;
                    Thread.sleep(sleepTime);
                }
            } while (Instant.now().isBefore(end));
            throw new RuntimeException("Unable to create dummy node: ", exception);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}

