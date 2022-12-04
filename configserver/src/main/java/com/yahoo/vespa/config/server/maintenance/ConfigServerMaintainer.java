// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.concurrent.maintenance.JobControl;
import com.yahoo.concurrent.maintenance.JobControlState;
import com.yahoo.concurrent.maintenance.JobMetrics;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.jdisc.Metric;
import com.yahoo.path.Path;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A maintainer is some job which runs at a fixed interval to perform some maintenance task in the config server.
 *
 * @author hmusum
 */
public abstract class ConfigServerMaintainer extends Maintainer {

    protected final ApplicationRepository applicationRepository;

    /** Creates a maintainer where maintainers on different nodes in this cluster run with even delay. */
    ConfigServerMaintainer(ApplicationRepository applicationRepository, Curator curator, FlagSource flagSource,
                           Instant now, Duration interval, boolean useLock) {
        super(null, interval, now, new JobControl(new JobControlFlags(curator, flagSource, useLock)),
              new ConfigServerJobMetrics(applicationRepository.metric()), cluster(curator), false);
        this.applicationRepository = applicationRepository;
    }

    private static class ConfigServerJobMetrics extends JobMetrics {

        private final Metric metric;

        public ConfigServerJobMetrics(Metric metric) {
            this.metric = metric;
        }

        @Override
        public void completed(String job, double successFactor) {
            metric.set("maintenance.successFactor", successFactor, metric.createContext(Map.of("job", job)));
        }

    }

    private static class JobControlFlags implements JobControlState {

        private static final Path root = Path.fromString("/configserver/v1/");

        private static final Path lockRoot = root.append("locks");

        private final Curator curator;
        private final ListFlag<String> inactiveJobsFlag;
        private final boolean useLock;

        public JobControlFlags(Curator curator, FlagSource flagSource, boolean useLock) {
            this.curator = curator;
            this.inactiveJobsFlag = PermanentFlags.INACTIVE_MAINTENANCE_JOBS.bindTo(flagSource);
            this.useLock = useLock;
        }

        @Override
        public Set<String> readInactiveJobs() {
            return Set.copyOf(inactiveJobsFlag.value());
        }

        @Override
        public Mutex lockMaintenanceJob(String job) {
            return (useLock)
                    ? curator.lock(lockRoot.append(job), Duration.ofSeconds(1))
                    : () -> { };
        }

    }

    /** Returns all hosts configured to be part of this ZooKeeper cluster */
    public static List<String> cluster(Curator curator) {
        return Arrays.stream(curator.zooKeeperEnsembleConnectionSpec().split(","))
                     .filter(hostAndPort -> !hostAndPort.isEmpty())
                     .map(hostAndPort -> hostAndPort.split(":")[0])
                     .collect(Collectors.toList());
    }


}
