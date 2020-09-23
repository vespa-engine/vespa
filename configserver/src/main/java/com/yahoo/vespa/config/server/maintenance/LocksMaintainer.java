// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;
import org.apache.zookeeper.data.Stat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Debug maintainer for logging info about node repository locks. Logs with level FINE, so to actually log something
 * one needs to change log levels with vespa-logctl
 *
 * @author hmusum
 */
public class LocksMaintainer extends ConfigServerMaintainer {

    private final boolean hostedVespa;
    private final Curator curator;

    LocksMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval, FlagSource flagSource) {
        super(applicationRepository, curator, flagSource, Duration.ZERO, interval);
        this.hostedVespa = applicationRepository.configserverConfig().hostedVespa();
        this.curator = curator;
    }

    @Override
    protected boolean maintain() {
        if (! hostedVespa) return true;

        Path unallocatedLockPath = Path.fromString("/provision/v1/locks/unallocatedLock");
        logLockInfo(unallocatedLockPath);

        applicationRepository.listApplications().forEach(applicationId -> {
            Path applicationLockPath = Path.fromString("/provision/v1/locks").append(applicationId.tenant().value())
                    .append(applicationId.application().value())
                    .append(applicationId.instance().value());
            logLockInfo(applicationLockPath);
        });

        return true;
    }

    private void logLockInfo(Path path) {
        List<String> children = curator.getChildren(path);
        if (children.size() > 0)
            log.log(Level.FINE, path + " has " + children.size() + " locks ");
        children.forEach(lockString -> {
            Optional<Instant> createTime = Optional.empty();
            Path lockPath = path.append(lockString);
            Optional<Stat> stat = curator.getStat(lockPath);
            if (stat.isPresent()) {
                createTime = Optional.of(Instant.ofEpochMilli(stat.get().getCtime()));
            }
            log.log(Level.FINE, "Lock /" + lockPath + " created " + createTime.map(Instant::toString).orElse(" at unknown time"));
        });
    }

}
