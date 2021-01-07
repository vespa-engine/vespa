// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.orchestrator.status;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Timer;
import com.yahoo.path.Path;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.status.json.WireHostInfo;
import org.apache.zookeeper.KeeperException.NoNodeException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles all ZooKeeper data structures related to each active application, including HostInfo.
 * Cache (if any) is above and not visible here.
 *
 * @author hakonhall
 */
public class HostInfosServiceImpl implements HostInfosService {
    private static final Logger log = Logger.getLogger(HostInfosServiceImpl.class.getName());

    private final Curator curator;
    private final Timer timer;

    HostInfosServiceImpl(Curator curator, Timer timer) {
        this.curator = curator;
        this.timer = timer;
    }

    @Override
    public HostInfos getHostInfos(ApplicationInstanceReference reference) {
        ApplicationId application = OrchestratorUtil.toApplicationId(reference);
        String hostsRootPath = hostsPath(application);

        List<String> hostnames;
        try {
            hostnames = curator.framework().getChildren().forPath(hostsRootPath);
        } catch (NoNodeException e) {
            return new HostInfos();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var hostInfos = new HashMap<HostName, HostInfo>();
        for (var hostname : hostnames) {
            byte[] bytes;
            try {
                bytes = curator.framework().getData().forPath(hostsRootPath + "/" + hostname);
            } catch (NoNodeException e) {
                // OK, node has been removed since getChildren()
                continue;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            hostInfos.put(new HostName(hostname), WireHostInfo.deserialize(bytes));
        }

        return new HostInfos(hostInfos);
    }

    @Override
    public boolean setHostStatus(ApplicationInstanceReference reference, HostName hostname, HostStatus status) {
        ApplicationId application = OrchestratorUtil.toApplicationId(reference);
        String path = hostPath(application, hostname);

        if (status == HostStatus.NO_REMARKS) {
            return deleteNode_ignoreNoNodeException(path, "Host already has state NO_REMARKS, path = " + path);
        }

        Optional<HostInfo> currentHostInfo = uncheck(() -> readBytesFromZk(path)).map(WireHostInfo::deserialize);
        if (currentHostInfo.isEmpty()) {
            Instant suspendedSince = timer.currentTime();
            HostInfo hostInfo = HostInfo.createSuspended(status, suspendedSince);
            byte[] hostInfoBytes = WireHostInfo.serialize(hostInfo);
            uncheck(() -> curator.framework().create().creatingParentsIfNeeded().forPath(path, hostInfoBytes));
        } else if (currentHostInfo.get().status() == status) {
            return false;
        } else {
            Instant suspendedSince = currentHostInfo.get().suspendedSince().orElseGet(timer::currentTime);
            HostInfo hostInfo = HostInfo.createSuspended(status, suspendedSince);
            byte[] hostInfoBytes = WireHostInfo.serialize(hostInfo);
            uncheck(() -> curator.framework().setData().forPath(path, hostInfoBytes));
        }

        return true;
    }

    @Override
    public void removeApplication(ApplicationInstanceReference reference) {
        ApplicationId application = OrchestratorUtil.toApplicationId(reference);
        curator.delete(Path.fromString(applicationPath(application)));
    }

    @Override
    public void removeHosts(ApplicationInstanceReference reference, Set<HostName> hostnames) {
        ApplicationId application = OrchestratorUtil.toApplicationId(reference);

        // Needed for VESPA-18864:
        log.info("Removing host status for " + application + ": " + hostnames);

        // Remove /vespa/host-status/APPLICATION_ID/hosts/HOSTNAME
        hostnames.forEach(hostname -> curator.delete(Path.fromString(hostPath(application, hostname))));
    }

    private Optional<byte[]> readBytesFromZk(String path) throws Exception {
        try {
            return Optional.of(curator.framework().getData().forPath(path));
        } catch (NoNodeException e) {
            return Optional.empty();
        }
    }

    private boolean deleteNode_ignoreNoNodeException(String path, String debugLogMessageIfNotExists) {
        try {
            curator.framework().delete().forPath(path);
            return true;
        } catch (NoNodeException e) {
            log.log(Level.FINE, debugLogMessageIfNotExists, e);
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String applicationPath(ApplicationId application) {
        return "/vespa/host-status/" + application.serializedForm();
    }

    private static String hostsPath(ApplicationId application) {
        return applicationPath(application) + "/hosts";
    }

    private static String hostPath(ApplicationId application, HostName hostname) {
        return hostsPath(application) + "/" + hostname.s();
    }

    private <T> T uncheck(SupplierThrowingException<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface SupplierThrowingException<T> {
        T get() throws Exception;
    }
}
