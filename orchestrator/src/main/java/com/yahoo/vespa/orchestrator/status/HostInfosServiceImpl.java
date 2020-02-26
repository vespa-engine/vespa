// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.orchestrator.status;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Timer;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.status.json.WireHostInfo;
import org.apache.zookeeper.KeeperException.NoNodeException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
        if (uncheck(() -> curator.framework().checkExists().forPath(hostsRootPath)) == null) {
            return new HostInfos();
        } else {
            List<String> hostnames = uncheck(() -> curator.framework().getChildren().forPath(hostsRootPath));
            Map<HostName, HostInfo> hostInfos = hostnames.stream().collect(Collectors.toMap(
                    hostname -> new HostName(hostname),
                    hostname -> {
                        byte[] bytes = uncheck(() -> curator.framework().getData().forPath(hostsRootPath + "/" + hostname));
                        return WireHostInfo.deserialize(bytes);
                    }));
            return new HostInfos(hostInfos);
        }
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
            log.log(LogLevel.DEBUG, debugLogMessageIfNotExists, e);
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String applicationPath(ApplicationId application) {
        return "/vespa/host-status/" + application.serializedForm();
    }

    private static String hostsPath(ApplicationId applicationInstanceReference) {
        return applicationPath(applicationInstanceReference) + "/hosts";
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
